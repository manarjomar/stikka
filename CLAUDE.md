# Stikka – notas para mantener el repo

- App Android en Kotlin + Jetpack Compose (Material 3). Paquete `app.stikka`, minSdk 29.
- Interfaz atractiva y cuidada, siguiendo Material Design 3, con los colores de la marca (frambuesa `#E0336F` → naranja `#FF8A3D`).
- No hay SDK de Android en local: GitHub Actions es quien compila. Tras cada push, revisa el resultado del workflow y corrige si falla.
- Versionado: sube `version.txt` (0.1 → 0.2…) y reescribe `release-notes.md` (bilingüe) cuando haya cambios para el usuario; así se publica una release nueva. Cambios internos sin release: no toques `version.txt`.
- Todo bilingüe: textos de la interfaz con `tr("español", "english")`; README y notas de versión en inglés y español.
- El repo es público: la firma vive solo en los secretos `KEYSTORE_BASE64` y `SIGNING_PASSWORD` (alias `stikka`). Nunca subas el `.jks` ni la contraseña. Si cambian, las actualizaciones no se instalan encima.
- Recorte: ML Kit Subject Segmentation (modelo de Google Play Services, en el dispositivo). Cada sticker guarda `source.jpg`, `mask.png` y `sticker.webp` en `filesDir/stickers/<id>/`, para poder retocar la máscara después.
- Hoja de ruta: 0.2 editor de recorte (pincel, lazo, rectángulo/cuadrado, zoom, lupa, deshacer) · 0.3 textos y emojis editables, borde blanco · 0.4 paquetes, «Añadir a WhatsApp» e importar desde WhatsApp · 1.0 pulido · después, animados.
