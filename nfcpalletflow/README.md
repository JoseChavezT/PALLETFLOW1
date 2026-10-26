# NFCPalletflow

App Android propia (Kotlin + Jetpack Compose) que reúne en una sola aplicación
la lectura/escritura de etiquetas NFC y la ejecución de tareas al acercarlas.

## Funciones

- **Leer**: tipo de chip (NTAG213/215/216, Ultralight, MIFARE Classic, ISO 14443/15693, FeliCa),
  UID, ATQA/SAK, memoria usada y todos los registros NDEF.
- **Escribir**: texto, enlace, teléfono, SMS, correo, contacto, Wi‑Fi, ubicación, dirección,
  abrir app, Bluetooth y URI personalizada; varios registros en una etiqueta.
- **Tareas**: secuencias de acciones (volumen, modo de sonido, No molestar, brillo, linterna,
  Wi‑Fi, abrir apps/enlaces, alarmas, temporizador, voz, condiciones de horario y día…).
  Se graban en la etiqueta y se ejecutan al acercarla, incluso con la app cerrada.
  Se pueden probar en el momento y guardar como perfiles.
- **Otros**: copiar, borrar, formatear y bloquear etiquetas, volcado de memoria y comandos hexadecimales.
- **Historial** de todas las operaciones.

## Compilar

GitHub Actions compila la APK firmada en cada cambio (`.github/workflows/nfcpalletflow.yml`)
y la publica en la release `nfcpalletflow-latest`. En local: `./gradlew assembleRelease`
con el Android SDK instalado.

La clave de `keystore/` es solo para este proyecto. Para publicar en tiendas, usa una clave
propia y define `NFCP_STORE_PASSWORD` / `NFCP_KEY_PASSWORD`.
