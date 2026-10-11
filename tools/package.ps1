# KinderJournal packaging script
#
# Produces a self-contained Windows application folder (and optionally an
# installer) that a teacher can run without installing Java.
#
#   tools\package.ps1                      app folder, then ask about an installer
#   tools\package.ps1 -Installer           build the installer without asking
#   tools\package.ps1 -NoInstaller         app folder only
#   tools\package.ps1 -Zip                 also write a .zip of the app folder
#   tools\package.ps1 -DataDir D           make the shortcut keep its records in D

[CmdletBinding()]
param(
    [switch]$Installer,
    [switch]$NoInstaller,
    [switch]$Zip,
    [string]$AppName = 'KinderJournal',
    [string]$Version = '1.0.0',
    [string]$DataDir
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
    '--description', 'A local journal for kindergarten teachers: attendance, observations, interviews, planning and printable reports.'
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

# --- A shortcut to click, so the teacher never has to find the .exe ----------
# The program is a window with no console, so the folder holds one thing that
# looks like an application: this shortcut. It is placed beside the executable
# rather than on the desktop, because a desktop shortcut would break the moment
# the folder was moved to another computer.
Write-Step 'Creating a shortcut to click'
$shortcutPath = Join-Path $appImage "$AppName.lnk"
try {
    $shell = New-Object -ComObject WScript.Shell
    $link = $shell.CreateShortcut($shortcutPath)
    $link.TargetPath = $exe
    $link.WorkingDirectory = $appImage
    $link.Description = 'KinderJournal - kindergarten journal'
    # Records normally live in %APPDATA%; -DataDir pins them somewhere else,
    # which is what a school keeping the journal on a shared drive would want.
    if ($DataDir) {
        $link.Arguments = "--data-dir `"$([System.IO.Path]::GetFullPath($DataDir))`""
        Write-Ok "records folder: $($link.Arguments)"
    }
    $link.IconLocation = if (Test-Path $icon) { "$icon,0" } else { "$exe,0" }
    $link.Save()
    [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($shell)
    Write-Ok "shortcut: $AppName.lnk"
} catch {
    Write-Host "    could not create the shortcut: $($_.Exception.Message)" -ForegroundColor Yellow
}

# A one-line launcher, for anyone who prefers a script or needs to pass options.
$cmd = @"
@echo off
rem Starts KinderJournal without a console window.
start "" "%~dp0$AppName.exe" %*
"@
Set-Content -Path (Join-Path $appImage 'Start KinderJournal.cmd') -Value $cmd -Encoding ASCII

# A real .lnk beside the executable cannot be moved with the folder - a
# shortcut's target is an absolute path - so a portable VBScript launcher is
# also written. Windows' CreateShortcut method is available from either.
$portable = @"
' Creates a shortcut to KinderJournal on the Desktop.
' Useful when the folder has been copied to another computer, which would have
' left KinderJournal.lnk pointing at the old location.
Set shell = CreateObject("WScript.Shell")
Set link = shell.CreateShortcut(shell.SpecialFolders("Desktop") & "\$AppName.lnk")
link.TargetPath = CreateObject("Scripting.FileSystemObject").GetParentFolderName(WScript.ScriptFullName) & "\$AppName.exe"
link.WorkingDirectory = CreateObject("Scripting.FileSystemObject").GetParentFolderName(WScript.ScriptFullName)
link.Description = "$AppName - kindergarten journal"
link.IconLocation = link.TargetPath & ",0"
link.Save
MsgBox "A shortcut to $AppName has been placed on your Desktop.", 64, "$AppName"
"@
Set-Content -Path (Join-Path $appImage 'Put a shortcut on my Desktop.vbs') -Value $portable -Encoding ASCII

# A plain-text note beside the executable: the folder is meant to be handed over.
$guide = @"
$AppName $Version
$('=' * ($AppName.Length + $Version.Length + 1))

TO START THE PROGRAM
    Double-click  $AppName.lnk  (the one with the journal icon).

    Do not worry about $AppName.exe - it is the same program, but it is the
    technical file. The shortcut is there so you do not have to go looking.

WHERE YOUR RECORDS ARE KEPT
    Not in this folder. They live in:
        %APPDATA%\$AppName
    and that is where the program reads and writes everything, including photos.

MOVING TO ANOTHER COMPUTER
    1. Copy this whole folder across.
    2. Inside the program, use "Data & backup" to create a backup file.
    3. On the new computer, open the program and restore that backup.

PRIVACY
    Everything stays on the computer you run it on. There is no account, no
    synchronisation, and no internet connection is used at any point.

SOMETHING WRONG?
    The program shows a message box if it cannot start. Details are also written
    to  %APPDATA%\$AppName\logs\kinderjournal.log
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
        '--win-menu-group', $AppName
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
