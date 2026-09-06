@echo off
rem cfucli installer. Double-click this, or run it from a terminal.
rem
rem It points the two launchers at the jars beside them by absolute path, and puts this folder on
rem your PATH. Unzipping cannot do either. See install.ps1 for why the absolute path matters.
setlocal EnableDelayedExpansion
set "ARGS=%*"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" !ARGS!
set "RC=!ERRORLEVEL!"
rem A double-click gets a window that would otherwise vanish before it could be read, so pause by
rem default; a script passes -NoPause, because a pause with no keyboard behind it just hangs.
echo !ARGS! | find /i "-NoPause" >nul
if errorlevel 1 (
  echo.
  pause
)
exit /b !RC!
