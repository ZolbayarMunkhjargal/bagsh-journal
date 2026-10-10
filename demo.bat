@echo off
rem ---------------------------------------------------------------------------
rem  KinderJournal - useful extras.
rem
rem    demo.bat                  fill the data folder with sample classes and
rem                              children so you can try everything out
rem    demo.bat C:\Temp\kjdemo   put the sample data in a separate folder
rem    checks.bat                run the data-layer tests and the interface checks
rem    help.bat                  list these options
rem ---------------------------------------------------------------------------
setlocal
cd /d "%~dp0"

where pwsh >nul 2>nul
if errorlevel 1 (
  where powershell >nul 2>nul
  if errorlevel 1 (
    echo ERROR: PowerShell is required. 1>&2
    exit /b 1
  )
  set "PS=powershell -NoProfile -ExecutionPolicy Bypass"
) else (
  set "PS=pwsh -NoProfile -ExecutionPolicy Bypass"
)

if /i "%~1"=="checks" goto checks
if /i "%~1"=="demo" goto demo
if /i "%~1"=="help" goto help
if /i "%~1"=="--help" goto help
goto demo

:demo
echo Creating sample data ^(classes, children, attendance, observations, interviews, plans, photos^)...
%PS% -File "%~dp0tools\seed-demo.ps1" -DataDir "%~1"
exit /b %ERRORLEVEL%

:checks
echo Running the data-layer tests...
%PS% -File "%~dp0tools\test.ps1"
if errorlevel 1 exit /b 1
echo.
echo Running the interface checks...
%PS% -File "%~dp0tools\run.ps1" -SkipBuild -AppArgs --ui-test
exit /b %ERRORLEVEL%

:help
echo KinderJournal helpful extras
echo.
echo   demo.bat                  create sample data in your own data folder
echo   demo.bat C:\Temp\kjdemo   create sample data in that folder instead
echo   checks.bat                run the data-layer tests and the interface checks
echo.
echo The sample data is added to whatever is already there, so pass a folder to
echo keep it away from real records.
exit /b 0
