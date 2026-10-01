# Publish a release that the one-line installers pick up. Run from this folder:
#
#     .\release.ps1                  build, checksum, and publish the version in pom.xml
#     .\release.ps1 -DryRun          build and checksum only; show what would be published
#
# The installers download from releases/latest/download/<name>, so the asset NAMES below must
# never change between releases - only their contents. Every machine picks the new version up by
# running its install line again, or `cfucli update`.
#
# Since 0.4 a release is ONE jar per platform - the cli and the window shaded together by the
# bundle module - built per platform because the window's JavaFX carries its natives per platform
# (see the profiles in bundle/pom.xml): cfucli-win.jar, cfucli-mac.jar, cfucli-mac-aarch64.jar.
# With no arguments the jar opens the window; with any, it is the cli.
#
# The Windows launcher cfucli.exe is jr (github.com/jarrunner/jr), built in the maven build itself
# by jr-maven-plugin (the jrexe profile in pom.xml, configured in bundle/pom.xml): it carries its
# config, the sha256 of cfucli-win.jar and this release's download url baked in, plus the icon and
# version info, so Task Manager shows "cfucli" with the seal. On first run it fetches its jar from
# this release (and a Java runtime if none is found); "cfucli -Xjr:update" later replaces the exe
# itself from the update file the plugin writes beside the release and this script copies into the
# site repo beside this one (update\cfucli.json there - commit and push that repo after
# publishing). The plugin also writes the release folder itself - assets, version.txt and
# SHA256SUMS - so this script only orders the builds and publishes. $env:JR_REPO, the jr
# working copy the plugin was built from, puts its commit into the release notes so the exe can be
# traced to source.
#
# $env:GH names the GitHub CLI to use (default: gh), for machines that pick an identity through a
# wrapper. Requires a clean, pushed working tree: a release must match a public commit.

param([switch]$DryRun)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$gh = if ($env:GH) { $env:GH } else { 'gh' }
$version = ([xml](Get-Content pom.xml)).project.version
$tag = "v$version"
$out = Join-Path $PSScriptRoot 'bundle\target\jr\release'

$site = Join-Path $PSScriptRoot '..\cfucli.github.io'
if (-not $DryRun -and -not (Test-Path (Join-Path $site '.git'))) { throw "no site working copy at $site - the update files go there" }
$update = Join-Path $site 'update\cfucli.json'

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
# Order matters. The Mac jars are built before the Windows exe, because the plugin puts them in
# the release folder and fails if one is missing; they need the other modules installed first.
Build @('clean', 'install')                                   # every module, the Windows jar
Build @('-Pmac-aarch64', '-pl', 'bundle', 'package')          # Apple Silicon jar
Build @('-Pmac', '-pl', 'bundle', 'package')                  # Intel Mac jar
# -Djrexe -Djr.source=url: jr-maven-plugin builds cfucli.exe with the sha256 of exactly this jar and
# this release's download url baked in, and writes the whole release into bundle\target\jr\release:
# the exe, cfucli-win.jar (the bytes it hashed), the Mac jars, version.txt, SHA256SUMS and
# cfucli.update.json. -Djr.updateMergeFrom keeps the releases already in the site's update file.
$jrArgs = @('-Djrexe', '-Djr.source=url', '-pl', 'bundle', 'verify')
if (Test-Path $update) { $jrArgs = @("-Djr.updateMergeFrom=$((Resolve-Path $update).Path)") + $jrArgs }
Build $jrArgs                                                 # cfucli.exe and the release folder

$updateOut = Join-Path $out 'cfucli.update.json'
foreach ($f in @('cfucli.exe', 'cfucli-win.jar', 'cfucli-mac.jar', 'cfucli-mac-aarch64.jar', 'version.txt', 'SHA256SUMS', 'cfucli.update.json')) {
    if (-not (Test-Path (Join-Path $out $f))) { throw "the release folder has no $f - see the jr:exe output above" }
}
$jrRepo = $env:JR_REPO
$jrCommit = if ($jrRepo) { @(git -C $jrRepo rev-parse HEAD 2>$null) | Where-Object { $_ -match '^[0-9a-f]{40}$' } | Select-Object -Last 1 }

Get-ChildItem $out | ForEach-Object { Write-Host ('  {0,-28} {1,7:N1} MB' -f $_.Name, ($_.Length / 1MB)) }
if ($DryRun) { Write-Host "dry run - nothing published. Assets are in $out"; return }

$notes = "Install or update with one line.`n`nmacOS:  curl -fsSL https://cfucli.github.io/install.sh | bash`nWindows (PowerShell):  irm https://cfucli.github.io/install.ps1 | iex" +
         "`n`nOn Windows cfucli.exe is a single file, the command line and the window in one (run it with no arguments for the window): on first run it fetches its jar from this release, checked against the sha256 it carries, and ``cfucli -Xjr:update`` replaces it with the next release." +
         "`ncfucli.exe is jr (https://github.com/jarrunner/jr), built by jr-maven-plugin." +
         "`njr commit: $(if ($jrCommit) { $jrCommit } else { 'unknown' })"
# Through a file: Windows PowerShell passes a multi-line string to a native program cut at the first
# line break - measured, v0.3's notes arrived as their first line only, losing the jr provenance.
$notesFile = Join-Path $out '..\release-notes.md'
[IO.File]::WriteAllText($notesFile, $notes + "`n")
# Every file in the folder is an asset except the update file, which goes to the site instead.
$assets = (Get-ChildItem $out -File | Where-Object Name -ne 'cfucli.update.json').FullName
& $gh release create $tag $assets --repo cfucli/cfucli --target (Sha HEAD) --title "cfucli $version" --notes-file $notesFile --latest
if ($LASTEXITCODE -ne 0) { throw 'gh release create failed' }
Write-Host "published $tag - installers now pick it up"

# The update file "cfucli -Xjr:update" reads, written by the plugin. Copied to the site only after the
# release exists, so a url never points at an asset that is not there yet.
New-Item -ItemType Directory -Force -Path (Split-Path $update) | Out-Null
Copy-Item $updateOut $update -Force
Write-Host "update file written to $update - commit and push the site repo to offer $version to -Xjr:update"
