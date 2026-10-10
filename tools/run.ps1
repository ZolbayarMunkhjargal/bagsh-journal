# KinderJournal launch script - builds if necessary, then starts the app.
#
#   tools\run.ps1              normal launch
#   tools\run.ps1 -DataDir D   use an alternative data folder (handy for tests)
#   tools\run.ps1 -SkipBuild   launch whatever is already compiled

[CmdletBinding()]
param(
    [string]$DataDir,
    [switch]$SkipBuild,
    [switch]$Clean,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$AppArgs
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$classesDir = Join-Path $root 'build\classes'
$toolchain = Join-Path $root 'build\toolchain.json'
$manifest = Join-Path $root '.tools\deps.properties'

if (-not $SkipBuild -or -not (Test-Path $classesDir)) {
    $buildArgs = @()
    if ($Clean) { $buildArgs += '-Clean' }
    & (Join-Path $PSScriptRoot 'build.ps1') @buildArgs
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

if (-not (Test-Path $manifest)) {
    Write-Host 'Missing .tools\deps.properties - run build.bat first.' -ForegroundColor Red
    exit 1
}

$props = @{}
foreach ($line in Get-Content $manifest) {
    if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
    $kv = $line.Split('=', 2)
    $props[$kv[0].Trim()] = $kv[1].Trim()
}
$libDir = Join-Path $root '.tools\lib'
$classpath = (@($props['classpath'] -split ';' | Where-Object { $_ }) |
    ForEach-Object { Join-Path $libDir $_ }) -join ';'
$modulepath = (@($props['modulepath'] -split ';' | Where-Object { $_ }) |
    ForEach-Object { Join-Path $libDir $_ }) -join ';'

$java = (Get-Command java -ErrorAction SilentlyContinue).Source
if (Test-Path $toolchain) {
    $tc = Get-Content $toolchain -Raw | ConvertFrom-Json
    if ($tc.java -and (Test-Path $tc.java)) { $java = $tc.java }
}
if (-not $java) {
    Write-Host 'No java.exe found. Run build.bat first.' -ForegroundColor Red
    exit 1
}

if ($DataDir) { $env:KINDERJOURNAL_DATA_DIR = (Resolve-Path -LiteralPath $DataDir).Path }

$javaArgs = @(
    '--module-path', $modulepath,
    '--add-modules', 'javafx.controls,javafx.graphics,javafx.base',
    '-classpath', "$classesDir;$classpath",
    'app.kinderjournal.App'
)
if ($AppArgs) { $javaArgs += $AppArgs }

& $java @javaArgs
exit $LASTEXITCODE
