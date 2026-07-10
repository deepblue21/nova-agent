# Git History Cleanup (Faz 4D) — public release prerequisite

The repository currently has **one commit** (`da8bece`) and all later work is in the
working tree. That single commit must be removed before going public because it
contains personal data **in both its content and its author metadata**:

- author/committer: a personal name + personal gmail address
- inside the commit: a personal email, the OS username, the old demo password,
  personal absolute home paths (`/mnt/c/Users/<user>/...`), and three
  `web/vite.config.js.timestamp-*.mjs` build artifacts.

The current working tree has already been sanitized (the personal reference in
`web/src/nova-agent.jsx` was genericized; the timestamp files are deleted and will
not be re-committed). So the only thing left is to **start the git history fresh**.

> ⚠️ This rewrites history and is destructive. Make the backup in step 1. Run it on
> your real machine (Windows PowerShell or WSL) — not from a sandbox.

---

## Recommended: clean-start (orphan branch)

This produces a single brand-new root commit from the current clean tree, with a
generic author, and drops `da8bece` entirely.

### 1) Back up first (always)

```bash
# from the repo root — a full mirror you can restore from if anything goes wrong
git bundle create ../nova-backup.bundle --all
# (or just copy the whole Nova_Agent_AI folder somewhere safe)
```

### 2) Confirm the tree is clean

```bash
npm run secret-scan        # must say "no secrets found"
npm run security           # optional but recommended: full gate
git status                 # review what will be committed
```

### 3) Choose the identity for the new history

For a public repo, prefer a non-personal email (e.g. GitHub's `noreply`) so your
address isn't published in every commit:

```bash
git config user.name  "NOVA Agent AI contributors"
git config user.email "noreply@users.noreply.github.com"
# (or your GitHub no-reply: <id>+<username>@users.noreply.github.com)
```

### 4) Rewrite to a single clean commit

```bash
git checkout --orphan clean-main     # new branch, NO parent → no old history
git add -A                           # stage the clean tree (.gitignore excludes node_modules/dist/.env/timestamps)
npm run secret-scan                  # re-scan what is staged, before committing
git commit -m "NOVA Agent — initial public release"
git branch -D main                   # drop the old branch (and its commit)
git branch -m main                   # rename clean-main → main
git reflog expire --expire=now --all
git gc --prune=now --aggressive      # purge the now-unreachable old commit locally
```

### 5) Verify locally

```bash
git rev-list --count HEAD            # → 1
git log --format='%an <%ae>'         # → your chosen generic identity
# replace the pattern with YOUR personal email/username to confirm they're gone:
git log -p | grep -i -E "your\.name@example\.com|your-username" || echo "clean"
```

### 6) Publish

The old commit can linger in **GitHub's** cache, forks, or open PRs even after a
force-push. Two options:

- **Safest (guaranteed clean):** delete the GitHub repo and create a new empty one,
  then push:
  ```bash
  git remote set-url origin https://github.com/<you>/<new-repo>.git
  git push -u origin main
  ```
- **Force-push the existing remote** (fine if the repo was always private and has no
  forks/PRs):
  ```bash
  git push --force origin main
  ```
  Then, on GitHub, confirm the old commit hash 404s:
  `https://github.com/deepblue21/nova-agent/commit/da8bece`

---

## Alternative: brand-new repo (simplest, also drops reflog)

```bash
# from the repo root, after the backup in step 1 and the checks in step 2:
rm -rf .git            # PowerShell: Remove-Item -Recurse -Force .git
git init -b main
git config user.name  "NOVA Agent AI contributors"
git config user.email "noreply@users.noreply.github.com"
git add -A
npm run secret-scan
git commit -m "NOVA Agent — initial public release"
git remote add origin https://github.com/<you>/<repo>.git
git push -u origin main      # use --force if pushing over existing history
```

This is equivalent to the orphan approach but starts from an empty `.git`, so there
is no reflog containing `da8bece` at all.

---

## Helper scripts

`scripts/clean-history.sh` (WSL/bash) and `scripts/clean-history.ps1` (PowerShell)
automate steps 1–5 with a backup, a typed confirmation, and secret-scan gates. They
**stop before pushing** and print the push options so you verify first.

```bash
# WSL / Git Bash
bash scripts/clean-history.sh

# PowerShell
powershell -ExecutionPolicy Bypass -File scripts/clean-history.ps1
```

## Troubleshooting

- **`Unable to create '.git/index.lock'`** — a stale lock from an interrupted git
  command. Remove it and retry: `rm -f .git/index.lock`.
- **Want to keep your own name but hide email** — set `user.email` to your GitHub
  no-reply address (Settings → Emails → "Keep my email addresses private").
