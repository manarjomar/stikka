# Stikka – notas para mantener el repo

- App Android en Kotlin + Jetpack Compose (Material 3). Paquete `app.stikka`, minSdk 29.
- Interfaz atractiva y cuidada, siguiendo Material Design 3, con los colores de la marca (frambuesa `#E0336F` → naranja `#FF8A3D`).
- No hay SDK de Android en local: GitHub Actions es quien compila. Tras cada push, revisa el resultado del workflow y corrige si falla.
- Versionado: sube `version.txt` (0.1 → 0.2…) y reescribe `release-notes.md` (bilingüe) cuando haya cambios para el usuario; así se publica una release nueva. Cambios internos sin release: no toques `version.txt`.
- Todo bilingüe: textos de la interfaz con `tr("español", "english")`; README y notas de versión en inglés y español.
- El repo es público: la firma vive solo en los secretos `KEYSTORE_BASE64` y `SIGNING_PASSWORD` (alias `stikka`). Nunca subas el `.jks` ni la contraseña. Si cambian, las actualizaciones no se instalan encima.
- Recorte: ML Kit Subject Segmentation (modelo de Google Play Services, en el dispositivo). Cada sticker vive en `filesDir/stickers/<id>/`: `sticker.webp` (final), `base.png` (recorte sin decorar), `decor.json` (borde, textos, emojis), `source.jpg` y `mask.png` (para retocar el recorte).
- WhatsApp: `data/StickerProvider.kt` sigue el formato de la app de ejemplo oficial (github.com/WhatsApp/stickers). Paquetes en `filesDir/packs.json` (máx. 10 paquetes, 3–30 stickers, sin mezclar animados y estáticos); icono 96×96 en `packs/<id>/tray.png`. Cada cambio sube `version` del paquete (image_data_version). Emojis y origen de cada sticker en `meta.json`.
- Ajustes en `data/Settings.kt` (prefs «settings»); `tr()` respeta el idioma elegido (`langOverride`). Actualizaciones: `domain/Updates.kt` lee la última release de GitHub (permiso INTERNET solo para esto). Copias y paquetes `.stikka`: `data/Backup.kt` (zip con `stikka.json` + `stickers/<id>/…`; importar nunca borra).
- Hoja de ruta: 0.2 editor de recorte (pincel, lazo, rectángulo/cuadrado, zoom, lupa, deshacer) · 0.3 textos y emojis editables, borde blanco · 0.4 paquetes, «Añadir a WhatsApp» e importar desde WhatsApp · 0.5 ajustes, actualizaciones, copia de seguridad, compartir paquetes, filtros, ordenar, duplicar, edición extra, varios sujetos · 1.0 pulido · después, animados desde vídeo/GIF (necesita un codificador de WebP animado; Android no trae uno).
