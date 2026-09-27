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
# The Windows launchers cfucli.exe and cfucliapp.exe are jr (github.com/littlejlib/jr) branded with
# the logo and version info by jr's own -Xjr:make, so Task Manager shows "cfucli" with the seal and
# the JVM runs inside the named process with an AOT cache. $env:JR_EXE names the jr.exe to brand -
# a build of a PUBLIC jr commit, whose hash and checksum go into the release notes so the exes can
# be traced to source. $env:CFUCLI_ICO overrides the icon (default: the site repo's favicon, which
# sits beside this repo in the workspace).
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

$jr = $env:JR_EXE
if (-not $jr -or -not (Test-Path $jr)) { throw 'set JR_EXE to the jr.exe to brand as cfucli.exe and cfucliapp.exe (a build of a pushed jr commit)' }
$ico = if ($env:CFUCLI_ICO) { $env:CFUCLI_ICO } else { Join-Path $PSScriptRoot '..\cfucli.github.io\assets\cfucli-favicon.ico' }
if (-not (Test-Path $ico)) { throw "no icon at $ico - set CFUCLI_ICO" }
# Windows version resources are four numbers; pom versions are two or three.
$parts = @($version -split '[.-]' | Where-Object { $_ -match '^\d+$' } | Select-Object -First 4)
while ($parts.Count -lt 4) { $parts += '0' }
$winVersion = $parts -join '.'

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

# The Windows launchers. FileDescription is the name Task Manager shows for the process.
foreach ($l in @(@('cfucli.exe', 'cfucli'), @('cfucliapp.exe', 'cfucli window'))) {
    & $jr "-Xjr:make=$(Join-Path $out $l[0])" "-Xjr:icon=$ico" "-Xjr:version=$winVersion" `
        "-Xjr:version.FileDescription=$($l[1])" '-Xjr:version.ProductName=cfucli' '-Xjr:version.CompanyName=cfucli' `
        "-Xjr:version.ProductVersion=$version" '-Xjr:version.LegalCopyright=cfucli contributors' | Out-Null
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path (Join-Path $out $l[0]))) { throw "jr could not make $($l[0])" }
}
$jrSha = (Get-FileHash -Algorithm SHA256 $jr).Hash.ToLower()
$jrCommit = @(git -C (Split-Path $jr) rev-parse HEAD 2>$null) | Where-Object { $_ -match '^[0-9a-f]{40}$' } | Select-Object -Last 1

# LF line endings and two spaces - the format shasum/sha256sum write and the installers parse.
$sums = Get-ChildItem $out -File | Where-Object Name -ne 'SHA256SUMS' | Sort-Object Name |
    ForEach-Object { '{0}  {1}' -f (Get-FileHash -Algorithm SHA256 $_.FullName).Hash.ToLower(), $_.Name }
[IO.File]::WriteAllText((Join-Path $out 'SHA256SUMS'), (($sums -join "`n") + "`n"))

Get-ChildItem $out | ForEach-Object { Write-Host ('  {0,-28} {1,7:N1} MB' -f $_.Name, ($_.Length / 1MB)) }
if ($DryRun) { Write-Host "dry run - nothing published. Assets are in $out"; return }

$notes = "Install or update with one line.`n`nmacOS:  curl -fsSL https://cfucli.github.io/install.sh | bash`nWindows (PowerShell):  irm https://cfucli.github.io/install.ps1 | iex" +
         "`n`ncfucli.exe and cfucliapp.exe are jr (https://github.com/littlejlib/jr) branded with jr's -Xjr:make." +
         "`njr commit: $(if ($jrCommit) { $jrCommit } else { 'unknown' })`njr.exe sha256: $jrSha"
# Through a file: Windows PowerShell passes a multi-line string to a native program cut at the first
# line break - measured, v0.3's notes arrived as their first line only, losing the jr provenance.
$notesFile = Join-Path $out '..\release-notes.md'
[IO.File]::WriteAllText($notesFile, $notes + "`n")
& $gh release create $tag (Get-ChildItem $out -File).FullName --repo cfucli/cfucli --target (Sha HEAD) --title "cfucli $version" --notes-file $notesFile --latest
if ($LASTEXITCODE -ne 0) { throw 'gh release create failed' }
Write-Host "published $tag - installers now pick it up"
