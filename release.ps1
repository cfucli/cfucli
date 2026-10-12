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
# (see the profiles in bundle/pom.xml): cfucli-win-<sha8>.jar, and for the Macs cfucli-mac-aarch64-<sha8>.jar and
# cfucli-mac-<sha8>.jar, each run by its macOS binary (cfucli-macos-arm64, cfucli-macos-x86_64; since 0.5). The
# unhashed cfucli-mac.jar and cfucli-mac-aarch64.jar are still published for 0.4's install.sh.
# With no arguments the jar opens the window; with any, it is the cli.
#
# The Windows launcher cfucli.exe is jr (github.com/jarrunner/jr), built in the maven build itself
# by jr-maven-plugin (the jrexe profile in pom.xml, configured in bundle/pom.xml): it carries its
# config, the sha256 of cfucli-win-<sha8>.jar and this release's download url baked in, plus the icon and
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
# the exe, cfucli-win-<sha8>.jar (the bytes it hashed), the Mac jars, version.txt, SHA256SUMS and
# cfucli.update.json. -Djr.updateMergeFrom keeps the releases already in the site's update file.
$jrArgs = @('-Djrexe', '-Djr.source=url', '-pl', 'bundle', 'verify')
# X.Y.Z-label (0.5.0-beta.1) is a pre-release: its exe follows the beta channel of the update file, and the
# stable channel, which every installed X.Y.Z follows, is kept as it was.
$pre = $version -match '-'
if ($pre) { $jrArgs = @('-Djrexe.channel=beta') + $jrArgs }
# @(...) around the if: an if that yields one string gives a string, and string + array would join them into one argument
$merge = @(if (Test-Path $update) { "-Djr.updateMergeFrom=$((Resolve-Path $update).Path)" })
Build ($merge + $jrArgs)                                      # cfucli.exe and the release folder

# Then one run per Mac architecture (0.5): each builds that architecture's jar and the macOS binary that
# runs it (cfucli-macos-arm64, cfucli-macos-x86_64; bundle/pom.xml), and merges the update file of the
# run before, so the last one lists Windows and both Macs under this version. Every run writes its own
# release folder, so they are gathered into one.
$all = Join-Path $PSScriptRoot 'bundle\target\jr\release-all'
if (Test-Path $all) { Remove-Item -Recurse -Force $all }
New-Item -ItemType Directory -Path $all | Out-Null
Copy-Item (Join-Path $out '*') $all
foreach ($p in @('mac-aarch64', 'mac')) {
    Build (@("-P$p", "-Djr.updateMergeFrom=$(Join-Path $all 'cfucli.update.json')") + $jrArgs)
    Copy-Item (Join-Path $out '*') $all -Force
}
$out = $all
# SHA256SUMS over everything gathered (each run wrote one for its own files only); the update file is not an asset.
$sums = Get-ChildItem $out -File | Where-Object { $_.Name -notin @('SHA256SUMS', 'cfucli.update.json') } | Sort-Object Name |
    ForEach-Object { "$((Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower())  $($_.Name)" }
[IO.File]::WriteAllText((Join-Path $out 'SHA256SUMS'), (($sums -join "`n") + "`n"))

$updateOut = Join-Path $out 'cfucli.update.json'
# cfucli-win-<sha8>.jar: the Windows jar is named after its own sha256 (bundle/pom.xml), so an exe
# from any other build of this version asks for a file that does not exist rather than a jar that
# does not match it. The Mac jars likewise, each beside the binary that carries its sha256.
foreach ($f in @('cfucli.exe', 'cfucli-win-*.jar', 'cfucli-macos-arm64', 'cfucli-mac-aarch64-*.jar', 'cfucli-macos-x86_64', 'cfucli-mac-????????.jar',
                 'cfucli-mac.jar', 'cfucli-mac-aarch64.jar', 'version.txt', 'SHA256SUMS', 'cfucli.update.json')) {
    if (-not (Test-Path (Join-Path $out $f))) { throw "the release folder has no $f - see the jr:exe output above" }
}
$jrRepo = $env:JR_REPO
$jrCommit = if ($jrRepo) { @(git -C $jrRepo rev-parse HEAD 2>$null) | Where-Object { $_ -match '^[0-9a-f]{40}$' } | Select-Object -Last 1 }

Get-ChildItem $out | ForEach-Object { Write-Host ('  {0,-28} {1,7:N1} MB' -f $_.Name, ($_.Length / 1MB)) }
if ($DryRun) { Write-Host "dry run - nothing published. Assets are in $out"; return }

$exeUrl = "https://github.com/cfucli/cfucli/releases/download/$tag/cfucli.exe"
$notes = "Install on Windows, in cmd or PowerShell (curl.exe ships with Windows 10 and 11; no installer script):`n`n    curl.exe -fLo cfucli.exe $exeUrl`n    .\cfucli.exe install" +
         "`n`nOr download cfucli.exe, run it, and press F6. Update later with ``cfucli update``." +
         "`n`nInstall on a Mac, in Terminal (one line; uname -m picks Apple silicon or Intel):`n`n    curl -fLo /tmp/cfucli https://github.com/cfucli/cfucli/releases/download/$tag/cfucli-macos-`$(uname -m) && chmod +x /tmp/cfucli && /tmp/cfucli install" +
         "`n`nThat puts cfucli.app in ~/Applications (Launchpad, the Dock) and the cfucli command in ~/.local/bin. It is signed ad hoc, not by an Apple developer: downloaded with curl it opens as is; downloaded with a browser, macOS asks once (System Settings, Privacy & Security, Open Anyway)." +
         "`n`ncfucli.exe is a single file, the command line and the window in one (run it with no arguments for the window): on first run it fetches its jar from this release, checked against the sha256 it carries." +
         "`n`n" + $(if ($pre) { "This is a pre-release: installed copies of earlier releases are not offered it, and it updates along the beta channel." } else { "" }) +
         "`ncfucli.exe is jr (https://github.com/jarrunner/jr), built by jr-maven-plugin." +
         "`njr commit: $(if ($jrCommit) { $jrCommit } else { 'unknown' })"
# Through a file: Windows PowerShell passes a multi-line string to a native program cut at the first
# line break - measured, v0.3's notes arrived as their first line only, losing the jr provenance.
$notesFile = Join-Path $out '..\release-notes.md'
[IO.File]::WriteAllText($notesFile, $notes + "`n")
# Every file in the folder is an asset except the update file, which goes to the site instead.
$assets = (Get-ChildItem $out -File | Where-Object Name -ne 'cfucli.update.json').FullName
# --latest=true, not a bare --latest: through a gh wrapper .bat the bare flag failed with "no matches found for `-`"
# and nothing was published (0.5.0, 2026-10-12); the =value form is what the pre-release path always used.
$latest = if ($pre) { @('--prerelease', '--latest=false') } else { @('--latest=true') }
& $gh release create $tag $assets --repo cfucli/cfucli --target (Sha HEAD) --title "cfucli $version" --notes-file $notesFile @latest
if ($LASTEXITCODE -ne 0) { throw 'gh release create failed' }
Write-Host $(if ($pre) { "published $tag as a pre-release - releases/latest and the stable channel are unchanged" } else { "published $tag - installers now pick it up" })

# The update file "cfucli -Xjr:update" reads, written by the plugin. Copied to the site only after the
# release exists, so a url never points at an asset that is not there yet.
New-Item -ItemType Directory -Force -Path (Split-Path $update) | Out-Null
Copy-Item $updateOut $update -Force
Write-Host "update file written to $update - commit and push the site repo to offer $version to -Xjr:update"
