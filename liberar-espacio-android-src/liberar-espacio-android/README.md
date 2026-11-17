# Liberar espacio (APK)

App Android que envuelve `app/src/main/assets/index.html` en un WebView y lee la galería
mediante un puente nativo (`AndroidStorage` → `window.StorageBridge`).

## Requisitos

- Android Studio reciente (Ladybug 2024.2 o posterior). Trae JDK 17.
- Teléfono con Android 11 o superior (`minSdk = 30`).

## Abrir y probar

1. Abre la carpeta `liberar-espacio-android` en Android Studio.
2. Espera la sincronización de Gradle. Si pide generar el wrapper, acepta.
3. Activa la depuración USB en el teléfono y pulsa **Run**.

## Generar el APK

- Depuración: **Build > Build APK(s)**. Sale en `app/build/outputs/apk/debug/app-debug.apk`.
- Línea de comandos (requiere `ANDROID_HOME` con `platforms;android-35` y `build-tools;35.0.0`):
  `./gradlew assembleDebug`. El wrapper usa Gradle 8.9.
- Para instalarla fuera de Play, genera una firmada: **Build > Generate Signed App > APK**.
  Necesitas un keystore; guárdalo, porque sin él no podrás actualizar la app.

## Qué revisar en el teléfono

1. Pulsa **Analizar galería** y concede el permiso de fotos y videos.
2. Revisa las categorías: duplicados, similares, borrosas, capturas, grandes.
3. Marca elementos y confirma. Deben ir a la papelera del sistema (Fotos > Papelera).
4. Si eliges solo algunas fotos (Android 14+), la app solo verá esas.

## Limitaciones conocidas

- Solo fotos y videos. Los archivos generales (PDF, APK, ZIP) no se leen en esta versión.
- Los videos se leen por HTTP sin soporte de rangos. La duración puede no cargarse en algunos
  dispositivos, y el análisis de cada video tiene un límite de 8 segundos.
  Si el seek falla, la solución propuesta es responder a `Range` en `MediaPathHandler`
  (206 Partial Content con `Content-Range`, usando `openAssetFileDescriptor` y `skip`),
  o reproducir el video con un `content://` vía un reproductor nativo.
- Los umbrales de `index.html` (`BLUR_MAX`, `SIM_MAX_DIST`) son de partida. Calíbralos con tus fotos.
- HEIC: si el WebView no lo decodifica, el elemento aparece sin vista previa y marcado con error.
- Todavía no se ha compilado: el entorno de trabajo no pudo instalar el Android SDK.
