# Validación

## Fuente del launcher

La implementación de Home 2.2 se usó en el Fire TV del caso. En esa sesión, con solo su servicio de accesibilidad y sin Home on Fire, Inicio volvió a la Home.

Con min_futurity=250, los registros finales fueron 501, 477 y 491 ms desde el evento de Amazon Home hasta la ventana de Home. Las pulsaciones se enviaron por ADB. No se probó reinicio ni mando físico. El caso Google TV funcionó tras la sustitución de su Home.

## Fire TV: comparación de `min_futurity`

El 19 de octubre de 2026 se comparó Home 2.2 en el Fire TV Stick 4K Max de 1.ª generación (AFTKA, Fire OS 7/API 28), con cinco pulsaciones `KEYCODE_HOME` por condición. La métrica va desde `Redirect requested` hasta `Home visible after` en `TvHomeRedirect`:

- 250 ms: 495, 475, 451 y 452 ms; mediana 463,5 ms. El sondeo no capturó un quinto retorno.
- 0 ms: el registro final confirmó 266, 289, 283 y 290 ms; mediana 286 ms. El sondeo no clasificó todos los intentos, por lo que la comparación es orientativa.

La mediana bajó 177,5 ms (38 %). Las pulsaciones se simularon por ADB; no se midió un mando físico ni se probó un reinicio. Se restauró y verificó `min_futurity=250`; el servicio de redirección quedó habilitado.

## Empaquetado de este repositorio

- El launcher del checkout nuevo compiló mediante scripts/Build-Launcher.ps1.
- Se cambiaron los valores por defecto del dock por tiendas, YouTube y VLC, ignorando paquetes ausentes. No se publica la selección personal original.
- Las tres skills pasaron quick_validate.py con Python en modo UTF-8.
- Los scripts PowerShell pasaron análisis de sintaxis.
- Se revisaron los archivos para excluir respaldos, IP reales, códigos, capturas, cuentas y claves.
- El APK preparado tiene SHA256 75A59CA7BE00420B0F65E0388B48AF4926FB4CED21193AD071F55564854D934D.

Los scripts nuevos son una generalización de los comandos usados en los casos. No se han ejecutado de extremo a extremo contra los dispositivos por preparar este repositorio. Las rutas de recuperación requieren conservar el StateRoot del mismo equipo.

No se hicieron nuevas conexiones ADB ni cambios en los equipos durante este empaquetado.
