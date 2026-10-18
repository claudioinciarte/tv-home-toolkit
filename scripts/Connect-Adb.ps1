[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Device,
    [string]$PairEndpoint,
    [string]$Adb = 'adb'
)
$ErrorActionPreference = 'Stop'
if ($Device -notmatch '^[A-Za-z0-9.-]+:\d{1,5}$') { throw 'Device debe ser host:puerto.' }
if ($PairEndpoint) {
    if ($PairEndpoint -notmatch '^[A-Za-z0-9.-]+:\d{1,5}$') { throw 'PairEndpoint debe ser host:puerto.' }
    & $Adb pair $PairEndpoint
    if ($LASTEXITCODE -ne 0) { throw 'Falló el emparejamiento. Abre un código nuevo.' }
}
& $Adb connect $Device
if ($LASTEXITCODE -ne 0) { throw 'No se pudo conectar.' }
$state = & $Adb -s $Device get-state
if ($LASTEXITCODE -ne 0 -or "$state".Trim() -ne 'device') {
    throw 'ADB desconectado/sin autorización. Acepta la autorización en pantalla y repite.'
}
Write-Host "ADB listo para $Device"
