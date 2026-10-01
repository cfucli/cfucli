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
# itself from the update file this script writes into the site repo beside this one
# (update\cfucli.json there - commit and push that repo after publishing). $env:JR_REPO, the jr
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
$out = Join-Path $PSScriptRoot 'dist\release'

$site = Join-Path $PSScriptRoot '..\cfucli.github.io'
if (-not $DryRun -and -not (Test-Path (Join-Path $site '.git'))) { throw "no site working copy at $site - the update files go there" }

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
# -Djrexe -Djr.source=url: cfucli.exe comes out of this same build, with the sha256 of exactly
# this jar and this release's download url baked in (parent pom, jrexe; bundle/pom.xml).
Build @('-Djrexe', '-Djr.source=url', 'clean', 'install')          # every module, the Windows jar and cfucli.exe
Build @('-Pmac-aarch64', '-pl', 'bundle', 'package')          # Apple Silicon jar
Build @('-Pmac', '-pl', 'bundle', 'package')                  # Intel Mac jar

# Emptied rather than deleted: a folder something still has open (a test server serving it, an
# Explorer window) cannot be removed on Windows, but its files can.
New-Item -ItemType Directory -Force -Path $out | Out-Null
Get-ChildItem $out | Remove-Item -Recurse -Force
Copy-Item bundle\shade\cfucli.jar              (Join-Path $out 'cfucli-win.jar')
Copy-Item bundle\shade\cfucli-mac-aarch64.jar  (Join-Path $out 'cfucli-mac-aarch64.jar')
Copy-Item bundle\shade\cfucli-mac.jar          (Join-Path $out 'cfucli-mac.jar')
Set-Content -NoNewline -Encoding ASCII (Join-Path $out 'version.txt') $version

# The Windows launcher, built by the first maven run. A baked jar hash that is not the hash of the
# jar being published would make every first run refuse its download, so check it here.
$exe = 'bundle\target\jr\cfucli.exe'
if (-not (Test-Path $exe)) { throw "missing $exe - the jrexe build did not run" }
$baked = (Get-Content -Raw 'bundle\target\jr\cfucli.jrc.json' | ConvertFrom-Json).jar
$got = (Get-FileHash -Algorithm SHA256 (Join-Path $out 'cfucli-win.jar')).Hash.ToLower()
if ($baked.sha256 -ne $got) { throw "$exe carries sha256 $($baked.sha256) but cfucli-win.jar is $got" }
if ($baked.sources[0].url -ne "https://github.com/cfucli/cfucli/releases/download/$tag/cfucli-win.jar") { throw "$exe downloads from $($baked.sources[0].url), not this release" }
Copy-Item $exe $out
$jrRepo = $env:JR_REPO
$jrCommit = if ($jrRepo) { @(git -C $jrRepo rev-parse HEAD 2>$null) | Where-Object { $_ -match '^[0-9a-f]{40}$' } | Select-Object -Last 1 }

# LF line endings and two spaces - the format shasum/sha256sum write and the installers parse.
$sums = Get-ChildItem $out -File | Where-Object Name -ne 'SHA256SUMS' | Sort-Object Name |
    ForEach-Object { '{0}  {1}' -f (Get-FileHash -Algorithm SHA256 $_.FullName).Hash.ToLower(), $_.Name }
[IO.File]::WriteAllText((Join-Path $out 'SHA256SUMS'), (($sums -join "`n") + "`n"))

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
& $gh release create $tag (Get-ChildItem $out -File).FullName --repo cfucli/cfucli --target (Sha HEAD) --title "cfucli $version" --notes-file $notesFile --latest
if ($LASTEXITCODE -ne 0) { throw 'gh release create failed' }
Write-Host "published $tag - installers now pick it up"

# The update file "cfucli -Xjr:update" reads (jr's update format 1): releases newest first, the
# stable channel on this one. Written only after the release exists, so a url never points at an
# asset that is not there yet. There is no cfucliapp.json: since 0.4 there is no cfucliapp.exe.
$updates = Join-Path $site 'update'
New-Item -ItemType Directory -Force -Path $updates | Out-Null
$file = Join-Path $updates 'cfucli.json'
$sha = (Get-FileHash -Algorithm SHA256 (Join-Path $out 'cfucli.exe')).Hash.ToLower()
$release = [ordered]@{ version = $version; released = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    exe = [ordered]@{ 'windows-x86_64' = [ordered]@{ sha256 = $sha; urls = @("https://github.com/cfucli/cfucli/releases/download/$tag/cfucli.exe") } } }
$releases = @($release)
if (Test-Path $file) { $releases += @((Get-Content -Raw $file | ConvertFrom-Json).releases | Where-Object { $_.version -ne $version }) }
$doc = [ordered]@{ format = 1; app = 'io.github.cfucli:cfucli'; channels = [ordered]@{ stable = $version }; releases = $releases }
[IO.File]::WriteAllText($file, ($doc | ConvertTo-Json -Depth 10), (New-Object Text.UTF8Encoding $false))
Write-Host "update file written to $file - commit and push the site repo to offer $version to -Xjr:update"
