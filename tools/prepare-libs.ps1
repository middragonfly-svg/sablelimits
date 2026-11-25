# Populates ../libs with the jars needed to compile SableLimits.
# The jars are not committed (see ../.gitignore).
#
#   powershell -ExecutionPolicy Bypass -File tools\prepare-libs.ps1 -ServerDir C:\path\to\server

param(
    [string]$ServerDir = (Join-Path $PSScriptRoot '..\..\server')
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$libs = Join-Path $root 'libs'
New-Item -ItemType Directory -Force $libs | Out-Null

function Find-One([string]$Path, [string]$Filter) {
    $items = @(Get-ChildItem -Path $Path -Filter $Filter -File -ErrorAction SilentlyContinue)
    if ($items.Count -eq 0) {
        throw "File not found: $Filter in $Path"
    }
    return $items[0].FullName
}

function Expand-Jar([string]$JarPath) {
    $temp = Join-Path ([System.IO.Path]::GetTempPath()) ('sablelimits_' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force $temp | Out-Null
    $zip = Join-Path $temp 'archive.zip'
    Copy-Item $JarPath $zip -Force
    Expand-Archive -Path $zip -DestinationPath (Join-Path $temp 'x') -Force
    return (Join-Path $temp 'x')
}

$mods = Join-Path $ServerDir 'mods'

$sable = Find-One $mods 'sable-neoforge-*.jar'
$create = Find-One $mods 'create-1.21.1-*.jar'
$aeronautics = Find-One $mods 'create-aeronautics-bundled-*.jar'

Copy-Item $sable $libs -Force
Copy-Item $create $libs -Force

# Sable nested jars: companion (BoundingBox3ic) and Veil (used in Sable signatures).
$sableDir = Expand-Jar $sable
$sableJarjar = Join-Path $sableDir 'META-INF\jarjar'
Get-ChildItem $sableJarjar -Filter 'sable-companion-*.jar' | Copy-Item -Destination $libs -Force
Get-ChildItem $sableJarjar -Filter 'veil-*.jar' | Copy-Item -Destination $libs -Force

# The Aeronautics bundle contains Simulated; the Sable assembly mixin targets it.
$aeroDir = Expand-Jar $aeronautics
$aeroJarjar = Join-Path $aeroDir 'META-INF\jarjar'
$simulated = Get-ChildItem $aeroJarjar -Filter 'dev.simulated_team.simulated*.jar' | Select-Object -First 1
if (-not $simulated) {
    throw 'Simulated jar not found inside the Aeronautics bundle'
}
Copy-Item $simulated.FullName $libs -Force

Write-Host 'libs:'
Get-ChildItem $libs -Filter '*.jar' | ForEach-Object { Write-Host ('  ' + $_.Name) }
