# Contexto y registro de cambios

Este archivo conserva el contexto de trabajo para que otra persona o agente pueda continuar sin perder el hilo. Añade una entrada por cada conjunto de cambios, incluidos código, documentación, configuración y pruebas. Actualiza «Último cambio» al completar cada conjunto. No atribuyas cambios previos a quien no los hizo.

## Último cambio

- **Fecha:** 2026-10-20 (Europe/Madrid)
- **Resumen:** se publicó el trabajo acumulado de Plex Home: conexión y credenciales Plex, carrusel visual, conexión local HTTPS, ajustes inline en el panel derecho y documentación.
- **Archivos:** `launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java`, `PlexManager.java`, `PlexSettingsActivity.java`, `PlexServerCredentials.java`, `launcher/app/src/main/res/layout/plex_carousel.xml`, cadenas ES/EN, `launcher/app/src/debug/`, `launcher/app/src/test/`, `docs/plex-home.md`, `docs/plex-home-ux.md`, `Design.md`, `Arquitecture.md`, `AGENTS.md`, `Context.md`.
- **Motivo:** consolidar los cambios probados en Fire TV y mantener documentados los flujos de Plex y las convenciones del proyecto.
- **Comprobaciones:** compilación debug satisfactoria; instalación en el Fire TV autorizado; pantallas inline de servidores, bibliotecas y conexión local revisadas a 1920 × 1080 con DPAD por ADB; `git diff --check` sin errores. No se ejecutó la suite JVM durante esta publicación.
- **Continuación:** PR dirigido a `main`; revisar CI de GitHub antes de fusionar.

- **Fecha:** 2026-10-20 (Europe/Madrid)
- **Resumen:** se añadieron los documentos de diseño y arquitectura, se creó este registro y se establecieron en `AGENTS.md` las reglas para registrar cada cambio y mantener actualizado el último cambio.
- **Archivos:** `Design.md`, `Arquitecture.md`, `Context.md`, `AGENTS.md`.
- **Motivo:** documentar el proyecto y mantener continuidad del trabajo entre agentes.
- **Comprobaciones:** lectura de `README.md`, `AGENTS.md`, documentación de launcher/Plex/validación, scripts PowerShell, manifiesto, configuración Gradle y componentes Android principales. Se detectaron cambios locales preexistentes; no fueron modificados. No se ejecutaron pruebas.
- **Continuación:** revisar la documentación al cambiar la arquitectura, interfaz, seguridad o flujos de instalación; registrar el siguiente conjunto de cambios en esta sección y añadir su entrada al historial.

## Estado heredado al iniciar este registro

Antes de crear esta documentación, Git ya mostraba modificaciones locales no confirmadas en:

- `docs/plex-home-ux.md`
- `docs/plex-home.md`
- `launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java`
- `launcher/app/src/main/java/com/gathin/tvhome/PlexManager.java`
- `launcher/app/src/main/java/com/gathin/tvhome/PlexSettingsActivity.java`
- `launcher/app/src/main/res/layout/plex_carousel.xml`
- `launcher/app/src/main/res/values-es/strings.xml`
- `launcher/app/src/main/res/values/strings.xml`
- `launcher/app/src/debug/`
- `launcher/app/src/main/java/com/gathin/tvhome/PlexServerCredentials.java`
- `launcher/app/src/test/java/com/gathin/tvhome/PlexServerCredentialsTest.java`

Estos cambios no se atribuyen ni se describen como parte de este registro porque ya existían al comenzar esta tarea. Revisa `git diff` antes de editarlos o integrarlos.

## Historial

### 2026-10-20 — Documentación inicial del proyecto

- Creación de `Design.md`, `Arquitecture.md` y `Context.md`.
- Actualización de `AGENTS.md` para exigir registro de todos los cambios y actualización del último cambio.
- Comprobación: lectura de la estructura, README y documentación relevante, scripts PowerShell, manifiesto, configuración Gradle y componentes Android principales; no se ejecutaron pruebas.
