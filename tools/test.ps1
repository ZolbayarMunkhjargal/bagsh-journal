# KinderJournal test runner - runs the headless self-tests.
#
#   tools\test.ps1

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

& (Join-Path $PSScriptRoot 'build.ps1')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$props = @{}
foreach ($line in Get-Content (Join-Path $root '.tools\deps.properties')) {
    if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
    $kv = $line.Split('=', 2)
    $props[$kv[0].Trim()] = $kv[1].Trim()
}
$libDir = Join-Path $root '.tools\lib'
$classpath = (@($props['classpath'] -split ';' | Where-Object { $_ }) |
    ForEach-Object { Join-Path $libDir $_ }) -join ';'

$java = (Get-Command java -ErrorAction SilentlyContinue).Source
$toolchain = Join-Path $root 'build\toolchain.json'
if (Test-Path $toolchain) {
    $tc = Get-Content $toolchain -Raw | ConvertFrom-Json
    if ($tc.java -and (Test-Path $tc.java)) { $java = $tc.java }
}

$classesDir = Join-Path $root 'build\classes'
& $java -classpath "$classesDir;$classpath" app.kinderjournal.tools.SelfTest
exit $LASTEXITCODE
