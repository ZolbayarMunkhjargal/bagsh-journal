# KinderJournal - create sample data
#
#   tools\seed-demo.ps1                     sample data in the normal data folder
#   tools\seed-demo.ps1 -DataDir C:\Temp\kj  sample data in a separate folder
#
# The sample data is added to whatever is already there, so use -DataDir when
# you want to keep it away from real records. It is safe to run more than once:
# classes and children are only created when they are not already present.

[CmdletBinding()]
param(
    [string]$DataDir
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$runArgs = @('-File', (Join-Path $PSScriptRoot 'run.ps1'), '-AppArgs', '--seed-demo')
if ($DataDir) {
    if (-not (Test-Path $DataDir)) {
        New-Item -ItemType Directory -Force -Path $DataDir | Out-Null
    }
    $runArgs += @('-DataDir', (Resolve-Path -LiteralPath $DataDir).Path)
}

& pwsh -NoProfile -ExecutionPolicy Bypass @runArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Sample data could not be created.' -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host ''
Write-Host 'Sample data is ready. Start the program with run.bat' -ForegroundColor Green
if ($DataDir) {
    Write-Host "It is in: $((Resolve-Path -LiteralPath $DataDir).Path)" -ForegroundColor DarkGray
    Write-Host 'Start the program on it with:  run.bat --data-dir "' -NoNewline
    Write-Host ((Resolve-Path -LiteralPath $DataDir).Path) -NoNewline
    Write-Host '"'
} else {
    Write-Host 'To remove it later, delete the data folder shown by run.bat --version' -ForegroundColor DarkGray
}
