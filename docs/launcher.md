# Launcher

Identificador com.gathin.tvhome. Dock y cuadrícula con descubrimiento de apps, textos en español y ajustes. Se retiró Inputs para equipos de streaming sin entradas de televisión.

Configurar selección/orden/ocultas en launcher/app/src/main/res/values/config.xml. Mantener visible la tienda. Las apps nuevas aparecen si ofrecen actividad lanzable de TV y no están ocultas; algunas apps móviles no la ofrecen.

Permisos: estado de red y Wi-Fi, ubicación para datos de Wi-Fi heredados del proyecto, WRITE_SECURE_SETTINGS para el interruptor Fire TV y BOOT_COMPLETED. No tiene INTERNET. canRetrieveWindowContent=false.

Versión incluida: 2.2 (versionCode 4). Un APK compilado en otro equipo normalmente tendrá otra firma debug. Si aparece INSTALL_FAILED_UPDATE_INCOMPATIBLE, detenerse: desinstalar borra datos y no es una recuperación automática autorizada.

No se publican claves de firma, APKs de terceros ni configuraciones personales de los dispositivos.
