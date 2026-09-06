# Assemble the distributable: dist/cfucli/ plus a zip of it.
#
# Everything that is not a jar lives in pkg/ and is version-controlled. The two launchers are
# copies of jr.exe under different names, taken at package time rather than committed, because a
# binary in the repo goes stale silently and jr is its own project.
#
# The settings.toml staged here has its three credential lines EMPTY, and pkg/ holds it under
# settings.toml.template so that .gitignore can go on refusing anything actually named
# settings.toml. That is what makes this zip publishable: it carries the software and no secret.
# Joining a relay is a separate step the recipient does once, in the window or with "relay set".
#
#   .\make-dist.ps1              package from the jars already built
#   .\make-dist.ps1 -Build       run the maven build first

param([switch]$Build)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$version = ([xml](Get-Content -LiteralPath (Join-Path $root 'pom.xml'))).project.version
$name    = "cfucli-$version-win-x64"
$appJar  = Join-Path $root 'app\shade\cfucli-app.jar'
$cliJar  = Join-Path $root 'cli\shade\cfucli.jar'
$stage   = Join-Path $root 'dist\cfucli'
$zip     = Join-Path $root "dist\$name.zip"

if ($Build) {
    # A node loads classes lazily, so replacing a jar under a running one leaves it able to run for
    # hours and then fail on a class it never happened to load. Stop them, then build.
    & cfucli node stop 2>&1 | Out-Null
    & mvn -o -q -DskipTests install
    if ($LASTEXITCODE -ne 0) { throw "maven build failed ($LASTEXITCODE)" }
}
foreach ($j in @($appJar, $cliJar)) {
    if (-not (Test-Path $j)) { throw "missing $j - run: mvn -o -DskipTests install" }
}

if (Test-Path (Join-Path $root 'dist')) { Remove-Item -Recurse -Force (Join-Path $root 'dist') }
New-Item -ItemType Directory -Force -Path $stage | Out-Null

$jrCandidates = @((Join-Path $root '..\jr\jr.exe')) + @((Get-Command jr.exe -ErrorAction SilentlyContinue).Source)
$jrExe = $jrCandidates | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
if (-not $jrExe) { throw "no jr.exe found - clone https://github.com/littlejlib/jr next to this project" }

Copy-Item $jrExe (Join-Path $stage 'cfucliapp.exe')
Copy-Item $jrExe (Join-Path $stage 'cfucli.exe')
Copy-Item (Join-Path $root 'pkg\cfucliapp.jrc') $stage
Copy-Item (Join-Path $root 'pkg\cfucli.jrc') $stage
Copy-Item (Join-Path $root 'pkg\README.txt') $stage
Copy-Item (Join-Path $root 'pkg\install.cmd') $stage
Copy-Item (Join-Path $root 'pkg\install.ps1') $stage
Copy-Item (Join-Path $root 'pkg\settings.toml.template') (Join-Path $stage 'settings.toml')
Copy-Item $appJar $stage
Copy-Item $cliJar $stage

# Refuse to ship a secret, rather than trusting that the template was the file copied. A scan with
# no positive control proves nothing, so this also checks it can see a string it knows is there.
$staged = Join-Path $stage 'settings.toml'
$text = Get-Content -Raw -LiteralPath $staged
if ($text -notmatch 'UPSTASH_REDIS_REST_TOKEN') { throw "sanity check failed: cannot even see the key names in $staged" }
foreach ($pattern in @('rediss://', 'upstash\.io')) {
    if ($text -match $pattern) { throw "REFUSING TO PACKAGE: $staged contains a real credential ($pattern)" }
}

Compress-Archive -Path $stage -DestinationPath $zip -CompressionLevel Optimal

$mb = [math]::Round((Get-Item $zip).Length / 1MB, 1)
Write-Host ''
Write-Host ("  launcher from : {0}" -f (Resolve-Path $jrExe))
Write-Host ("  staged        : {0}" -f $stage)
Get-ChildItem $stage | ForEach-Object { Write-Host ("                  {0,-24} {1,12:N0}" -f $_.Name, $_.Length) }
Write-Host ''
Write-Host ("  zip           : {0}  ({1} MB)" -f $zip, $mb)
Write-Host '  credentials   : none - the shipped settings.toml has empty values, checked above'
Write-Host ''
