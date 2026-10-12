# Reads an .msi package's own database, so an installer can be checked before it is
# handed to anybody: what it calls itself, where it installs, and what it creates.
#
# The queries live in tools\msi-queries.sql, because several MSI table names are SQL
# reserved words and they are painful to escape from PowerShell.
#
#   tools\read-msi.ps1 -Path <installer.msi>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Path
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$full = (Resolve-Path -LiteralPath $Path).Path

$installer = New-Object -ComObject WindowsInstaller.Installer
$database = $installer.GetType().InvokeMember(
    'OpenDatabase', 'InvokeMethod', $null, $installer, @([string]$full, [int]0))

function Invoke-MsiQuery {
    param([string]$Sql)
    $view = $database.GetType().InvokeMember(
        'OpenView', 'InvokeMethod', $null, $database, @([string]$Sql))
    $view.GetType().InvokeMember('Execute', 'InvokeMethod', $null, $view, $null)
    $rows = @()
    while ($true) {
        $record = $view.GetType().InvokeMember('Fetch', 'InvokeMethod', $null, $view, $null)
        if ($null -eq $record) { break }
        $count = $record.GetType().InvokeMember('FieldCount', 'GetProperty', $null, $record, $null)
        $values = @()
        for ($i = 1; $i -le $count; $i++) {
            $values += [string]$record.GetType().InvokeMember(
                'StringData', 'GetProperty', $null, $record, @([int]$i))
        }
        $rows += ($values -join ' | ')
    }
    return $rows
}

$queries = Get-Content (Join-Path $PSScriptRoot 'msi-queries.sql') |
    Where-Object { $_ -and -not $_.StartsWith('--') }

function Answer([int]$Index) {
    $rows = Invoke-MsiQuery $queries[$Index]
    if ($rows.Count -eq 0) { return '(none)' }
    return ($rows -join '; ')
}

Write-Host "KinderJournal installer check"
Write-Host "  file         : $full"
Write-Host "  size         : $([math]::Round((Get-Item $full).Length / 1MB, 1)) MB"
Write-Host ""
Write-Host "Identity"
Write-Host "  product      : $(Answer 0)"
Write-Host "  version      : $(Answer 1)"
Write-Host "  manufacturer : $(Answer 2)"
Write-Host "  upgrade code : $(Answer 3)"
$scope = Answer 4
Write-Host "  installs     : $(if ($scope -eq '1') { 'per machine - asks for an administrator' } elseif ($scope -eq '2') { 'per user - no administrator needed' } else { $scope })"

$files = Invoke-MsiQuery 'SELECT `FileName` FROM `File`'
Write-Host ""
Write-Host "Content"
Write-Host "  files        : $($files.Count)"

Write-Host ""
Write-Host "Default install folder"
Answer 6
Write-Host ""
Write-Host "Start Menu group"
Answer 7

Write-Host ""
Write-Host "Shortcuts"
Answer 5

