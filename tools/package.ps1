# KinderJournal packaging script
#
# Produces a self-contained Windows application folder (and optionally an
# installer) that a teacher can run without installing Java.
#
#   tools\package.ps1                      app folder, then ask about an installer
#   tools\package.ps1 -Installer           build the installer without asking
#   tools\package.ps1 -NoInstaller         app folder only
#   tools\package.ps1 -Zip                 also write a .zip of the app folder

[CmdletBinding()]
param(
    [switch]$Installer,
    [switch]$NoInstaller,
    [switch]$Zip,
    [string]$AppName = 'KinderJournal',
    [string]$Version = '1.0.0'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Write-Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "    $msg" -ForegroundColor DarkGray }
function Fail($msg) {
    Write-Host ""
    Write-Host "PACKAGING FAILED: $msg" -ForegroundColor Red
    exit 1
}

# --- 1. Compile ---------------------------------------------------------------
& (Join-Path $PSScriptRoot 'build.ps1')
if ($LASTEXITCODE -ne 0) { Fail 'The build did not succeed.' }

$classesDir = Join-Path $root 'build\classes'
$releaseDir = Join-Path $root 'release'
$inputDir   = Join-Path $root 'build\package-input'
$appOut     = Join-Path $releaseDir "$AppName-$Version-windows"

$toolchain = Get-Content (Join-Path $root 'build\toolchain.json') -Raw | ConvertFrom-Json
$jdkHome = $toolchain.jdkHome
if (-not $jdkHome -or -not (Test-Path $jdkHome)) { Fail 'Could not determine the JDK location.' }
$jpackage = Join-Path $jdkHome 'bin\jpackage.exe'
if (-not (Test-Path $jpackage)) {
    Fail @"
jpackage.exe was not found in $jdkHome.
Packaging needs a full JDK (not a JRE). Install JDK 21 or newer and run build.bat again.
"@
}

# --- 2. Lay out the application's own files -----------------------------------
Write-Step 'Assembling application files'
if (Test-Path $inputDir) { Remove-Item $inputDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $inputDir | Out-Null

# The classes go into a jar so the launcher stays tidy.
$jarFile = Join-Path $inputDir "$AppName.jar"
& (Join-Path $jdkHome 'bin\jar.exe') --create --file $jarFile --main-class app.kinderjournal.App -C $classesDir .
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $jarFile)) { Fail 'Could not build the application jar.' }
Write-Ok "app jar: $(Split-Path -Leaf $jarFile)"

# Third-party libraries sit beside it; jpackage copies the whole folder.
$props = @{}
foreach ($line in Get-Content (Join-Path $root '.tools\deps.properties')) {
    if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
    $kv = $line.Split('=', 2)
    $props[$kv[0].Trim()] = $kv[1].Trim()
}
$libDir = Join-Path $root '.tools\lib'
$runtimeJars = @()
foreach ($jar in ($props['classpath'] -split ';')) {
    if (-not $jar) { continue }
    if ($jar -like 'javafx-*') { continue }   # provided by the bundled JavaFX modules
    Copy-Item (Join-Path $libDir $jar) -Destination $inputDir -Force
    $runtimeJars += $jar
    Write-Ok "library: $jar"
}
$modulePath = (@($props['modulepath'] -split ';' | Where-Object { $_ }) |
    ForEach-Object { Join-Path $libDir $_ }) -join ';'
Copy-Item (Join-Path $root 'README.md') -Destination (Join-Path $inputDir 'README.md') -Force
if (Test-Path (Join-Path $root 'LICENSE')) {
    Copy-Item (Join-Path $root 'LICENSE') -Destination $inputDir -Force
}

# The SQLite driver carries its native library inside the jar and unpacks it into
# the temporary folder the first time it runs. Unpacking it here instead means the
# driver finds it straight away on the library path, which matters because a
# program started from a Windows shortcut is not always given a writable temporary
# folder - and the driver would otherwise try to write inside C:\WINDOWS.
Write-Step 'Placing the SQLite native library'
$sqliteJar = Join-Path $libDir "sqlite-jdbc-$($props['sqlite.version']).jar"
if (-not (Test-Path $sqliteJar)) { Fail "Could not find $sqliteJar" }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($sqliteJar)
try {
    $entry = $archive.Entries |
        Where-Object { $_.FullName -eq 'org/sqlite/native/Windows/x86_64/sqlitejdbc.dll' } |
        Select-Object -First 1
    if (-not $entry) { Fail 'The SQLite driver does not contain the expected Windows library.' }
    $dllTarget = Join-Path $inputDir 'sqlitejdbc.dll'
    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dllTarget, $true)
    Write-Ok "native library: sqlitejdbc.dll ($([math]::Round($entry.Length / 1KB)) KB)"
} finally {
    $archive.Dispose()
}

# --- 3. Build the runtime image ------------------------------------------------
Write-Step 'Building a self-contained application (this takes a minute)'
# jpackage writes <dest>\<name> and refuses to overwrite, so it works in a
# scratch folder that is always cleared and the finished folder is moved into
# place afterwards. That also means a failed run cannot leave a half-built
# application in release\.
$staging = Join-Path $root 'build\package-out'
if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
New-Item -ItemType Directory -Force -Path $staging | Out-Null
if (Test-Path $appOut) { Remove-Item $appOut -Recurse -Force }
New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null

