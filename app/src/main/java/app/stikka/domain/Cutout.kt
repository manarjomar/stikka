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

    private val segmenter: SubjectSegmenter by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableForegroundConfidenceMask()
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

    /**
     * Devuelve la confianza (0..1) de que cada píxel pertenece a algún sujeto y, aparte,
     * una máscara por cada sujeto encontrado (hasta 4, del más grande al más pequeño).
     */
    suspend fun segment(bitmap: Bitmap): CutResult {
        val w = bitmap.width
        val h = bitmap.height
        val result = segmenter.process(InputImage.fromBitmap(bitmap, 0)).await()
        val buffer = result.foregroundConfidenceMask ?: throw IllegalStateException("no mask")
        buffer.rewind()
        val mask = FloatArray(w * h)
        buffer.get(mask, 0, minOf(mask.size, buffer.remaining()))
        val subjects = result.subjects.mapNotNull { subj ->
            val sb = subj.confidenceMask ?: return@mapNotNull null
            sb.rewind()
            val full = FloatArray(w * h)
            for (y in 0 until subj.height) {
                val row = (subj.startY + y) * w + subj.startX
                for (x in 0 until subj.width) {
                    val v = if (sb.hasRemaining()) sb.get() else 0f
                    val i = row + x
                    if (i in full.indices) full[i] = v
                }
            }
            full to subj.width.toLong() * subj.height
        }.sortedByDescending { it.second }.take(4).map { it.first }
        return CutResult(mask, if (subjects.size > 1) subjects else emptyList())
    }
}

/** Máscara de todo lo encontrado y, si hay varios, una por sujeto. */
class CutResult(val mask: FloatArray, val subjects: List<FloatArray>)
