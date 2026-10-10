# KinderJournal build script
#
# Resolves a JDK 21+, ensures the JavaFX / SQLite JDBC dependencies are present,
# then compiles all sources into build/classes.

[CmdletBinding()]
param(
    [switch]$Force,          # re-download dependencies
    [switch]$Clean           # wipe build/ before compiling
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Write-Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "    $msg" -ForegroundColor DarkGray }
function Fail($msg) {
    Write-Host ""
    Write-Host "BUILD FAILED: $msg" -ForegroundColor Red
    exit 1
}

# --- Locate a JDK (javac) -----------------------------------------------------
Write-Step 'Locating JDK'
$javac = $null
$candidates = @()
if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\javac.exe') }
$cmd = Get-Command javac -ErrorAction SilentlyContinue
if ($cmd) { $candidates += $cmd.Source }
foreach ($c in $candidates) {
    if (-not $c -or -not (Test-Path $c)) { continue }
    $verText = (& $c -version 2>&1 | Out-String)
    if ($verText -match 'javac\s+(\d+)') {
        $major = [int]$Matches[1]
        if ($major -ge 21) { $javac = $c; Write-Ok "javac $major -> $c"; break }
        Write-Ok "skipping javac $major (need 21+) at $c"
    }
}
if (-not $javac) {
    Fail @"
No JDK 21 or newer found.
Install a JDK 21 (e.g. https://adoptium.net/temurin/releases/?version=21) or set
JAVA_HOME to point at one, then run build.bat again.
"@
}
$java = Join-Path (Split-Path -Parent $javac) 'java.exe'
$jdkHome = Split-Path -Parent (Split-Path -Parent $javac)

# --- Dependencies -------------------------------------------------------------
$libDir = Join-Path $root '.tools\lib'
$manifest = Join-Path $root '.tools\deps.properties'
$needsBootstrap = $Force -or -not (Test-Path $manifest)

if (-not $needsBootstrap) {
    $props = @{}
    foreach ($line in Get-Content $manifest) {
        if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
        $kv = $line.Split('=', 2)
        $props[$kv[0].Trim()] = $kv[1].Trim()
    }
    foreach ($jar in ($props['classpath'] -split ';')) {
        if (-not (Test-Path (Join-Path $libDir $jar))) { $needsBootstrap = $true; break }
    }
}

if ($needsBootstrap) {
    Write-Step 'Fetching dependencies (JavaFX + SQLite JDBC)'
    $node = Get-Command node -ErrorAction SilentlyContinue
    if (-not $node) {
        Fail @"
Node.js is required once, to download the JavaFX and SQLite dependencies.
Install it from https://nodejs.org (or copy a .tools\lib folder from another
machine that has already built this project).
"@
    }
    $bootArgs = @((Join-Path $PSScriptRoot 'bootstrap.mjs'))
    if ($Force) { $bootArgs += '--force' }
    & $node.Source @bootArgs
    if ($LASTEXITCODE -ne 0) { Fail 'Dependency download failed.' }
}
else {
    Write-Step 'Dependencies already present'
    Write-Ok ".tools\lib"
}

$props = @{}
foreach ($line in Get-Content $manifest) {
    if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
    $kv = $line.Split('=', 2)
    $props[$kv[0].Trim()] = $kv[1].Trim()
}
$classpathJars = @($props['classpath'] -split ';' | Where-Object { $_ })
$modulepathJars = @($props['modulepath'] -split ';' | Where-Object { $_ })
$classpath = ($classpathJars | ForEach-Object { Join-Path $libDir $_ }) -join ';'
$modulepath = ($modulepathJars | ForEach-Object { Join-Path $libDir $_ }) -join ';'

# --- Compile ------------------------------------------------------------------
$buildDir = Join-Path $root 'build'
$classesDir = Join-Path $buildDir 'classes'
if ($Clean -and (Test-Path $buildDir)) {
    Write-Step 'Cleaning build output'
    Remove-Item $buildDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $classesDir | Out-Null

Write-Step 'Compiling sources'
$sources = Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter '*.java' |
    Select-Object -ExpandProperty FullName
if (-not $sources) { Fail 'No Java sources found under src\main\java.' }

$argFile = Join-Path $buildDir 'javac.args'
# javac's @argfile treats backslash as an escape character, so every path is
# written with forward slashes, and the file must have no byte-order mark.
function ConvertTo-JavacPath([string]$path) { return $path.Replace('\', '/') }

$lines = @(
    '-encoding', 'UTF-8',
    '-d', "`"$(ConvertTo-JavacPath $classesDir)`"",
    '--module-path', "`"$(ConvertTo-JavacPath $modulepath)`"",
    '--add-modules', 'javafx.controls,javafx.graphics,javafx.base',
    '-classpath', "`"$(ConvertTo-JavacPath $classpath)`"",
    '-Xlint:all,-serial'
)
foreach ($s in $sources) { $lines += "`"$(ConvertTo-JavacPath $s)`"" }
[System.IO.File]::WriteAllLines($argFile, $lines, (New-Object System.Text.UTF8Encoding($false)))

$sw = [System.Diagnostics.Stopwatch]::StartNew()
& $javac "@$argFile"
$code = $LASTEXITCODE
$sw.Stop()
if ($code -ne 0) { Fail "javac reported errors (exit $code)." }

# --- Copy resources -----------------------------------------------------------
$resDir = Join-Path $root 'src\main\resources'
if (Test-Path $resDir) {
    Write-Step 'Copying resources'
    Copy-Item (Join-Path $resDir '*') -Destination $classesDir -Recurse -Force
}

Write-Host ""
Write-Host ("BUILD OK  ({0} files, {1:N1}s)" -f $sources.Count, $sw.Elapsed.TotalSeconds) -ForegroundColor Green
Write-Ok "classes: $classesDir"

# Remember the JDK for run.ps1 / package.ps1
@{
    java     = $java
    javac    = $javac
    jdkHome  = $jdkHome
    builtAt  = (Get-Date).ToString('s')
} | ConvertTo-Json | Set-Content (Join-Path $buildDir 'toolchain.json') -Encoding UTF8

exit 0
