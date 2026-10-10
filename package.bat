@echo off
rem ---------------------------------------------------------------------------
rem  KinderJournal - build a version you can hand to a teacher.
rem
rem  Produces a self-contained desktop application (no Java installation
rem  needed) and, on request, a Windows installer.
rem
rem    package.bat            app folder + optional installer prompt
rem    package.bat -Zip       also write a .zip of the app folder
rem    package.bat -Installer build the installer without asking
rem    package.bat -NoInstaller  app folder only
rem ---------------------------------------------------------------------------
setlocal
cd /d "%~dp0"

where pwsh >nul 2>nul
if errorlevel 1 (
  where powershell >nul 2>nul
  if errorlevel 1 (
    echo ERROR: PowerShell is required to package the application. 1>&2
    exit /b 1
  )
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\package.ps1" %*
) else (
  pwsh -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\package.ps1" %*
)

exit /b %ERRORLEVEL%
