@echo off
rem ---------------------------------------------------------------------------
rem  KinderJournal - build (compile the app).
rem  Requires JDK 21+ and Node.js. Dependencies are fetched once into .tools\lib.
rem ---------------------------------------------------------------------------
setlocal
cd /d "%~dp0"

where pwsh >nul 2>nul
if errorlevel 1 (
  where powershell >nul 2>nul
  if errorlevel 1 (
    echo ERROR: PowerShell is required to build. 1>&2
    exit /b 1
  )
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\build.ps1" %*
) else (
  pwsh -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\build.ps1" %*
)

exit /b %ERRORLEVEL%
