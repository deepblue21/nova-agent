#!/usr/bin/env bash
# NOVA — clean-start git history (Faz 4D). Replaces the single old commit (which
# contains personal data) with one fresh root commit from the current clean tree.
#
# Safe by design: makes a backup bundle, requires a typed confirmation, runs the
# secret scanner before committing, and STOPS before pushing (prints push options).
#
# Run from the repo root:  bash scripts/clean-history.sh
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"
NAME="${GIT_AUTHOR_NAME:-NOVA Agent AI contributors}"
EMAIL="${GIT_AUTHOR_EMAIL:-noreply@users.noreply.github.com}"
MSG="${COMMIT_MSG:-NOVA Agent — initial public release}"

echo "Repo:     $ROOT"
echo "Identity: $NAME <$EMAIL>   (override with GIT_AUTHOR_NAME / GIT_AUTHOR_EMAIL)"
echo "This will REWRITE git history into a single fresh commit and delete the old one."
printf 'Type CONFIRM to proceed: '
read -r ans
[ "$ans" = "CONFIRM" ] || { echo "aborted."; exit 1; }

echo "==> 1/6 backup bundle"
git bundle create "../nova-backup-$(date +%Y%m%d-%H%M%S).bundle" --all

echo "==> 2/6 secret scan (working tree)"
node scripts/secret-scan.mjs

echo "==> 3/6 orphan branch + stage clean tree"
git checkout --orphan clean-main
git add -A

echo "==> 4/6 secret scan (staged tree)"
node scripts/secret-scan.mjs

echo "==> 5/6 commit + replace main"
git -c user.name="$NAME" -c user.email="$EMAIL" commit -m "$MSG"
git branch -D main 2>/dev/null || true
git branch -m main

echo "==> 6/6 purge unreachable old objects"
git reflog expire --expire=now --all || true
git gc --prune=now --aggressive || true

echo
echo "Done. History is now:"
git --no-pager log --format='  %h %an <%ae>  %s'
echo "  commits: $(git rev-list --count HEAD)"
echo
echo "NOT pushed yet. After verifying, push with ONE of:"
echo "  git push --force origin main                 # rewrite existing remote"
echo "  # or point origin at a fresh empty repo, then: git push -u origin main"
echo "Backup saved as ../nova-backup-*.bundle (restore: git clone <bundle> restored)"
