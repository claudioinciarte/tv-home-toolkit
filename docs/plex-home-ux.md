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
- Izquierda y derecha cambian el elemento, el fondo y el detalle. El título y el subtítulo salen 8 dp hacia la tarjeta anterior en 120 ms y entran desde el lado opuesto en 200 ms; el fondo cruza entre imágenes en 320 ms. Si el foco vuelve a cambiar durante el cruce, se conserva la imagen que más se ve y se inicia la transición siguiente desde ella. Abajo devuelve el foco al favorito correspondiente. Arriba conserva la ruta existente a Ajustes.
- Las valoraciones solo se muestran si Plex las entrega junto a sus metadatos.

En Ajustes de Plex, «Conexión local» queda junto al selector de servidores. El usuario elige uno de sus servidores seleccionados, escribe una IPv4 privada y un puerto, y usa «Probar y guardar». La dirección se guarda solo si responde como Plex por HTTPS; el certificado sigue validándose. Se puede quitar la ruta para volver al orden anunciado por Plex.

La altura del carrusel se calcula según el espacio sobre el dock. La lista deja separación sobre el bloque de título, permite el pequeño crecimiento de foco y conserva el margen con el dock.

Al desplazarse por las filas de aplicaciones, el carrusel acompaña el desplazamiento vertical del grid y sale por el borde superior. Al volver hacia el dock, reaparece con el mismo movimiento; no roba foco ni cambia la posición del dock.

## Validación en Fire TV

Validado en el Fire TV Stick 4K Max a 1920 × 1080 con densidad efectiva de 320 dpi. Se revisaron las tarjetas enfocadas y sin foco, el título/año, el cambio de detalle con DPAD derecha, la vuelta al dock con DPAD abajo y el retorno al elemento correspondiente con DPAD arriba. La selección de Plex se conserva cuando termina una actualización del catálogo.

En la compilación actual, el DPAD abajo desplaza el carrusel hacia arriba junto al grid hasta sacarlo de pantalla; DPAD arriba lo trae de vuelta al llegar al dock. Recorrido visual comprobado en el Fire TV a 1920 × 1080 mediante eventos DPAD enviados por ADB; falta repetirlo con el mando físico.

En la misma revisión, Inicio del mando aparecía desactivado en Fire OS aunque la preferencia local del launcher estuviera marcada. El servicio de accesibilidad no figuraba como activo, así que el botón Home seguía abriendo Amazon Home. La activación desde el ajuste propio quedó pendiente.

Los ajustes de Plex se reorganizaron en tres paneles siguiendo la estructura de Fire TV: identidad y descripción a la izquierda, opciones navegables en el centro y detalle/estado a la derecha. Probado en el Fire TV a 1920 × 1080: el foco cambia la explicación contextual y DPAD permite llegar a las opciones inferiores con scroll. La validación se hizo con DPAD por ADB; falta repetirla con el mando físico.

Al abrir una opción de cuenta, servidores, bibliotecas o conexión local, el panel derecho pasa a mostrar sus controles inline. El selector de servidores, el de bibliotecas y el formulario local se revisaron instalados en el Fire TV a 1920 × 1080; DPAD derecha lleva al primer control. Guardar o cancelar vuelve el foco a la opción central. El teclado del sistema sigue apareciendo al editar la dirección o el puerto.

Las capturas de prueba quedaron fuera del repositorio: muestran carátulas y títulos privados de la biblioteca. En la selección revisada Plex no devolvió una valoración para mostrar; la presentación de la insignia con una valoración disponible queda pendiente de un título que la incluya.

La configuración de ruta local está instalada en el Fire TV y el flujo HTTPS con la identidad del servidor se verificó. En la revisión del editor inline, el formulario y la navegación DPAD se comprobaron por ADB; falta revisar el teclado con el mando físico.

Las transiciones de título y fondo descritas arriba quedan pendientes de validación visual con el mando en el Fire TV.
