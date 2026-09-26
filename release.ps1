# Publish a release that the one-line installers pick up. Run from this folder:
#
#     .\release.ps1                  build, checksum, and publish the version in pom.xml
#     .\release.ps1 -DryRun          build and checksum only; show what would be published
#
# The installers download from releases/latest/download/<name>, so the asset NAMES below must
# never change between releases - only their contents. Every machine picks the new version up by
# running its install line again, or `cfucli update`.
#
# The window jar is built once per platform, because JavaFX carries its natives per platform - see
# the profiles in app/pom.xml. The cli jar carries every platform's natives already and is shared.
#
# $env:GH names the GitHub CLI to use (default: gh), for machines that pick an identity through a
# wrapper. Requires a clean, pushed working tree: a release must match a public commit.

param([switch]$DryRun)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$gh = if ($env:GH) { $env:GH } else { 'gh' }
$version = ([xml](Get-Content pom.xml)).project.version
$tag = "v$version"
$out = Join-Path $PSScriptRoot 'dist\release'

# Only lines shaped like git's own output are trusted: a git wrapper that picks an identity may
# print a banner of its own on stdout, which would otherwise read as a changed file or a bad hash.
function Sha([string]$ref) { @(git rev-parse $ref) | Where-Object { $_ -match '^[0-9a-f]{40}$' } | Select-Object -Last 1 }

if (-not $DryRun) {
    $dirty = @(git status --porcelain) | Where-Object { $_ -match '^[ MADRCU?!]{2} ' }
    if ($dirty) { throw "the working tree is not clean - commit first, so the release matches a public commit:`n$($dirty -join "`n")" }
    git fetch -q origin
    if ((Sha HEAD) -ne (Sha '@{u}')) { throw 'HEAD is not pushed - push first' }
}

function Build([string[]]$mvnArgs) {
    # The network here drops connections mid-download often enough that one retry is not enough.
    for ($i = 1; $i -le 4; $i++) {
        & mvn -q -DskipTests @mvnArgs
        if ($LASTEXITCODE -eq 0) { return }
        Write-Host "build attempt $i failed, retrying"
    }
    throw "mvn $mvnArgs failed"
}

Write-Host "building $tag"
Build @('clean', 'install')                                   # cli jar + the Windows window
Build @('-Pmac-aarch64', '-pl', 'app', 'package')             # Apple Silicon window
Build @('-Pmac', '-pl', 'app', 'package')                     # Intel Mac window

# Emptied rather than deleted: a folder something still has open (a test server serving it, an
# Explorer window) cannot be removed on Windows, but its files can.
New-Item -ItemType Directory -Force -Path $out | Out-Null
Get-ChildItem $out | Remove-Item -Recurse -Force
Copy-Item cli\shade\cfucli.jar                  (Join-Path $out 'cfucli.jar')
Copy-Item app\shade\cfucli-app.jar              (Join-Path $out 'cfucli-app-win.jar')
Copy-Item app\shade\cfucli-app-mac-aarch64.jar  (Join-Path $out 'cfucli-app-mac-aarch64.jar')
Copy-Item app\shade\cfucli-app-mac.jar          (Join-Path $out 'cfucli-app-mac.jar')
Set-Content -NoNewline -Encoding ASCII (Join-Path $out 'version.txt') $version

# LF line endings and two spaces - the format shasum/sha256sum write and the installers parse.
$sums = Get-ChildItem $out -File | Where-Object Name -ne 'SHA256SUMS' | Sort-Object Name |
    ForEach-Object { '{0}  {1}' -f (Get-FileHash -Algorithm SHA256 $_.FullName).Hash.ToLower(), $_.Name }
[IO.File]::WriteAllText((Join-Path $out 'SHA256SUMS'), (($sums -join "`n") + "`n"))

Get-ChildItem $out | ForEach-Object { Write-Host ('  {0,-28} {1,7:N1} MB' -f $_.Name, ($_.Length / 1MB)) }
if ($DryRun) { Write-Host "dry run - nothing published. Assets are in $out"; return }

$notes = "Install or update with one line.`n`nmacOS:  curl -fsSL https://cfucli.github.io/install.sh | bash`nWindows (PowerShell):  irm https://cfucli.github.io/install.ps1 | iex"
& $gh release create $tag (Get-ChildItem $out -File).FullName --repo cfucli/cfucli --target (Sha HEAD) --title "cfucli $version" --notes $notes --latest
if ($LASTEXITCODE -ne 0) { throw 'gh release create failed' }
Write-Host "published $tag - installers now pick it up"
