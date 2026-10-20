#Requires -Version 7.2
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('Inspect','Telemetry','Install','Restore','EnableFastHome','RestoreFastHome','DisablePackages','RestorePackages')]
    [string]$Action,
    [Parameter(Mandatory)][string]$Device,
    [ValidateSet('GoogleTV','FireTV')][string]$Platform,
    [string]$Apk,
    [string]$Adb = 'adb',
    [string]$StateRoot = (Join-Path $PSScriptRoot '../.local'),
    [switch]$ReplaceGoogleHome,
    [string[]]$Packages = @()
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = [Text.UTF8Encoding]::new($false)
if ($Device -notmatch '^[A-Za-z0-9.-]+:\d{1,5}$') { throw 'Device debe ser host:puerto; sin objetivo implícito.' }
function Invoke-Adb {
    param([Parameter(Mandatory)][string[]]$Arguments)
    $lines = @(& $Adb -s $Device @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "ADB falló: $($Arguments[0]); $($lines -join ' ')" }
    $text = ($lines -join [Environment]::NewLine).Trim()
    if ($text -match '(?m)^(Error[: ]|Failure \[|Security exception:|java.lang.SecurityException)') { throw $text }
    return $text
}
function Shell { param([string[]]$Command) Invoke-Adb -Arguments (@('shell') + $Command) }
function Write-Json { param($Path,$Value) $Value | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $Path -Encoding utf8 }
function Get-PackageState {
    param([string]$Package)
    if ($Package -notmatch '^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$') { throw "Nombre inválido: $Package" }
    $dump = Shell @('dumpsys','package',$Package)
    if ($dump -notmatch '(?m)User 0:.*\benabled=(\d+)') { throw "No se pudo leer estado de $Package para usuario 0." }
    return [int]$Matches[1]
}
function Set-PackageState {
    param([string]$Package,[int]$State)
    $verbs = @{0='default-state';1='enable';2='disable';3='disable-user';4='disable-until-used'}
    if (-not $verbs.ContainsKey($State)) { throw "Estado no soportado: $State" }
    Shell @('pm',$verbs[$State],'--user','0',$Package) | Out-Host
}
function Get-Services {
    $raw = Shell @('settings','get','secure','enabled_accessibility_services')
    return @($raw -split ':' | Where-Object { $_ -and $_ -ne 'null' })
}
function Remove-HomeService {
    $other = @(Get-Services | Where-Object { $_ -notin @('com.gathin.tvhome/.HomeRedirectService','com.gathin.tvhome/com.gathin.tvhome.HomeRedirectService') })
    if ($other.Count) {
        Shell @('settings','put','secure','enabled_accessibility_services',($other -join ':')) | Out-Null
    } else {
        Shell @('settings','delete','secure','enabled_accessibility_services') | Out-Null
        Shell @('settings','put','secure','accessibility_enabled','0') | Out-Null
    }
}
function Get-MinFuturity {
    param([string]$Raw)
    $entry = @($Raw -split ',' | Where-Object { $_ -match '^min_futurity=' })
    if ($entry.Count -gt 1) { throw 'min_futurity duplicado; revisar antes de modificar.' }
    if ($entry.Count) { return ($entry[0] -split '=',2)[1] }
    return $null
}
function Set-MinFuturity {
    param([string]$Current,[AllowNull()][string]$Value)
    $entries = @($Current -split ',' | Where-Object { $_ -and $_ -ne 'null' -and $_ -notmatch '^min_futurity=' })
    if ($null -ne $Value -and $Value -ne '') { $entries += "min_futurity=$Value" }
    if ($entries.Count) {
        Shell @('settings','put','global','alarm_manager_constants',($entries -join ',')) | Out-Null
    } else { Shell @('settings','delete','global','alarm_manager_constants') | Out-Null }
}
function Restore-FastHome {
    $path = Join-Path $stateDir 'fast-home.json'
    if (-not (Test-Path $path)) { return }
    $backup = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
    if ($backup.restored) { return }
    $current = Shell @('settings','get','global','alarm_manager_constants')
    if ((Get-MinFuturity $current) -ne '250') { throw 'min_futurity cambió después. Revisar el respaldo sin sobrescribir cambios ajenos.' }
    Set-MinFuturity -Current $current -Value $backup.previousMinFuturity
    $backup.restored = $true
    Write-Json $path $backup
}