$jpArgs = @(
    '--type', 'app-image',
    '--name', $AppName,
    '--app-version', $Version,
    '--input', $inputDir,
    '--main-jar', "$AppName.jar",
    '--main-class', 'app.kinderjournal.App',
    '--module-path', $modulePath,
    # java.sql provides the JDBC classes the SQLite driver needs, and java.desktop
    # provides the java.awt.Desktop calls used to open folders and print. Without
    # them jlink produces a runtime that starts and then fails on the first save.
    '--add-modules', 'javafx.controls,javafx.graphics,javafx.base,java.sql,java.desktop',
    '--java-options', '-Xmx512m',
    '--java-options', '-Djava.io.tmpdir=$ROOTDIR\temp',
    '--dest', $staging,
    '--vendor', 'KinderJournal',
    '--description', 'A local journal for kindergarten teachers: attendance, observations, interviews, planning and printable reports.',
    '--win-console'
)
# A friendly icon is used when the project provides one.
$icon = Join-Path $root 'assets\icon.ico'
if (Test-Path $icon) {
    $jpArgs += @('--icon', $icon)
    Write-Ok "icon: $icon"
}

& $jpackage @jpArgs
if ($LASTEXITCODE -ne 0) { Fail "jpackage reported errors (exit $LASTEXITCODE)." }

$appImage = Join-Path $staging $AppName
if (-not (Test-Path $appImage)) { Fail "Expected the packaged application at $appImage" }
$exe = Join-Path $appImage "$AppName.exe"
if (-not (Test-Path $exe)) { Fail "The packaged application has no $AppName.exe." }
Move-Item -LiteralPath $appImage -Destination $appOut
$appImage = $appOut
$exe = Join-Path $appImage "$AppName.exe"

# The temporary folder java.io.tmpdir points at (see the java-options above).
New-Item -ItemType Directory -Force -Path (Join-Path $appImage 'temp') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $appImage 'runtime\temp') | Out-Null

# A plain-text note beside the executable: the folder is meant to be handed over.
$guide = @"
$AppName $Version
$('=' * ($AppName.Length + $Version.Length + 1))

To start the program, double-click $AppName.exe in this folder.

Your records are NOT stored in this folder. They live in:
    %APPDATA%\$AppName
which is where the program reads and writes everything, including photos.

To move to another computer, copy this whole folder AND use
"Data & backup" inside the program to create a backup file, then restore
that backup on the new computer.

Everything stays on the computer you run it on: there is no account,
no synchronisation and no internet connection is used.
"@
Set-Content -Path (Join-Path $appImage 'READ ME FIRST.txt') -Value $guide -Encoding UTF8

$size = (Get-ChildItem $appImage -Recurse -File | Measure-Object -Property Length -Sum).Sum
Write-Host ""
Write-Host "APP READY" -ForegroundColor Green
Write-Ok "folder : $appImage"
Write-Ok "size   : $([math]::Round($size / 1MB, 1)) MB"
Write-Ok "start  : $exe"

# --- 4. Optional zip ----------------------------------------------------------
if ($Zip) {
    Write-Step 'Writing a zip archive'
    $zipPath = Join-Path $releaseDir "$AppName-$Version-windows.zip"
    if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
    Compress-Archive -Path $appImage -DestinationPath $zipPath -CompressionLevel Optimal
    Write-Ok "archive: $zipPath"
}

# --- 5. Optional installer ----------------------------------------------------
$wantInstaller = $Installer
if (-not $Installer -and -not $NoInstaller) {
    $answer = Read-Host 'Also build a Windows installer (.exe) that adds Start Menu entries? [y/N]'
    $wantInstaller = $answer -match '^(y|yes)$'
}

if ($wantInstaller) {
    Write-Step 'Building the Windows installer'
    $installerArgs = @(
        '--type', 'msi',
        '--name', $AppName,
        '--app-version', $Version,
        '--input', $inputDir,
        '--main-jar', "$AppName.jar",
        '--main-class', 'app.kinderjournal.App',
        '--module-path', $modulePath,
        '--add-modules', 'javafx.controls,javafx.graphics,javafx.base,java.sql,java.desktop',
        '--dest', $releaseDir,
        '--vendor', 'KinderJournal',
        '--description', 'A local journal for kindergarten teachers.',
        '--win-menu',
        '--win-shortcut',
        '--win-dir-chooser',
        '--win-menu-group', $AppName,
        '--win-console'
    )
    if (Test-Path $icon) { $installerArgs += @('--icon', $icon) }

    & $jpackage @installerArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Host "    The installer could not be built (exit $LASTEXITCODE)." -ForegroundColor Yellow
        Write-Host "    The application folder above is still complete and can be used as it is." -ForegroundColor Yellow
    } else {
        Write-Host ""
        Write-Host "INSTALLER READY" -ForegroundColor Green
        Get-ChildItem $releaseDir -Filter '*.msi' |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1 |
            ForEach-Object { Write-Ok "installer: $($_.FullName)" }
    }
}

exit 0
