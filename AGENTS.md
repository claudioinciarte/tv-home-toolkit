# TV Home Toolkit

Trabaja desde la raíz. Conserva LICENSE (MIT del proyecto Gathin23/tv-vibehack) y la licencia OFL de Outfit.

- Antes de cambiar archivos, lee `Context.md` para conocer el hilo de trabajo y el último cambio registrado.
- Registra en `Context.md` todos los cambios que hagas, incluidos documentación, código, configuración y pruebas. Anota fecha, resumen, archivos afectados, motivo y comprobaciones para que otros agentes puedan continuar el contexto.
- En `Context.md`, actualiza siempre la sección «Último cambio» al terminar cada conjunto de cambios. No reescribas ni atribuyas cambios locales preexistentes que no hayas realizado.
- Si no puedes actualizar el registro en el mismo conjunto de cambios, deja constancia de ello antes de terminar y explica por qué.

- Lee la skill correspondiente en .agents/skills/ y el caso en docs/cases/.
- No conectes ni modifiques dispositivos por crear documentación o preparar una publicación.
- scripts/TvHome.ps1 centraliza ADB: objetivo obligatorio, inventario, respaldo, instalación y recuperación.
- Google TV y Fire TV tienen procedimientos distintos. No deshabilites Amazon Home.
- Conserva Play Store/Appstore, apps del usuario, Cast, cuentas y actualizaciones salvo petición explícita.
- .local contiene respaldos privados. No publiques IP reales, seriales, cuentas, capturas, claves ni códigos.
- Inventario de paquetes y ajustes no demuestra tráfico ni ausencia de telemetría.
- EnableFastHome modifica un ajuste GLOBAL; explica su alcance antes de aplicarlo.
- Un conflicto de firma no autoriza desinstalar el launcher ni borrar datos automáticamente.
