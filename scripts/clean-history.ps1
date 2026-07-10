# NOVA — clean-start git history (Faz 4D), PowerShell version.
# Replaces the single old commit (which contains personal data) with one fresh
# root commit from the current clean tree. Makes a backup, requires confirmation,
# runs the secret scanner, and STOPS before pushing.
#
# Run from the repo root:
#   powershell -ExecutionPolicy Bypass -File scripts/clean-history.ps1

$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
$root = (Get-Location).Path
$name  = if ($env:GIT_AUTHOR_NAME)  { $env:GIT_AUTHOR_NAME }  else { "NOVA Agent AI contributors" }
$email = if ($env:GIT_AUTHOR_EMAIL) { $env:GIT_AUTHOR_EMAIL } else { "noreply@users.noreply.github.com" }
$msg   = if ($env:COMMIT_MSG)       { $env:COMMIT_MSG }       else { "NOVA Agent — initial public release" }

Write-Host "Repo:     $root"
Write-Host "Identity: $name <$email>   (override with GIT_AUTHOR_NAME / GIT_AUTHOR_EMAIL)"
Write-Host "This will REWRITE git history into a single fresh commit and delete the old one."
$ans = Read-Host "Type CONFIRM to proceed"
if ($ans -ne "CONFIRM") { Write-Host "aborted."; exit 1 }

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
Write-Host "==> 1/6 backup bundle"
git bundle create "..\nova-backup-$stamp.bundle" --all

Write-Host "==> 2/6 secret scan (working tree)"
node scripts/secret-scan.mjs

Write-Host "==> 3/6 orphan branch + stage clean tree"
git checkout --orphan clean-main
git add -A

Write-Host "==> 4/6 secret scan (staged tree)"
node scripts/secret-scan.mjs

Write-Host "==> 5/6 commit + replace main"
git -c user.name="$name" -c user.email="$email" commit -m "$msg"
git branch -D main 2>$null
git branch -m main

Write-Host "==> 6/6 purge unreachable old objects"
git reflog expire --expire=now --all
git gc --prune=now --aggressive

Write-Host ""
Write-Host "Done. History is now:"
git --no-pager log --format='  %h %an <%ae>  %s'
Write-Host ("  commits: " + (git rev-list --count HEAD))
Write-Host ""
Write-Host "NOT pushed yet. After verifying, push with ONE of:"
Write-Host "  git push --force origin main                 # rewrite existing remote"
Write-Host "  # or point origin at a fresh empty repo, then: git push -u origin main"
Write-Host "Backup saved as ..\nova-backup-$stamp.bundle"
