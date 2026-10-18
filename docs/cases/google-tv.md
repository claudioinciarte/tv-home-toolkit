# Chromecast con Google TV 4K

Caso probado: Android 14/API 34. Home original: com.google.android.apps.tv.launcherx/.home.HomeActivity.

El APK abría directamente, pero seleccionar Home no bastaba porque las actividades del sistema seguían teniendo prioridad. Se deshabilitaron para usuario 0, sin desinstalar, com.google.android.apps.tv.launcherx y com.google.android.tungsten.setupwraith, con autorización expresa para esa instalación.

Setup/Recovery queda indisponible para ese usuario hasta restaurarlo. Restaurar antes de necesitar configuración inicial o recuperación.

## Procedimiento

1. Emparejar con Connect-Adb.ps1 si corresponde y usar el puerto actual de conexión.
2. Inspect y Telemetry. Confirmar modelo, SDK, paquetes y HOME.
3. Compilar una vez o proporcionar un APK con firma compatible.
4. Install -Platform GoogleTV -ReplaceGoogleHome. Guarda estados antes de cambiar Home, instala y abre el APK, después sustituye el enrutamiento.
5. Si se solicita verificar: abrir una app, pulsar Inicio y comprobar la actividad reanudada. Comprobar Play Store y descubrimiento de una app nueva si se pide esa prueba.
6. Restore usa los estados anteriores de ambos paquetes y la Home guardada. No elimina apps.

El script exige launcherx y setupwraith. No extrapolar a cualquier Android TV. Si ya había componentes deshabilitados manualmente, revisarlos; el script solo cambia estados de paquetes.

## Apps y privacidad

Se conservaron las ocho apps y el orden elegidos por el usuario, manteniendo Play Store. Esa selección no se publica como perfil universal. Las apps nuevas de TV aparecen salvo que estén ocultas en hidden_apps.

Antes de intervenir ya estaban deshabilitados paquetes de recomendaciones, feedback, publicidad, personalización, cálculo federado y diagnósticos. No atribuimos esos cambios al instalador. Play Services, framework de Google, Cast y actualizaciones permanecieron disponibles.

Ver docs/telemetry.md: revisión de paquetes y ajustes, sin evidencia de tráfico.
