# Diseño

## Propósito

TV Home Toolkit reúne un launcher Android para televisores y herramientas PowerShell para instalarlo, inspeccionar el dispositivo y recuperar cambios. La interfaz está pensada para navegar desde el sofá con un mando; Google TV y Fire TV tienen flujos de instalación y recuperación diferentes.

## Dirección visual

- Pantalla panorámica de fondo negro, con tipografía Outfit y una composición despejada.
- Reloj, saludo e información de red acompañan al dock de aplicaciones.
- El foco del mando es visible y persistente. El nombre y el brillo ambiental de la app enfocada complementan el dock sin sustituirlo.
- El dock conserva las apps seleccionadas; la cuadrícula permite recorrer las aplicaciones lanzables del dispositivo.
- El carrusel opcional de Plex presenta hasta 20 elementos añadidos recientemente encima del dock. La carátula enfocada informa el fondo; tarjetas compactas muestran título y metadatos.
- El control vertical pasa entre carrusel y aplicaciones mediante DPAD, sin quitarle el dock su lugar.
- Los ajustes de Plex usan paneles navegables con descripción contextual y controles adaptados al mando.

## Interacción

- La pantalla de inicio es una actividad Android declarada como `HOME` y `LEANBACK_LAUNCHER`.
- La cuadrícula y el dock exponen el foco para la navegación DPAD; seleccionar una tarjeta abre su actividad lanzable.
- La redirección opcional de Fire TV se activa desde el ajuste propio y usa un servicio de accesibilidad. La instalación por sí sola no activa la preferencia del sistema.
- La estantería Plex es opcional y se configura desde los ajustes: vinculación por PIN, selección de servidores y bibliotecas, y actualización del catálogo.
- Las dimensiones, animaciones y validaciones visuales de Plex están descritas en [docs/plex-home-ux.md](docs/plex-home-ux.md).

## Principios de producto

- Mantener la tienda, las aplicaciones del usuario, Cast, cuentas y actualizaciones.
- Hacer reversibles los cambios de dispositivo y conservar respaldos por objetivo.
- No presentar inventarios de paquetes o ajustes como prueba de tráfico o de ausencia de telemetría.
- No mostrar ni incluir en el repositorio identificadores, cuentas, credenciales o respaldos privados.
- Mantener la función Plex optativa; la app no recoge contraseñas de Plex ni reproduce contenido por sí misma.

## Referencias

- [Guía del launcher](docs/launcher.md)
- [Plex Home: configuración y seguridad](docs/plex-home.md)
- [Plex Home: diseño y validación visual](docs/plex-home-ux.md)
- [Casos de Google TV y Fire TV](docs/cases/)
