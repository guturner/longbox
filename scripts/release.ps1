# Create a semver release tag and push it to trigger the GitHub Actions release workflow.
# Usage: .\scripts\release.ps1 -Version 0.1.1
param(
    [Parameter(Mandatory)]
    [string]$Version
)

if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    Write-Error "Usage: .\scripts\release.ps1 -Version 0.1.1"
    exit 1
}

$tag = "v$Version"
git tag -a $tag -m "Release $Version"
git push origin $tag
Write-Host "Tagged $tag and pushed — GitHub Actions release workflow is now running."
