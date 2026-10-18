---
name: google-tv-home
description: Instalar o restaurar la Home de este repositorio en Chromecast con Google TV mediante ADB, con respaldo reversible y conservando Play Store y las apps elegidas.
---

# Google TV Home

Usar el checkout tv-home-toolkit, localizado por scripts/TvHome.ps1 y launcher/. Si esta skill se copió fuera, localizar primero el checkout. Leer docs/cases/google-tv.md y docs/launcher.md.

## Flujo

1. Usar el host:puerto actual indicado por el usuario. Distinguir puerto de emparejamiento del de conexión. Connect-Adb.ps1 pide el código interactivamente: no guardarlo en archivos, Git o informes.
2. Ejecutar Inspect y, si se pidió privacidad, Telemetry. Identificar SDK, modelo y paquetes antes de elegir el procedimiento.
3. Compilar con Build-Launcher.ps1 o reutilizar el APK autorizado y su hash para varias instalaciones compatibles. No reconstruir por cada comando ADB.
4. Explicar que se deshabilitan launcherx y setupwraith para usuario 0. La autorización previa cuenta si cubre esta instalación; no pedirla de nuevo ni ampliarla a otras apps.
5. Ejecutar Install -Platform GoogleTV -ReplaceGoogleHome -Device <objetivo> -Apk <archivo>. El estado previo se guarda antes de modificar Home. Si falla, diagnosticar el error; no repetir mutaciones a ciegas.
6. Conservar Play Store, Cast, cuentas y actualizaciones. Configurar el orden elegido en config.xml; no copiar selecciones de otro usuario. No hace falta Inputs para un dispositivo de streaming.
7. Si se pide verificar: abrir una app, pulsar Inicio y comprobar mResumedActivity/topResumedActivity. Las apps nuevas de TV deben aparecer salvo hidden_apps.
8. Usar Restore con el mismo StateRoot para recuperar estados y Home anteriores. Sin respaldo, inspeccionar antes de adivinar estados.

## Evitar reintentos

- set-home-activity puede no bastar por prioridad de componentes del sistema.
- Restaurar Setup/Recovery antes de necesitar recuperación/configuración inicial.
- Un conflicto de firma no autoriza desinstalar ni borrar datos.
- Si cambia el puerto, comprobar identidad y trasladar el respaldo del mismo equipo; no crear uno nuevo encima de una instalación ya modificada.
- Un equipo que ya usa com.gathin.tvhome sin respaldo requiere recuperar el respaldo original o reconstruir su estado de forma explícita.
- La limpieza de telemetría va aparte: Install no aplica debloat.
