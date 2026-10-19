# TV Home Toolkit

Home personalizada para Google TV y Fire TV, con herramientas ADB de instalación, diagnóstico de telemetría y recuperación.

Derivado de [Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack), bajo MIT; Outfit mantiene su licencia OFL. La redirección integrada de Fire TV usa APIs de Android y no incluye código ni APK de Home on Fire.

## Origen y créditos

El proyecto original es **[tv-vibehack](https://github.com/Gathin23/tv-vibehack)**, creado por **[Gathin / Gathin23](https://github.com/Gathin23)**. Su launcher y diseño son la base de este repositorio; el crédito por ese trabajo corresponde a su autor.

Esta adaptación añade los procedimientos de Google TV y Fire TV, la redirección integrada de Inicio, las herramientas de recuperación y las skills. Conservamos el aviso de copyright y la licencia MIT originales.

Consulta [CREDITS.md](CREDITS.md) para el origen exacto del código y las licencias de terceros.

## Dos casos reales

| Equipo probado | Método | Resultado |
| --- | --- | --- |
| Chromecast con Google TV 4K, Android 14/API 34 | Home propia; desactivación reversible de Google TV Home y Setup/Recovery para usuario 0 | Inicio abre el launcher; tienda y apps conservadas |
| Fire TV Stick 4K Max 1.ª gen, AFTKA, Fire OS 7.7.1.3/API 28 | Accesibilidad propia y apertura programada | 501, 477 y 491 ms con min_futurity global de 250 ms |

Los tiempos empiezan al detectar Amazon Home, no en la pulsación física. Se usó ADB. Reinicio y mando físico no probados. Otros firmwares necesitan nueva comprobación.

## APK preparado

La [primera versión descargable](https://github.com/claudioinciarte/tv-home-toolkit/releases/tag/v0.1.0) incluye tvhome-debug.apk y SHA256SUMS. Con gh autenticado en este repositorio privado:

~~~powershell
gh release download v0.1.0 --repo claudioinciarte/tv-home-toolkit --pattern tvhome-debug.apk --pattern SHA256SUMS --dir ./artifacts
Get-FileHash ./artifacts/tvhome-debug.apk -Algorithm SHA256
~~~

Comparar el hash con SHA256SUMS antes de instalar. Es un APK debug: conserva la clave del equipo de compilación fuera de Git para actualizar instalaciones compatibles. Compilar en otro equipo puede producir una firma distinta.

## Inicio rápido — PowerShell 7.2+

Requisitos: Android Platform Tools (adb en PATH o -Adb), Android SDK y JDK compatible con AGP. Proyecto: AGP 9.4.0, Gradle 9.6.0, compileSdk 37, minSdk 26, targetSdk 29. Se usó el JBR de Android Studio. La primera compilación necesita red para dependencias.

~~~powershell
./scripts/Build-Launcher.ps1 -JavaHome $env:JAVA_HOME -AndroidHome $env:ANDROID_HOME

# El código se introduce interactivamente; los puertos de emparejar y conectar son distintos.
./scripts/Connect-Adb.ps1 -Device '192.0.2.20:37001' -PairEndpoint '192.0.2.20:38001'
./scripts/TvHome.ps1 -Action Inspect -Device '192.0.2.20:37001'
./scripts/TvHome.ps1 -Action Telemetry -Device '192.0.2.20:37001'

# Google TV: requiere autorización para reemplazar Home y desactivar Setup/Recovery.
./scripts/TvHome.ps1 -Action Install -Platform GoogleTV -Device '192.0.2.20:37001' -Apk ./artifacts/tvhome-debug.apk -ReplaceGoogleHome

# Fire TV: autorizar ADB en pantalla; activar luego el interruptor en Ajustes > Inicio del mando.
./scripts/Connect-Adb.ps1 -Device '192.0.2.30:5555'
./scripts/TvHome.ps1 -Action Install -Platform FireTV -Device '192.0.2.30:5555' -Apk ./artifacts/tvhome-debug.apk
~~~

La instalación de Fire TV no activa automáticamente el interruptor ni modifica el planificador. Para acelerar Inicio, explicar y autorizar primero el alcance GLOBAL sobre todas las apps:

~~~powershell
./scripts/TvHome.ps1 -Action EnableFastHome -Platform FireTV -Device '192.0.2.30:5555'
./scripts/TvHome.ps1 -Action RestoreFastHome -Platform FireTV -Device '192.0.2.30:5555'
~~~

## Recuperación y limpieza

~~~powershell
./scripts/TvHome.ps1 -Action Restore -Platform GoogleTV -Device '192.0.2.20:37001'
./scripts/TvHome.ps1 -Action Restore -Platform FireTV -Device '192.0.2.30:5555'

# Solo paquetes revisados y acordados; sin listas automáticas.
./scripts/TvHome.ps1 -Action DisablePackages -Device '192.0.2.20:37001' -Packages 'paquete.confirmado'
./scripts/TvHome.ps1 -Action RestorePackages -Device '192.0.2.20:37001'
~~~

Restore conserva el APK propio y las apps del usuario. Los respaldos quedan en .local/<objetivo>/, excluidos de Git. No reutilizar un respaldo en otro dispositivo, aunque comparta IP.

## Skills de Codex

- [google-tv-home](.agents/skills/google-tv-home/SKILL.md): instalar y recuperar Google TV.
- [fire-tv-home](.agents/skills/fire-tv-home/SKILL.md): redirección integrada y apertura rápida.
- [tv-telemetry-review](.agents/skills/tv-telemetry-review/SKILL.md): diagnóstico y limpieza reversible.

Se descubren al abrir este repositorio en Codex. También se pueden copiar las carpetas a ~/.codex/skills, conservando el checkout al que remiten.

## Documentación

- [Plex Home: configuración y seguridad](docs/plex-home.md)
- [Plex Home: diseño y validación visual](docs/plex-home-ux.md)
- [Google TV](docs/cases/google-tv.md)
- [Fire TV y velocidad](docs/cases/fire-tv.md)
- [Telemetría](docs/telemetry.md)
- [Apps, tiendas y firma](docs/launcher.md)
- [Validación](docs/validation.md)

No requiere root. El launcher no pide INTERNET. Un inventario de paquetes no demuestra que todo el dispositivo haya dejado de enviar telemetría.
