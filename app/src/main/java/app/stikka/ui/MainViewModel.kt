package app.stikka.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.stikka.StikkaApp
import app.stikka.data.Sticker
import app.stikka.domain.Cutout
import app.stikka.domain.Images
import app.stikka.domain.StickerMath
import app.stikka.domain.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Stage { LOADING, DOWNLOADING_MODEL, CUTTING }

/** Estado de la pantalla de recorte. */
sealed interface CutState {
    data class Working(val stage: Stage, val original: Bitmap? = null) : CutState
    data class Ready(
        val original: Bitmap,
        val mask: FloatArray,
        val sticker: Bitmap,
        val autoCut: Boolean,
    ) : CutState
    data class NoSubject(val original: Bitmap) : CutState
    data class Failed(val message: String, val original: Bitmap? = null) : CutState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = StikkaApp.instance.store

    private val _stickers = MutableStateFlow(store.list())
    val stickers: StateFlow<List<Sticker>> = _stickers

    var cut by mutableStateOf<CutState?>(null)
        private set
    var viewing by mutableStateOf<Sticker?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    private var job: Job? = null

    fun startCutout(uri: Uri) {
        job?.cancel()
        viewing = null
        cut = CutState.Working(Stage.LOADING)
        job = viewModelScope.launch {
            var original: Bitmap? = null
            try {
                original = withContext(Dispatchers.IO) { Images.load(getApplication(), uri) }
                cut = CutState.Working(Stage.CUTTING, original)
                Cutout.ensureModel(getApplication()) { cut = CutState.Working(Stage.DOWNLOADING_MODEL, original) }
                cut = CutState.Working(Stage.CUTTING, original)
                val mask = Cutout.confidenceMask(original)
                if (StickerMath.coverage(mask) < 0.002f) {
                    cut = CutState.NoSubject(original)
                    return@launch
                }
                cut = build(original, mask, autoCut = true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                cut = CutState.Failed(
                    if (original == null) {
                        tr("No se ha podido abrir la imagen.", "The image couldn't be opened.")
                    } else {
                        tr(
                            "No se ha podido hacer el recorte. Comprueba que tienes conexión la primera vez (hay que descargar el modelo) y que Google Play Services está al día.",
                            "The cutout failed. The first time you need a connection to download the model, and Google Play Services must be up to date.",
                        )
                    },
                    original,
                )
            }
        }
    }

    /** Usa la imagen completa, sin recortar. */
    fun useWholeImage() {
        val original = when (val c = cut) {
            is CutState.NoSubject -> c.original
            is CutState.Failed -> c.original
            is CutState.Ready -> c.original
            else -> null
        } ?: return
        job?.cancel()
        job = viewModelScope.launch {
            cut = build(original, Images.fullMask(original), autoCut = false)
        }
    }

    private suspend fun build(original: Bitmap, mask: FloatArray, autoCut: Boolean): CutState.Ready =
        withContext(Dispatchers.Default) {
            val cutout = Images.applyMask(original, mask)
            val sticker = Images.renderSticker(cutout, mask)
            CutState.Ready(original, mask, sticker, autoCut)
        }

    fun saveCut() {
        val ready = cut as? CutState.Ready ?: return
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    store.save(
                        webp = Images.encodeWebp(ready.sticker),
                        sourceJpeg = Images.encodeJpeg(ready.original),
                        maskPng = Images.encodeMask(ready.mask, ready.original.width, ready.original.height),
                    )
                }
                refresh()
                cut = null
                message = tr("Sticker guardado", "Sticker saved")
            } catch (e: Throwable) {
                message = tr("No se ha podido guardar", "Couldn't save it")
            } finally {
                saving = false
            }
        }
    }

    fun closeCut() {
        job?.cancel()
        cut = null
    }

    fun open(sticker: Sticker) { viewing = sticker }
    fun closeViewer() { viewing = null }

    fun delete(sticker: Sticker) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(sticker) }
            if (viewing?.id == sticker.id) viewing = null
            refresh()
            message = tr("Sticker eliminado", "Sticker deleted")
        }
    }

    private suspend fun refresh() {
        _stickers.value = withContext(Dispatchers.IO) { store.list() }
    }
}
