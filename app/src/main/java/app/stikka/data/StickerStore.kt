package app.stikka.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.stikka.domain.Decor
import java.io.File

/**
 * Un sticker guardado. Su carpeta contiene:
 * - `sticker.webp`: el sticker final (lo que se comparte y va a WhatsApp).
 * - `base.png`: el recorte de 512 px sin decoración.
 * - `decor.json`: borde, textos y emojis (editables).
 * - `source.jpg` y `mask.png`: la foto original y la máscara, para retocar el recorte.
 */
data class Sticker(
    val id: String,
    val dir: File,
    /** Huella del archivo al leerlo: cambia al reescribirlo, para refrescar las imágenes. */
    val version: Long = File(dir, "sticker.webp").let { it.lastModified() * 31 + it.length() },
) {
    val file: File get() = File(dir, "sticker.webp")
    val baseFile: File get() = File(dir, "base.png")
    val decorFile: File get() = File(dir, "decor.json")
    val sourceFile: File get() = File(dir, "source.jpg")
    val maskFile: File get() = File(dir, "mask.png")
    val created: Long get() = id.substringBefore('-').toLongOrNull() ?: dir.lastModified()
    val sizeBytes: Long get() = file.length()
    /** Se puede retocar el recorte si guardamos la foto original y la máscara. */
    val editable: Boolean get() = sourceFile.exists() && maskFile.exists()
}

/** Almacén sencillo en la memoria interna de la app: una carpeta por sticker. */
class StickerStore(context: Context) {
    private val root = File(context.filesDir, "stickers").apply { mkdirs() }

    fun list(): List<Sticker> =
        (root.listFiles() ?: emptyArray())
            .filter { it.isDirectory && File(it, "sticker.webp").exists() }
            .map { Sticker(it.name, it) }
            .sortedByDescending { it.created }

    fun get(id: String): Sticker? =
        File(root, id).takeIf { File(it, "sticker.webp").exists() }?.let { Sticker(id, it) }

    fun save(
        webp: ByteArray,
        basePng: ByteArray,
        decorJson: String,
        sourceJpeg: ByteArray? = null,
        maskPng: ByteArray? = null,
    ): Sticker {
        val id = "${System.currentTimeMillis()}-${(1000..9999).random()}"
        val dir = File(root, id).apply { mkdirs() }
        val sticker = Sticker(id, dir)
        sourceJpeg?.let { sticker.sourceFile.writeBytes(it) }
        maskPng?.let { sticker.maskFile.writeBytes(it) }
        sticker.baseFile.writeBytes(basePng)
        sticker.decorFile.writeText(decorJson)
        // El sticker se escribe el último: si algo falla antes, no aparece en la lista.
        sticker.file.writeBytes(webp)
        return sticker
    }

    /** Reescribe las partes que cambian de un sticker. */
    fun write(
        sticker: Sticker,
        webp: ByteArray,
        basePng: ByteArray? = null,
        decorJson: String? = null,
        maskPng: ByteArray? = null,
    ) {
        maskPng?.let { sticker.maskFile.writeBytes(it) }
        basePng?.let { sticker.baseFile.writeBytes(it) }
        decorJson?.let { sticker.decorFile.writeText(it) }
        val tmp = File(sticker.dir, "sticker.tmp")
        tmp.writeBytes(webp)
        tmp.renameTo(sticker.file)
    }

    fun loadDecor(sticker: Sticker): Decor =
        Decor.fromJson(runCatching { sticker.decorFile.readText() }.getOrNull())

    /** Recorte sin decoración. Los stickers antiguos (sin base.png) usan su propio WebP. */
    fun loadBase(sticker: Sticker): Bitmap? {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val f = if (sticker.baseFile.exists()) sticker.baseFile else sticker.file
        return BitmapFactory.decodeFile(f.absolutePath, opts)
    }

    fun delete(sticker: Sticker) {
        sticker.dir.deleteRecursively()
    }
}
