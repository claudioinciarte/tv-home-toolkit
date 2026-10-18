[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AndroidHome = $env:ANDROID_HOME,
    [string]$Output = (Join-Path $PSScriptRoot '../artifacts/tvhome-debug.apk')
)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome 'bin/java.exe'))) {
    throw 'Indica -JavaHome con un JDK compatible; se usó el JBR de Android Studio.'
}
if (-not $AndroidHome -or -not (Test-Path $AndroidHome)) { throw 'Indica -AndroidHome con el SDK.' }
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $AndroidHome
$project = Join-Path $PSScriptRoot '../launcher'
Push-Location $project
try {
    & ./gradlew.bat assembleDebug -q --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Falló Gradle; no se conectó ningún dispositivo.' }
} finally { Pop-Location }
$outputPath = [IO.Path]::GetFullPath($Output)
New-Item -ItemType Directory -Force -Path (Split-Path $outputPath) | Out-Null
Copy-Item -LiteralPath (Join-Path $project 'app/build/outputs/apk/debug/app-debug.apk') -Destination $outputPath
Get-FileHash -LiteralPath $outputPath -Algorithm SHA256
