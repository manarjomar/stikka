<p align="center"><img src="docs/icono.png" width="140" alt="Stikka"></p>

<h1 align="center">Stikka</h1>

<p align="center">
  <b>Ad-free WhatsApp sticker maker for Android, with on-device smart cutout.</b><br>
  <b>Creador de stickers de WhatsApp para Android, sin anuncios y con recorte inteligente.</b>
</p>

<p align="center">
  <a href="https://github.com/manarjomar/stikka/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/manarjomar/stikka?color=E0336F&label=release"></a>
  <img alt="Android 10+" src="https://img.shields.io/badge/Android-10%2B-FF8A3D">
  <img alt="No ads" src="https://img.shields.io/badge/ads-none-555">
</p>

---

## 🇬🇧 English

### What it does
- **Smart cutout** – picks out the person, pet or object automatically (Google ML Kit, runs on your phone).
- **Any source** – gallery, camera, or *Share → Stikka* from any app.
- **WhatsApp-ready** – 512 × 512 px transparent WebP under 100 KB.
- **Your library** – keep, share and delete your stickers.
- **Private** – your photos never leave your phone. No ads, no trackers, no accounts.

### Roadmap
| Version | Features |
|---|---|
| **0.1** | Import → automatic cutout → save ✅ |
| **0.2** | Cutout editor: brush, lasso, rectangle/square, zoom, magnifier, undo ✅ |
| 0.3 | Text and emojis (movable, editable), white outline |
| 0.4 | Sticker packs + **Add to WhatsApp** + import from WhatsApp |
| 1.0 | Polish, themes, more languages |
| Later | Animated stickers from GIF/video |

### Install
Download `Stikka-X.Y.apk` from [Releases](https://github.com/manarjomar/stikka/releases/latest) and open it on your phone. Requires Android 10+ and Google Play Services (the cutout model is downloaded once, the first time).

---

## 🇪🇸 Español

### Qué hace
- **Recorte inteligente** – detecta y recorta solo a la persona, mascota u objeto (Google ML Kit, en tu móvil).
- **Desde cualquier sitio** – galería, cámara o *Compartir → Stikka* desde cualquier app.
- **Listo para WhatsApp** – WebP transparente de 512 × 512 px y menos de 100 KB.
- **Tu biblioteca** – guarda, comparte y elimina tus stickers.
- **Privado** – tus fotos no salen del móvil. Sin anuncios, sin rastreadores y sin cuentas.

### Hoja de ruta
| Versión | Novedades |
|---|---|
| **0.1** | Importar → recorte automático → guardar ✅ |
| **0.2** | Editor de recorte: pincel, lazo, rectángulo/cuadrado, zoom, lupa, deshacer ✅ |
| 0.3 | Textos y emojis (movibles y editables), borde blanco |
| 0.4 | Paquetes + **Añadir a WhatsApp** + importar desde WhatsApp |
| 1.0 | Pulido, temas, más idiomas |
| Después | Stickers animados desde GIF o vídeo |

### Instalar
Descarga `Stikka-X.Y.apk` desde [Releases](https://github.com/manarjomar/stikka/releases/latest) y ábrelo en el móvil. Requiere Android 10 o posterior y Google Play Services (el modelo de recorte se descarga una vez, la primera vez).

---

## Build · Compilación

GitHub Actions builds the signed APK on every push to `main`; when `version.txt` changes it publishes release `vX.Y` with `release-notes.md`.
GitHub Actions compila el APK firmado en cada push a `main`; cuando cambia `version.txt` publica la release `vX.Y` con `release-notes.md`.

Kotlin · Jetpack Compose · Material 3 · ML Kit Subject Segmentation

---

© manarjomar. All rights reserved · Todos los derechos reservados.
