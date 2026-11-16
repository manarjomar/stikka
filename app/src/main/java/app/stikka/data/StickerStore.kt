package app.stikka.data

import android.content.Context
import java.io.File

/** Un sticker guardado. La carpeta guarda también la foto original y la máscara para retocarlo. */
data class Sticker(val id: String, val dir: File) {
    val file: File get() = File(dir, "sticker.webp")
    val created: Long get() = id.substringBefore('-').toLongOrNull() ?: dir.lastModified()
    val sizeBytes: Long get() = file.length()
    /** Cambia cada vez que se reescribe el archivo, para refrescar las imágenes. */
    val version: Long get() = file.lastModified()
    val sourceFile: File get() = File(dir, "source.jpg")
    val maskFile: File get() = File(dir, "mask.png")
    /** Se puede retocar si guardamos la foto original y la máscara. */
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

    fun save(webp: ByteArray, sourceJpeg: ByteArray, maskPng: ByteArray): Sticker {
        val id = "${System.currentTimeMillis()}-${(1000..9999).random()}"
        val dir = File(root, id).apply { mkdirs() }
        File(dir, "source.jpg").writeBytes(sourceJpeg)
        File(dir, "mask.png").writeBytes(maskPng)
        // El sticker se escribe el último: si algo falla antes, no aparece en la lista.
        File(dir, "sticker.webp").writeBytes(webp)
        return Sticker(id, dir)
    }

    fun get(id: String): Sticker? = File(root, id).takeIf { File(it, "sticker.webp").exists() }?.let { Sticker(id, it) }

    /** Reescribe un sticker retocado. */
    fun update(sticker: Sticker, webp: ByteArray, maskPng: ByteArray) {
        sticker.maskFile.writeBytes(maskPng)
        val tmp = File(sticker.dir, "sticker.tmp")
        tmp.writeBytes(webp)
        tmp.renameTo(sticker.file)
    }

    fun delete(sticker: Sticker) {
        sticker.dir.deleteRecursively()
    }
}
