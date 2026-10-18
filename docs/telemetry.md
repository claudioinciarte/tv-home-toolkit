# Telemetría

Telemetry hace lecturas ADB y guarda informes JSON/Markdown locales: modelo/SDK, paquetes, estados y ajustes concretos. No vuelca cuentas, Android ID, seriales de hardware, SSID ni logs de apps.

| Evidencia | Conclusión posible | Límite |
| --- | --- | --- |
| Paquetes presentes/deshabilitados | Estado para usuario 0 | No demuestra que envíen datos ni que todo el firmware haya dejado de hacerlo |
| Valores de preferencias | Configuración observada | null significa ausente/no disponible; no apagado |
| Sin INTERNET en el launcher | No abre conexiones usando ese permiso | No describe servicios del sistema |

En Chromecast ya estaban deshabilitados antes de instalar: com.google.android.tvrecommendations, com.google.android.feedback, com.google.android.adservices.api, com.google.android.ext.adservices.api, com.google.android.ondevicepersonalization.services, com.google.android.federatedcompute, com.google.android.tv.remotecontrol.logging y com.google.android.apps.tv.netoscope.

En Fire TV, separar recomendaciones/Home de cuenta, Appstore, DRM y actualizaciones. No se distribuye una lista universal para eliminar paquetes de Amazon.

DisablePackages requiere nombres explícitos, guarda estados previos y usa disable-user. Bloquea tiendas, launchers y servicios base conocidos. Ese filtro no prueba que el resto sea seguro: revisar dependencias y acordar los cambios. RestorePackages conserva el estado de lo que ya estaba deshabilitado. No desinstalar paquetes del sistema por defecto.

Mantener com.android.vending o com.amazon.venezia. Comprobar tiendas y apps nuevas cuando se solicite esa verificación.

Para conocer destinos reales se necesita captura DNS/red autorizada, intervalo definido y correlación con apps. No instalar certificados ni interceptar credenciales por defecto. Capturas fuera de Git.
