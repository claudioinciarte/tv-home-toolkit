# Plex Home: diseño y validación visual

## Objetivo

Dar protagonismo a los últimos títulos añadidos a Plex sin desplazar el dock de aplicaciones. La fila se usa con el mando: el foco debe ser visible desde el sofá, el detalle debe cambiar con cada selección y ninguna tarjeta puede tapar su título ni invadir el dock.

## Dirección visual

Se mantiene el negro real del launcher y la tipografía Outfit. El elemento propio de Plex es la imagen seleccionada, usada como fondo atenuado; las carátulas forman una estantería compacta. El ámbar Plex `#E5A00D` aparece solo en el borde de foco. No se añade movimiento automático ni elementos decorativos.

La dirección toma la base “true-black flat” de Taste Skill y conserva las imágenes como contenido. El indicador cálido de 2 dp se mantiene porque el mando necesita un estado de foco claro y de alto contraste.

## Composición e interacción

- Encabezado en frase normal, título del contenido y año o nombre del episodio.
- Tarjetas de `110 × 128 dp`, con hasta dos líneas de título y una línea de temporada/año.
- Imagen de fondo al 40 % con una capa negra para conservar la lectura del texto.
- El foco escala la tarjeta al 106 % durante 170 ms y añade un borde ámbar de 2 dp.
- Izquierda y derecha cambian el elemento, el fondo y el detalle; abajo devuelve el foco al favorito correspondiente. Arriba conserva la ruta existente a Ajustes.
- Las valoraciones solo se muestran si Plex las entrega junto a sus metadatos.

La altura del carrusel se calcula según el espacio sobre el dock. La lista deja separación sobre el bloque de título, permite el pequeño crecimiento de foco y conserva el margen con el dock.

## Validación en Fire TV

Validado en el Fire TV Stick 4K Max a 1920 × 1080 con densidad efectiva de 320 dpi. Se revisaron las tarjetas enfocadas y sin foco, el título/año, el cambio de detalle con DPAD derecha, la vuelta al dock con DPAD abajo y el retorno al elemento correspondiente con DPAD arriba. La selección de Plex se conserva cuando termina una actualización del catálogo.

Las capturas de prueba quedaron fuera del repositorio: muestran carátulas y títulos privados de la biblioteca. En la selección revisada Plex no devolvió una valoración para mostrar; la presentación de la insignia con una valoración disponible queda pendiente de un título que la incluya.
