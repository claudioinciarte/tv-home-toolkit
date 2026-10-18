---
name: fire-tv-home
description: Instalar y restaurar la Home de este repositorio en Fire TV, activar la redirección integrada y acelerar Inicio en Fire OS 7 cuando se autoriza el ajuste global.
---

# Fire TV Home

Localizar el checkout tv-home-toolkit por scripts/TvHome.ps1 y launcher/. Leer docs/cases/fire-tv.md; para apps y tiendas, docs/launcher.md.

## Flujo

1. Confirmar el objetivo ADB autorizado y ejecutar Inspect. No reutilizar IP, modelo o firmware de un caso anterior.
2. Compilar una vez o reutilizar el APK autorizado. Install -Platform FireTV guarda estado y concede WRITE_SECURE_SETTINGS. Amazon Home queda habilitado.
3. En Home activar Ajustes > Inicio del mando > Abrir Home con el botón Inicio. Se necesitan la preferencia interna y la accesibilidad; escribir solo enabled_accessibility_services no activa la preferencia.
4. Preservar otros servicios de accesibilidad. Revisar auxiliares que compitan; no desinstalarlos sin autorización. El servicio propio ya incorpora la función de Home on Fire, sin depender de él.
5. Para acelerar API 28, explicar que EnableFastHome cambia min_futurity GLOBAL a 250 ms y afecta al planificador de todas las apps. Reutilizar autorización previa solo si cubre ese alcance; en caso contrario solicitarla antes del cambio.
6. Ejecutar EnableFastHome con el mismo StateRoot. Si se pide verificar, medir varias transiciones app > Inicio > Home con logcat filtrado TvHomeRedirect y actividad reanudada. Distinguir desde detección de Amazon Home de latencia total del mando.
7. RestoreFastHome revierte min_futurity. Restore retira solo el servicio propio y abre la Home anterior. No sobrescribir cambios concurrentes.

## Evitar caminos fallidos

- En AFTKA/Fire OS 7.7.1.3, deshabilitar Amazon Home falla por protección. No forzar desinstalaciones.
- set-home-activity puede informar Success sin cambiar la Home efectiva.
- startActivity y PendingIntent.send directo mantuvieron unos 5 s.
- AlarmManager sin ajustar min_futurity también puede tardar 5 s. El APK solo no garantiza medio segundo.
- Los ajustes probados usan DEVICE_INFO_SETTINGS en com.amazon.tv.settings.v2; ACTION_SETTINGS puede volver a Home y disparar otra redirección.
- El arranque está implementado, no se verificó reiniciando. Otros firmwares requieren evidencia nueva.
- No hacen falta root, INTERNET, lectura del árbol de accesibilidad, filtros de teclas ni consultas continuas.
