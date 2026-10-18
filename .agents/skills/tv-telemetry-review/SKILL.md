---
name: tv-telemetry-review
description: Revisar paquetes y ajustes de telemetría y publicidad en Google TV o Fire TV, explicar la evidencia y aplicar solo limpieza reversible acordada conservando tiendas y apps.
---

# Telemetría TV

Usar el checkout tv-home-toolkit, localizado por scripts/TvHome.ps1. Leer docs/telemetry.md y el caso correspondiente en docs/cases/.

## Procedimiento

- Confirmar objetivo y ejecutar Telemetry. Es de solo lectura sobre el equipo y guarda informes privados en .local.
- Separar paquetes presentes, previamente deshabilitados, ajustes observados y tráfico capturado. null significa ausente/no disponible, no apagado.
- En Chromecast varios paquetes ya estaban deshabilitados antes de intervenir. No atribuirlos al instalador ni replicarlos sin revisar.
- Proponer cada paquete con función, estado, efecto esperado y dependencias. El nombre no demuestra envío de datos.
- Conservar Play Store/Appstore, Cast, cuenta, instaladores, DRM y actualizaciones salvo una instrucción explícita distinta.
- Tras acordar paquetes concretos, usar DisablePackages -Packages con lista explícita. Guarda estado inicial; RestorePackages no habilita indiscriminadamente lo que ya estaba deshabilitado.
- Si el script bloquea un paquete, revisar dependencias. No eludir el filtro ni repetir mutaciones sin diagnóstico.
- Resumir cambios y límites. No afirmar “sin telemetría” sin evidencia apropiada de red. Sin INTERNET solo describe el launcher.
- Captura DNS/red requiere objetivo e intervalo acordados. No interceptar credenciales ni instalar certificados por defecto; mantener capturas fuera de Git.

## Rapidez y privacidad

Reutilizar el inventario de la sesión mientras siga vigente y agrupar lecturas independientes. Mantener secuenciales respaldo, cambios y recuperación. No descargar auxiliares si el servicio integrado resuelve Inicio.

Para compartir resultados, retirar IP, seriales, cuentas, SSID, claves, códigos y listas personales de apps. Publicar conclusiones anonimizadas, no volcados completos.
