# cfucli installer.
#
# It does two things, and neither can be done by unzipping:
#
#   1. Rewrites the two .jrc files to name the jars by ABSOLUTE path. jr.exe finds its .jrc beside
#      itself whatever directory you are standing in, but it hands the -jar path to the JVM
#      untouched - so a relative one is resolved against the CURRENT directory. Double-clicking the
#      window from Explorer happens to work, because Explorer sets the current directory to the
#      folder; running "cfucli" from a terminal anywhere else fails with "Unable to access
#      jarfile". That difference is exactly the kind that gets reported as "it works for me".
#
#   2. Adds this folder to the current user's PATH, so "cfucli" is a command.
#
# Re-running is safe, and moving the folder and running it again re-points everything.
#
#   install.cmd              install
#   install.cmd -NoPath      stamp the jar paths but leave PATH alone
#   install.cmd -NoShortcut  do not put a shortcut on the desktop
#   install.cmd -NoPause     do not wait for a keypress (for scripts)
param([switch]$NoPath, [switch]$NoShortcut, [switch]$NoPause)

$ErrorActionPreference = 'Stop'

$here = (Split-Path -Parent $MyInvocation.MyCommand.Path).TrimEnd('\')

function Fail($m) { Write-Host ''; Write-Host ("  [!!] {0}" -f $m) -ForegroundColor Red; Write-Host ''; exit 1 }

Write-Host ''
Write-Host 'cfucli installer'
Write-Host ("  folder : {0}" -f $here)
Write-Host ''

# ---- java ---------------------------------------------------------------------------------
$javaCmd = Get-Command java.exe -ErrorAction SilentlyContinue
if (-not $javaCmd) { Fail 'java.exe is not on PATH. Install JDK 25 and run this again.' }
# cmd does the stderr redirect: PowerShell 5.1 wraps a native command's stderr in ErrorRecords,
# which with $ErrorActionPreference = 'Stop' would abort the install on a perfectly normal line.
$verText = (cmd /c "java -version 2>&1" | Out-String)
$major = 0
if ($verText -match 'version "(\d+)') { $major = [int]$Matches[1] }
if ($major -eq 0) {
    Write-Host ("  [??] java found at {0} but its version could not be read; continuing." -f $javaCmd.Source)
} elseif ($major -lt 25) {
    # Not a preference: these jars are compiled with release 25 and carry JavaFX 25, so an older
    # JDK fails at class-load with UnsupportedClassVersionError rather than doing anything useful.
    Fail ("java {0} is too old - these jars need JDK 25. Found at {1}" -f $major, $javaCmd.Source)
} else {
    Write-Host ("  [ok] java {0}  ({1})" -f $major, $javaCmd.Source)
}

# ---- the archive really was unzipped whole -------------------------------------------------
$want = @{ 'cfucliapp.exe' = 'cfucli-app.jar'; 'cfucli.exe' = 'cfucli.jar' }
foreach ($exe in $want.Keys) {
    if (-not (Test-Path (Join-Path $here $exe))) { Fail "$exe is missing - unzip the whole archive, not part of it." }
    if (-not (Test-Path (Join-Path $here $want[$exe]))) { Fail ("{0} is missing." -f $want[$exe]) }
}
Write-Host '  [ok] both programs and both jars are here'

# ---- stamp absolute jar paths ---------------------------------------------------------------
foreach ($exe in $want.Keys) {
    $jrc = Join-Path $here ([IO.Path]::GetFileNameWithoutExtension($exe) + '.jrc')
    if (-not (Test-Path $jrc)) { Fail ("{0} is missing." -f (Split-Path -Leaf $jrc)) }
    $jar = Join-Path $here $want[$exe]
    $line = 'java.args=-jar "' + $jar + '"'
    $stamped = $false
    # Line by line rather than a regex replace: a Windows path is full of backslashes and can carry
    # a dollar sign, both of which mean something in a replacement string.
    $out = foreach ($l in (Get-Content -LiteralPath $jrc)) {
        if (-not $stamped -and $l -match '^\s*java\.args\s*=') { $stamped = $true; $line } else { $l }
    }
    if (-not $stamped) { $out = @($out) + '' + $line }
    Set-Content -LiteralPath $jrc -Value $out -Encoding ASCII
    Write-Host ("  [ok] {0} -> {1}" -f (Split-Path -Leaf $jrc), $want[$exe])
}

# ---- PATH -------------------------------------------------------------------------------------
# SetEnvironmentVariable(...,'User') and not setx: setx truncates at 1024 characters, and "%PATH%"
# in a batch file is the system and user paths already merged, so the usual one-liner quietly
# copies the whole system PATH into the user one.
if ($NoPath) {
    Write-Host '  [--] -NoPath given, so PATH was left alone.'
} else {
    $userPath = [Environment]::GetEnvironmentVariable('PATH', 'User')
    if ($null -eq $userPath) { $userPath = '' }
    $entries = @($userPath -split ';' | Where-Object { $_.Trim() -ne '' })
    if (@($entries | Where-Object { $_.Trim().TrimEnd('\') -eq $here }).Count -gt 0) {
        Write-Host '  [ok] this folder is already on your PATH'
    } else {
        [Environment]::SetEnvironmentVariable('PATH', ((@($entries) + $here) -join ';'), 'User')
        Write-Host '  [ok] this folder added to your user PATH'
    }
}

# ---- desktop shortcut to the window ------------------------------------------------------------
# The README tells the person to double-click the window, so give them something to double-click
# that is not buried in whatever folder they unzipped into.
if ($NoShortcut) {
    Write-Host '  [--] -NoShortcut given.'
} else {
    try {
        $lnk = Join-Path ([Environment]::GetFolderPath('Desktop')) 'cfucli.lnk'
        $sh = New-Object -ComObject WScript.Shell
        $s = $sh.CreateShortcut($lnk)
        $s.TargetPath = Join-Path $here 'cfucliapp.exe'
        $s.WorkingDirectory = $here
        $s.Description = 'Share this machine terminal with someone you trust'
        $s.Save()
        Write-Host ("  [ok] desktop shortcut: {0}" -f $lnk)
    } catch {
        Write-Host ("  [??] could not create the desktop shortcut ({0})" -f $_.Exception.Message)
    }
}

# ---- prove the command works from somewhere else ------------------------------------------------
# Run it with the current directory deliberately NOT this folder, because that is the case the
# stamping above exists to fix and the one that was broken in the shipped zip.
Write-Host ''
$out = cmd /c ('cd /d "%TEMP%" && "' + (Join-Path $here 'cfucli.exe') + '" relay show 2>&1')
$rc = $LASTEXITCODE
$out | ForEach-Object { Write-Host ("       {0}" -f $_) }
Write-Host ''
if ($rc -ne 0) { Fail ("cfucli relay show exited {0} - see above." -f $rc) }

Write-Host '  cfucli is installed.' -ForegroundColor Green
Write-Host ''
Write-Host '  Double-click the desktop shortcut, or open a NEW terminal and run:'
Write-Host ''
Write-Host '      cfucli relay show      is this machine connected to a relay yet'
Write-Host '      cfucli guide           the manual'
Write-Host ''
Write-Host '  README.txt in this folder explains sharing your machine with someone.'
Write-Host ''
exit 0