# Conexión previa mediante Connect-Adb.ps1; nunca se elige el primer dispositivo.
if ((Invoke-Adb @('get-state')) -ne 'device') { throw 'Dispositivo desconectado o sin autorización.' }
$model = Shell @('getprop','ro.product.model')
$maker = Shell @('getprop','ro.product.manufacturer')
$sdk = [int](Shell @('getprop','ro.build.version.sdk'))
$fingerprint = Shell @('getprop','ro.build.fingerprint')
# Identidad estable solo como hash local; no se imprime ni incluye en informes.
$stableId = Shell @('getprop','ro.serialno')
if (-not $stableId) { $stableId = Shell @('settings','get','secure','android_id') }
if (-not $stableId -or $stableId -eq 'null') { throw 'No se obtuvo identidad estable para asociar el respaldo.' }
$deviceHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($stableId)))
$all = @((Shell @('pm','list','packages','--user','0')) -split '\r?\n' | ForEach-Object { $_ -replace '^package:','' } | Where-Object { $_ })
$disabled = @((Shell @('pm','list','packages','-d','--user','0')) -split '\r?\n' | ForEach-Object { $_ -replace '^package:','' } | Where-Object { $_ })
$homeResolution = Shell @('cmd','package','resolve-activity','--brief','--user','0','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')
$component = @($homeResolution -split '\r?\n' | Where-Object { $_ -match '^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+' }) | Select-Object -Last 1
$actualPlatform = if ($maker -eq 'Amazon' -and $all -contains 'com.amazon.tv.launcher') { 'FireTV' } elseif ($all -contains 'com.google.android.apps.tv.launcherx') { 'GoogleTV' } else { 'Unsupported' }
if ($Platform -and $Platform -ne $actualPlatform) { throw "Se pidió $Platform pero se detectó $actualPlatform ($model)." }
$stateDir = Join-Path $StateRoot ($Device -replace ':','_')
New-Item -ItemType Directory -Force -Path $stateDir | Out-Null
$identityPath = Join-Path $stateDir 'identity.json'
$identity = @{model=$model;manufacturer=$maker;fingerprint=$fingerprint;deviceHash=$deviceHash}
if (Test-Path $identityPath) {
    $saved = Get-Content -Raw -LiteralPath $identityPath | ConvertFrom-Json
    if ($saved.deviceHash -ne $deviceHash -or $saved.fingerprint -ne $fingerprint) {
        throw 'El equipo/firmware difiere del respaldo. Usa un StateRoot nuevo tras revisar la identidad.'
    }
} else { Write-Json $identityPath $identity }

