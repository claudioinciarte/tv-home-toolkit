# Arquitectura

## Vista general

El repositorio contiene dos partes que cooperan por ADB, pero se pueden revisar y ejecutar por separado:

1. Una aplicación Android nativa en Java que funciona como launcher y ofrece ajustes, salvapantallas y redirección opcional del botón Inicio en Fire TV.
2. Scripts PowerShell que compilan el launcher y automatizan conexión ADB, inspección, instalación, diagnóstico y recuperación.

No hay backend propio. Plex es una integración opcional con los servicios en la nube de Plex y los servidores seleccionados por el usuario.

## Aplicación Android

```text
launcher/
├── build.gradle                 Configuración Gradle del proyecto
└── app/
    ├── build.gradle             SDK, versión, Java y dependencias
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/gathin/tvhome/
        │   └── res/              Layouts, recursos visuales y configuración
        ├── debug/                 Diagnóstico de Plex solo para builds debug
        └── test/                  Pruebas unitarias JVM
```

### Componentes

- `MainActivity`: pantalla HOME, descubrimiento de apps lanzables, cuadrícula/dock, reloj, red, foco DPAD y estantería Plex.
- `AppAdapter` y `PlexShelfAdapter`: presentación y navegación de tarjetas de apps y contenido Plex.
- `PlexSettingsActivity`: vinculación por PIN, selección de servidores/bibliotecas, conexión local opcional y controles de catálogo.
- `PlexManager`: acceso a cuenta, servidores, bibliotecas, catálogo y carátulas Plex; caché y preferencias privadas; ejecución de red fuera del hilo de interfaz.
- `PlexServerCredentials`, `PlexServerCredentialsTest` y clases de apoyo Plex (`PlexCatalog`, `PlexRating`, `PlexPosterSources`, `PlexLocalEndpoint`): validación y transformación de datos, credenciales de servidor, rutas TLS y reglas del catálogo.
- `HomeSettings`, `HomeRedirect` y `HomeRedirectService`: preferencias y redirección opcional de Inicio en Fire TV mediante Accessibility Service.
- `HomeBootReceiver`: reacciona al arranque para restaurar el comportamiento del servicio cuando corresponde.
- `ClockDream`: salvapantallas del sistema.
- `GlassView` y `DockDecoration`: detalles visuales de la interfaz.

### Flujo de Plex

1. El usuario vincula la app mediante el PIN de Plex; no comparte la sesión privada de la app oficial.
2. El launcher descubre servidores y permite elegir bibliotecas.
3. `PlexManager` consulta los elementos recientes seleccionados, normaliza metadatos y conserva una caché limitada en preferencias privadas.
4. Adaptadores muestran tarjetas y solicitan carátulas/fondos al servidor seleccionado.
5. Si se configura una ruta local, se prueba con HTTPS e identidad del servidor antes de guardarla; las rutas anunciadas por Plex siguen como alternativa.

Los tokens se protegen con Android Keystore. El detalle de credenciales, TLS, almacenamiento y límites está en [docs/plex-home.md](docs/plex-home.md).

## Herramientas del repositorio

- `scripts/Build-Launcher.ps1`: invoca la compilación Gradle.
- `scripts/Connect-Adb.ps1`: empareja/conecta ADB; el código de emparejamiento se introduce de forma interactiva.
- `scripts/TvHome.ps1`: punto central para inspección, diagnóstico, instalación y restauración. Requiere identificar el objetivo del dispositivo.
- `docs/cases/`: procedimientos específicos de Google TV y Fire TV.
- `.agents/skills/`: instrucciones operativas para esos casos y revisión de telemetría.
- `.local/`: respaldos e información local por objetivo; contiene datos privados y no debe publicarse.

## Plataforma y dependencias

- Android `minSdk 26`, `compileSdk 37`, `targetSdk 29`, Java 17.
- Gradle 9.6.0 y Android Gradle Plugin 9.4.0, según README.
- AndroidX RecyclerView y Dynamic Animation; JUnit 4 para pruebas JVM.
- No requiere root. Fire TV requiere la concesión ADB indicada para `WRITE_SECURE_SETTINGS` y activación manual del servicio de accesibilidad.
- El launcher declara `INTERNET` para Plex opcional; el alcance de red y sus límites se documentan en `docs/plex-home.md` y `docs/telemetry.md`.

## Seguridad y recuperación

- Los scripts deben respaldar el estado anterior y restaurar solo el mismo objetivo.
- No deshabilitar Amazon Home ni eliminar datos automáticamente ante conflictos de firma.
- No aplicar ajustes globales sin explicar su alcance; `EnableFastHome` afecta un ajuste global.
- No inferir tráfico de red a partir del inventario local de paquetes o permisos.
- No conectar ni modificar dispositivos como parte de tareas de documentación.
