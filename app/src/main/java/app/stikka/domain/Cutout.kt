package app.stikka.domain

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

/**
 * Recorte inteligente con ML Kit Subject Segmentation.
 * Todo ocurre en el móvil: la foto no sale del teléfono.
 */
object Cutout {

    /** Recorte normal: una sola máscara con todo lo que sea sujeto (el modo que siempre ha ido bien). */
    private val segmenter: SubjectSegmenter by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableForegroundConfidenceMask()
                .build()
        )
    }

    /** Modo «varios sujetos»: solo se usa si el usuario lo pide. */
    private val multiSegmenter: SubjectSegmenter by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableMultipleSubjects(
                    SubjectSegmenterOptions.SubjectResultOptions.Builder()
                        .enableConfidenceMask()
                        .build()
                )
                .build()
        )
    }

    /**
     * Se asegura de que el modelo está en el móvil. Si hay que descargarlo, avisa con [onDownloading]
     * y espera (la primera vez puede tardar unos segundos).
     */
    suspend fun ensureModel(context: Context, onDownloading: () -> Unit) {
        val client = ModuleInstall.getClient(context)
        if (client.areModulesAvailable(segmenter).await().areModulesAvailable()) return
        onDownloading()
        client.installModules(ModuleInstallRequest.newBuilder().addApi(segmenter).build()).await()
        repeat(120) {
            if (client.areModulesAvailable(segmenter).await().areModulesAvailable()) return
            delay(1000)
        }
        throw IllegalStateException("model download timeout")
    }

    /** Confianza (0..1) de que cada píxel pertenece a algún sujeto. */
    suspend fun mask(bitmap: Bitmap): FloatArray {
        val result = segmenter.process(InputImage.fromBitmap(bitmap, 0)).await()
        val buffer = result.foregroundConfidenceMask ?: throw IllegalStateException("no mask")
        buffer.rewind()
        val mask = FloatArray(bitmap.width * bitmap.height)
        buffer.get(mask, 0, minOf(mask.size, buffer.remaining()))
        return mask
    }

    /** Lo mismo con el resultado empaquetado (para quien solo quiere la máscara general). */
    suspend fun segment(bitmap: Bitmap): CutResult = CutResult(mask(bitmap), emptyList())

    /**
     * Una máscara por cada sujeto (hasta 4, del más grande al más pequeño), al tamaño de [bitmap].
     * Se calcula sobre una copia reducida para gastar poca memoria y luego se amplía.
     */
    suspend fun subjects(bitmap: Bitmap): List<FloatArray> {
        val w = bitmap.width
        val h = bitmap.height
        val scale = minOf(1f, 1024f / maxOf(w, h))
        val small = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, maxOf(1, (w * scale).toInt()), maxOf(1, (h * scale).toInt()), true)
        } else bitmap
        val sw = small.width
        val sh = small.height
        val result = multiSegmenter.process(InputImage.fromBitmap(small, 0)).await()
        val masks = result.subjects.mapNotNull { subj ->
            val sb = subj.confidenceMask ?: return@mapNotNull null
            sb.rewind()
            val m = FloatArray(sw * sh)
            for (y in 0 until subj.height) {
                val row = (subj.startY + y) * sw + subj.startX
                for (x in 0 until subj.width) {
                    val v = if (sb.hasRemaining()) sb.get() else 0f
                    val i = row + x
                    if (i in m.indices) m[i] = v
                }
            }
            m to subj.width.toLong() * subj.height
        }.sortedByDescending { it.second }.take(4).map { it.first }
        if (small === bitmap) return masks
        // Ampliar cada máscara al tamaño original (vecino más próximo, el borde se suaviza después).
        return masks.map { m ->
            FloatArray(w * h) { i ->
                val x = (i % w) * sw / w
                val y = (i / w) * sh / h
                m[y * sw + x]
            }
        }
    }
}

/** Máscara de todo lo encontrado y, si hay varios, una por sujeto. */
class CutResult(val mask: FloatArray, val subjects: List<FloatArray>)