switch ($Action) {
    'Inspect' {
        $result = @{model=$model;manufacturer=$maker;sdk=$sdk;platform=$actualPlatform;home=$component;disabledPackages=$disabled;stores=@($all | Where-Object { $_ -in @('com.android.vending','com.amazon.venezia') })}
        Write-Json (Join-Path $stateDir 'inspect.json') $result
        $result | ConvertTo-Json -Depth 5
    }
    'Telemetry' {
        $settings = [ordered]@{}
        foreach ($item in @(@('global','usage_stats_enabled'),@('secure','send_action_app_error'),@('global','upload_apk_enable'),@('secure','limit_ad_tracking'),@('secure','usage_reporting_enabled'))) {
            $settings["$($item[0])/$($item[1])"] = Shell @('settings','get',$item[0],$item[1])
        }
        $result = @{model=$model;sdk=$sdk;platform=$actualPlatform;packages=$all;disabledPackages=$disabled;settings=$settings;accessibilityServices=@(Get-Services);note='null significa ajuste ausente/no disponible; inventario, no evidencia de tráfico.'}
        Write-Json (Join-Path $stateDir 'telemetry.json') $result
        @("# Diagnóstico local", "", "Equipo: $model; API $sdk; $actualPlatform.", "", "Paquetes presentes: $($all.Count); deshabilitados: $($disabled.Count).", "", "Datos en telemetry.json. null no demuestra telemetría desactivada.", "No se capturó tráfico ni se inspeccionaron cuentas. Ver docs/telemetry.md.") | Set-Content -LiteralPath (Join-Path $stateDir 'telemetry.md') -Encoding utf8
        Write-Host "Informe local: $stateDir/telemetry.md"
    }
    'Install' {
        if (-not $Platform) { throw 'Install requiere -Platform explícito.' }
        if (-not $Apk -or -not (Test-Path -LiteralPath $Apk -PathType Leaf)) { throw 'Indica -Apk con un APK compilado.' }
        if ($Platform -eq 'GoogleTV' -and (-not $ReplaceGoogleHome -or $all -notcontains 'com.google.android.tungsten.setupwraith')) { throw 'Este caso requiere launcherx, setupwraith y -ReplaceGoogleHome autorizado.' }
        $baselinePath = Join-Path $stateDir 'home-before.json'
        if (-not (Test-Path $baselinePath)) {
            if (-not $component -or $component -match '^com.gathin.tvhome/') { throw 'No hay una Home original identificada; revisar antes de sustituirla.' }
            $packageStates = @{}
            if ($Platform -eq 'GoogleTV') {
                foreach ($pkg in @('com.google.android.apps.tv.launcherx','com.google.android.tungsten.setupwraith')) { $packageStates[$pkg] = Get-PackageState $pkg }
            }
            Write-Json $baselinePath @{platform=$Platform;home=$component;packages=$packageStates;services=@(Get-Services);restored=$false}
        } else {
            $prior = Get-Content -Raw -LiteralPath $baselinePath | ConvertFrom-Json
            if ($prior.restored) { throw 'El ciclo anterior ya se restauró. Archiva el StateRoot y usa uno nuevo.' }
        }
        # Fallar aquí no deshabilita Home. Nunca desinstalar por conflicto de firma.
        Invoke-Adb @('install','-r',([IO.Path]::GetFullPath($Apk))) | Out-Host
        $launch = Shell @('am','start','-W','-n','com.gathin.tvhome/.MainActivity')
        $launch | Out-Host
        if ($launch -notmatch '(?m)^Status: ok\s*$') {
            throw 'El APK no confirmó una apertura correcta. La Home original permanece sin deshabilitar.'
        }
        if ($Platform -eq 'GoogleTV') {
            Shell @('cmd','package','set-home-activity','--user','0','com.gathin.tvhome/.MainActivity') | Out-Host
            foreach ($pkg in @('com.google.android.apps.tv.launcherx','com.google.android.tungsten.setupwraith')) { Set-PackageState $pkg 3 }
            $resolved = Shell @('cmd','package','resolve-activity','--brief','--user','0','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')
            if ($resolved -notmatch 'com.gathin.tvhome/') { throw 'La Home no resuelve al launcher. Ejecuta Restore antes de continuar.' }
        } else {
            Shell @('pm','grant','com.gathin.tvhome','android.permission.WRITE_SECURE_SETTINGS') | Out-Null
            Write-Host 'En Home: Ajustes > Inicio del mando > activar Abrir Home con el botón Inicio.'
        }
        Write-Host 'Instalación completada; tiendas y apps del usuario conservadas.'
    }
    'EnableFastHome' {
        if ($Platform -ne 'FireTV' -or $sdk -ne 28) { throw 'Optimización limitada al caso Fire TV Android 9/API 28.' }
        $path = Join-Path $stateDir 'fast-home.json'
        $current = Shell @('settings','get','global','alarm_manager_constants')
        if (Test-Path $path) {
            $backup = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
            if ($backup.restored) { throw 'Optimización anterior restaurada. Usa un StateRoot nuevo.' }
            if ((Get-MinFuturity $current) -eq '250') { Write-Host 'Ya aplicado.'; break }
            throw 'Existe respaldo activo pero el ajuste cambió. Revisar antes de modificar.'
        }
        Write-Json $path @{previousRaw=$current;previousMinFuturity=(Get-MinFuturity $current);restored=$false}
        Set-MinFuturity -Current $current -Value '250'
        if ((Get-MinFuturity (Shell @('settings','get','global','alarm_manager_constants'))) -ne '250') { throw 'El sistema no conservó el ajuste.' }
        Write-Host 'Mínimo GLOBAL: 250 ms. RestoreFastHome permite revertirlo.'
    }
    'RestoreFastHome' { Restore-FastHome }
    'Restore' {
        $path = Join-Path $stateDir 'home-before.json'
        if (-not (Test-Path $path)) { throw 'Falta home-before.json; no se adivina un estado de restauración.' }
        $baseline = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
        if ($baseline.restored) { Write-Host 'Este ciclo ya fue restaurado.'; break }
        if ($baseline.platform -ne $actualPlatform) { throw 'El respaldo es de otra plataforma.' }
        if ($baseline.platform -eq 'GoogleTV') {
            Shell @('pm','enable','--user','0','com.google.android.apps.tv.launcherx') | Out-Null
            Shell @('cmd','package','set-home-activity','--user','0',$baseline.home) | Out-Host
            foreach ($entry in $baseline.packages.PSObject.Properties) { Set-PackageState $entry.Name ([int]$entry.Value) }
        } else {
            Restore-FastHome
            Remove-HomeService
        }
        Shell @('am','force-stop','com.gathin.tvhome') | Out-Null
        Shell @('am','start','-n',$baseline.home) | Out-Host
        $baseline.restored = $true
        Write-Json $path $baseline
        Write-Host 'Home anterior restaurada. El APK propio permanece instalado.'
    }
    'DisablePackages' {
        if (-not $Packages.Count) { throw 'Se requiere -Packages con una lista revisada.' }
        $protected = '(^android$|com\.android\.vending|com\.amazon\.venezia|com\.gathin\.tvhome|launcher|setup|packageinstaller|permissioncontroller|systemui|settings|providers|google\.android\.gms|google\.android\.gsf|cast|drm|account|update|ota|amazon\.identity|amazon\.device\.messaging)'
        foreach ($pkg in $Packages) {
            if ($pkg -match $protected -or $all -notcontains $pkg) { throw "Paquete protegido o ausente: $pkg" }
        }
        $path = Join-Path $stateDir 'packages-before.json'
        $states = @{}
        if (Test-Path $path) {
            $prior = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
            foreach ($entry in $prior.PSObject.Properties) { $states[$entry.Name] = $entry.Value }
        }
        foreach ($pkg in $Packages) {
            if (-not $states.ContainsKey($pkg)) {
                $states[$pkg] = Get-PackageState $pkg
                Write-Json $path $states
            }
            Set-PackageState $pkg 3
        }
    }
    'RestorePackages' {
        $path = Join-Path $stateDir 'packages-before.json'
        if (-not (Test-Path $path)) { throw 'No hay respaldo de paquetes.' }
        $states = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
        foreach ($entry in $states.PSObject.Properties) { Set-PackageState $entry.Name ([int]$entry.Value) }
    }
}
