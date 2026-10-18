# Fire TV e Inicio rápido

Equipo: Fire TV Stick 4K Max 1.ª gen, AFTKA, Fire OS 7.7.1.3 (PS7713/5443), Android 9/API 28.

## Evidencia

- Deshabilitar com.amazon.tv.launcher mediante pm falla con SecurityException.
- set-home-activity puede devolver Success y Amazon seguir ganando (prioridad 950).
- Se usó Home on Fire inicialmente; después se integró la función en el launcher y se desinstaló el auxiliar.
- HomeRedirectService usa metadatos de ventana y foco; no obtiene el árbol de accesibilidad ni filtra teclas.
- startActivity tardaba unos 5 s. PendingIntent.send directo tardó 5005 ms.
- AlarmManager entrega un PendingIntent explícito. Sin ajustar el mínimo global, la versión final tardó 5248–5794 ms.
- Con min_futurity=250: 501, 477 y 491 ms desde detectar Amazon Home hasta mostrar Home.
- Las primeras medidas de 788–1012 ms no fueron estables tras reinstalar. No son el resultado final.

Pulsaciones enviadas por ADB; no se probó reinicio ni mando físico.

## Instalación

Install -Platform FireTV guarda el estado, instala el APK y concede WRITE_SECURE_SETTINGS para gestionar el interruptor. Amazon Home permanece habilitado.

En Home: Ajustes > Inicio del mando > Abrir Home con el botón Inicio. El interruptor preserva otros servicios. Revisar y desactivar auxiliares que compitan antes de verificar; no se desinstalan automáticamente.

Ajustes de Fire TV usa DEVICE_INFO_SETTINGS en com.amazon.tv.settings.v2. ACTION_SETTINGS pasa por CTSDummySettingsHandler en este firmware y puede devolver a Amazon Home, provocando otra redirección. Conservar la ruta probada.

## Velocidad y restauración

EnableFastHome solo admite Fire TV/API 28. Guarda el valor anterior de alarm_manager_constants, conserva otras entradas y fija min_futurity=250.

Este mínimo es GLOBAL y afecta al programador de todas las apps. Explicar y obtener autorización para ese alcance antes de aplicarlo en otra instalación. No modifica los intervalos de repetición que las apps solicitan.

RestoreFastHome recupera solo min_futurity y conserva otras entradas modificadas después. Aborta si detecta un cambio concurrente en min_futurity. Restore también lo revierte si hay respaldo y retira solo nuestro servicio de accesibilidad. Abre Amazon Home y conserva el APK propio.

La apertura programada se cancela al mostrar Home, apagar la opción o destruir el servicio. BOOT_COMPLETED está implementado, pero no probado con un reinicio. No se garantiza el mismo comportamiento en firmwares distintos.

## Fuentes

- [ActivityStarter de Android 9](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-9.0.0_r1/services/core/java/com/android/server/am/ActivityStarter.java)
- [AlarmManagerService de Android 9 y MIN_FUTURITY](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-9.0.0_r1/services/core/java/com/android/server/AlarmManagerService.java)
- [PendingIntent](https://developer.android.com/reference/android/app/PendingIntent)
