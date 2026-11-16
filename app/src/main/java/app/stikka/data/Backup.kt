package app.stikka.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Resultado de una importación. */
data class ImportResult(val stickers: Int, val packs: Int, val skippedPacks: Int)

/**
 * Copias de seguridad y paquetes compartidos. Formato (un .zip, extensión .stikka para paquetes):
 * - `stikka.json`: `{ "format": 1, "packs": [ ... ] }`
 * - `stickers/<id>/<archivos del sticker>`
 */
class Backup(context: Context, private val store: StickerStore, private val packs: PackStore) {
    private val files = context.filesDir
    private val cache = context.cacheDir

    /** Exporta todo, o solo los paquetes indicados con sus stickers. */
    fun export(out: OutputStream, packIds: Set<String>? = null) {
        val chosenPacks = packs.list().filter { packIds == null || it.id in packIds }
        val stickerIds: Set<String> = if (packIds == null) store.list().map { it.id }.toSet()
        else chosenPacks.flatMap { it.stickerIds }.toSet()
        ZipOutputStream(out.buffered()).use { zip ->
            val manifest = JSONObject()
                .put("format", 1)
                .put("app", "Stikka")
                .put("packs", JSONArray().apply { chosenPacks.forEach { put(it.toJson()) } })
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest.toString().toByteArray())
            zip.closeEntry()
            stickerIds.mapNotNull { store.get(it) }.forEach { s ->
                s.dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }?.forEach { f ->
                    zip.putNextEntry(ZipEntry("stickers/${s.id}/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Importa sin borrar nada: añade los stickers y paquetes que no existan ya. */
    fun import(input: InputStream): ImportResult {
        val tmp = File(cache, "import-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            var manifest: JSONObject? = null
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name
                    if (e.isDirectory) continue
                    if (name == MANIFEST) {
                        manifest = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                        continue
                    }
                    // Solo stickers/<id>/<archivo>, sin rutas raras.
                    val parts = name.split('/')
                    if (parts.size != 3 || parts[0] != "stickers" || !SAFE.matches(parts[1]) || !SAFE.matches(parts[2])) continue
                    val target = File(tmp, "${parts[1]}/${parts[2]}")
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
            }
            val m = manifest ?: throw IllegalArgumentException("not a Stikka file")

            val root = File(files, "stickers").apply { mkdirs() }
            var stickerCount = 0
            tmp.listFiles()?.filter { it.isDirectory && File(it, "sticker.webp").exists() }?.forEach { dir ->
                val dest = File(root, dir.name)
                if (!dest.exists()) {
                    if (!dir.renameTo(dest)) dir.copyRecursively(dest, overwrite = false)
                    stickerCount++
                }
            }

            val existing = packs.list()
            val arr = m.optJSONArray("packs") ?: JSONArray()
            var packCount = 0
            var skipped = 0
            for (i in 0 until arr.length()) {
                val p = runCatching { Pack.fromJson(arr.getJSONObject(i)) }.getOrNull() ?: continue
                if (existing.any { it.id == p.id }) { skipped++; continue }
                if (existing.size + packCount >= Pack.MAX_PACKS) { skipped++; continue }
                val valid = p.copy(stickerIds = p.stickerIds.filter { store.get(it) != null })
                packs.add(valid)
                packCount++
            }
            return ImportResult(stickerCount, packCount, skipped)
        } finally {
            tmp.deleteRecursively()
        }
    }

    companion object {
        const val MANIFEST = "stikka.json"
        private val SAFE = Regex("^[A-Za-z0-9._-]+$")
    }
}
