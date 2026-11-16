package app.stikka.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.stikka.StikkaApp
import android.content.Context
import android.content.Intent
import app.stikka.data.Pack
import app.stikka.data.Sticker
import app.stikka.data.StickerMeta
import app.stikka.data.WaFile
import app.stikka.data.WhatsAppImport
import app.stikka.BuildConfig
import app.stikka.data.Backup
import app.stikka.domain.Release
import app.stikka.domain.Updates
import app.stikka.domain.WebpInfo
import app.stikka.domain.WhatsApp
import app.stikka.domain.Cutout
import app.stikka.domain.Decor
import app.stikka.domain.DecorEditor
import app.stikka.domain.DecorRenderer
import app.stikka.domain.EditTarget
import app.stikka.domain.Images
import app.stikka.domain.MaskEditor
import app.stikka.domain.StickerMath
import app.stikka.domain.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Stage { LOADING, DOWNLOADING_MODEL, CUTTING }

/** Sticker animado en preparación (desde un vídeo o un GIF). */
class AnimSession(val source: app.stikka.domain.AnimSource) {
    var startMs by mutableStateOf(0L)
    var endMs by mutableStateOf(minOf(source.durationMs, 3000L))
    var fps by mutableStateOf(10)
    var square by mutableStateOf(false)
    var removeBg by mutableStateOf(false)
    var decor by mutableStateOf(Decor())
    /** Fotogramas colocados en 512 px (sin fondo si se pidió). */
    var frames by mutableStateOf<List<Bitmap>>(emptyList())
    /** Fotogramas finales con la decoración. */
    var finals by mutableStateOf<List<Bitmap>>(emptyList())
    /** Qué se está haciendo ahora (null = nada). */
    var working by mutableStateOf<String?>(null)
    var progress by mutableStateOf(0f)
    /** Los ajustes han cambiado desde la última vista previa. */
    var dirty by mutableStateOf(true)

    val frameMs get() = 1000 / fps
    val frameCount get() = app.stikka.domain.AnimFrames.times(startMs, endMs, fps).size
}

/** Estado de la pantalla de recorte. */
sealed interface CutState {
    data class Working(val stage: Stage, val original: Bitmap? = null) : CutState

    /**
     * Recorte listo. [base] es el recorte de 512 px sin decorar y [sticker] el resultado final
     * con la decoración aplicada.
     */
    data class Ready(
        val original: Bitmap,
        val mask: FloatArray,
        val base: Bitmap,
        val decor: Decor,
        val sticker: Bitmap,
        val autoCut: Boolean,
        /** Máscaras de cada sujeto si se encontraron varios (vacío si solo uno). */
        val subjects: List<FloatArray> = emptyList(),
        /** Sujeto elegido: -1 = todos. */
        val subjectIndex: Int = -1,
    ) : CutState

    data class NoSubject(val original: Bitmap) : CutState
    data class Failed(val message: String, val original: Bitmap? = null) : CutState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = StikkaApp.instance.store
    private val packStore = StikkaApp.instance.packs
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _packs = MutableStateFlow(packStore.list())
    val packs: StateFlow<List<Pack>> = _packs

    /** Pestaña de inicio: 0 = stickers, 1 = paquetes. */
    var tab by mutableStateOf(0)
    /** Stickers seleccionados con pulsación larga. */
    var selection by mutableStateOf<Set<String>>(emptySet())
        private set
    var openPackId by mutableStateOf<String?>(null)
        private set
    /** Paquete al que se están añadiendo stickers (pantalla de selección). */
    var pickerPackId by mutableStateOf<String?>(null)
        private set
    /** ¿Está cada paquete añadido en WhatsApp? (null = no se sabe) */
    var addedStatus by mutableStateOf<Map<String, Boolean?>>(emptyMap())
        private set
    var whatsappInstalled by mutableStateOf(true)
        private set
    var viewingMeta by mutableStateOf(StickerMeta())
        private set

    // Importación desde WhatsApp
    var importOpen by mutableStateOf(false)
        private set
    var importTree by mutableStateOf(prefs.getString("wa_tree", null)?.let { Uri.parse(it) })
        private set
    var importFiles by mutableStateOf<List<WaFile>?>(null)
        private set
    var importLoading by mutableStateOf(false)
        private set
    var importSelected by mutableStateOf<Set<String>>(emptySet())
        private set
    var importedOrigins by mutableStateOf<Set<String>>(emptySet())
        private set
    var importProgress by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    val settings = StikkaApp.instance.settings
    private val backup = Backup(app, store, packStore)

    var settingsOpen by mutableStateOf(false)
    /** Datos extra de cada sticker (emojis, tipo). */
    var metas by mutableStateOf<Map<String, StickerMeta>>(emptyMap())
        private set
    /** Filtro de la biblioteca: 0 todos, 1 estáticos, 2 animados, 3 sin paquete. */
    var filter by mutableStateOf(0)
    /** Búsqueda por emoji. */
    var query by mutableStateOf("")
    /** Sticker recién guardado: se ofrece añadirlo a un paquete. */
    var justSaved by mutableStateOf<String?>(null)
    var update by mutableStateOf<Release?>(null)
        private set
    var checkingUpdate by mutableStateOf(false)
        private set

    /** Stickers marcados como animados (para mostrarlo y no mezclarlos en paquetes). */
    var animatedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Stickers que no han cabido en un paquete (máx. 30): se ofrece crear otro con ellos. */
    var overflow by mutableStateOf<Pair<String, List<String>>?>(null)
        private set

    /**
     * Las versiones anteriores tomaban por animados stickers de WhatsApp de un solo fotograma.
     * Se revisan, se pasan a estático (WebP normal + base para decorar) y se corrigen los paquetes.
     */
    private fun fixStaticMarkedAsAnimated() {
        if (prefs.getBoolean("fix_anim_v1", false)) return
        store.list().forEach { s ->
            val meta = store.loadMeta(s)
            if (!meta.animated) return@forEach
            val bytes = runCatching { s.file.readBytes() }.getOrNull() ?: return@forEach
            if (!WebpInfo.isAnimated(bytes)) runCatching { convertToStatic(s, bytes) }
        }
        packStore.recomputeTypes { id -> store.get(id)?.let { store.loadMeta(it).animated } ?: false }
        prefs.edit().putBoolean("fix_anim_v1", true).apply()
    }

    /** Convierte un sticker en estático: primer fotograma como WebP normal y base para poder decorarlo. */
    private fun convertToStatic(sticker: Sticker, bytes: ByteArray) {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.copy(Bitmap.Config.ARGB_8888, false) ?: return
        val base = Images.fitTo512(bmp)
        store.write(sticker, Images.encodeWebp(base), basePng = Images.encodePng(base))
        store.saveMeta(sticker, store.loadMeta(sticker).copy(animated = false))
    }

    /** El usuario corrige el tipo de un sticker. */
    fun setStickerType(sticker: Sticker, animated: Boolean) {
        viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) {
                if (!animated) {
                    val bytes = runCatching { sticker.file.readBytes() }.getOrNull()
                    if (bytes != null && WebpInfo.isAnimated(bytes)) {
                        // Animación real que se quiere estática: se queda con el primer fotograma.
                        convertToStatic(sticker, bytes)
                    } else {
                        store.saveMeta(sticker, store.loadMeta(sticker).copy(animated = false))
                        if (!sticker.baseFile.exists() && bytes != null) convertToStatic(sticker, bytes)
                    }
                } else {
                    store.saveMeta(sticker, store.loadMeta(sticker).copy(animated = true))
                }
                packStore.recomputeTypes { id -> store.get(id)?.let { store.loadMeta(it).animated } ?: false }
                store.loadMeta(sticker)
            }
            viewingMeta = meta
            viewing = store.get(sticker.id)
            stickerChanged(sticker.id, deleted = false)
            refresh()
            message = if (animated) tr("Marcado como animado", "Marked as animated") else tr("Marcado como estático", "Marked as static")
        }
    }

    val lastPublisher: String get() = prefs.getString("publisher", null).orEmpty()

    private val _stickers = MutableStateFlow(store.list())
    val stickers: StateFlow<List<Sticker>> = _stickers

    var cut by mutableStateOf<CutState?>(null)
        private set
    var viewing by mutableStateOf<Sticker?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    var editor by mutableStateOf<MaskEditor?>(null)
        private set
    var decorator by mutableStateOf<DecorEditor?>(null)
        private set

    private var job: Job? = null

    var anim by mutableStateOf<AnimSession?>(null)
        private set

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { fixStaticMarkedAsAnimated() }
            refresh()
            // Comprobación automática de versiones nuevas, como mucho una vez al día.
            if (settings.autoUpdateCheck && System.currentTimeMillis() - settings.lastUpdateCheck > 24 * 3600 * 1000L) {
                checkUpdates(manual = false)
            }
        }
    }

    // ---------- Sticker animado (vídeo o GIF) ----------

    fun startAnimated(uri: Uri) {
        job?.cancel()
        viewing = null
        cut = null
        viewModelScope.launch {
            val src = withContext(Dispatchers.IO) { runCatching { app.stikka.domain.AnimFrames.open(getApplication(), uri) }.getOrNull() }
            if (src == null) {
                message = tr("No se puede leer ese vídeo o GIF", "That video or GIF can't be read")
                return@launch
            }
            anim = AnimSession(src)
            generateAnim()
        }
    }

    /** Saca los fotogramas con los ajustes actuales y prepara la vista previa. */
    fun generateAnim() {
        val a = anim ?: return
        job?.cancel()
        job = viewModelScope.launch {
            val ctx: Context = getApplication()
            try {
                a.working = tr("Sacando fotogramas…", "Extracting frames…")
                a.progress = 0f
                val times = app.stikka.domain.AnimFrames.times(a.startMs, a.endMs, a.fps)
                var frames = withContext(Dispatchers.IO) {
                    app.stikka.domain.AnimFrames.extract(ctx, a.source, times, a.square) { p -> a.progress = p }
                }
                if (frames.isEmpty()) throw IllegalStateException("no frames")
                if (a.removeBg) {
                    a.working = tr("Quitando el fondo…", "Removing the background…")
                    a.progress = 0f
                    Cutout.ensureModel(ctx) { a.working = tr("Preparando el recorte inteligente…", "Getting smart cutout ready…") }
                    a.working = tr("Quitando el fondo…", "Removing the background…")
                    val done = ArrayList<Bitmap>(frames.size)
                    frames.forEachIndexed { i, f ->
                        done += app.stikka.domain.AnimFrames.removeBackground(f)
                        a.progress = (i + 1f) / frames.size
                    }
                    frames = done
                }
                a.frames = frames
                renderAnimFinals(a)
                a.dirty = false
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                message = tr("No se han podido sacar los fotogramas", "Couldn't extract the frames")
            } finally {
                a.working = null
            }
        }
    }

    private suspend fun renderAnimFinals(a: AnimSession) {
        a.working = tr("Aplicando la decoración…", "Applying decoration…")
        a.finals = withContext(Dispatchers.Default) { a.frames.map { DecorRenderer.render(it, a.decor) } }
    }

    fun decorateAnim() {
        val a = anim ?: return
        val first = a.frames.firstOrNull() ?: return
        decorator = DecorEditor(first, a.decor, EditTarget.Animated)
    }

    fun closeAnim() {
        job?.cancel()
        anim = null
    }

    fun saveAnim() {
        val a = anim ?: return
        if (saving || a.finals.isEmpty()) return
        saving = true
        viewModelScope.launch {
            try {
                a.working = tr("Creando el sticker animado…", "Creating the animated sticker…")
                val bytes = withContext(Dispatchers.Default) {
                    app.stikka.domain.AnimFrames.encode(a.finals, a.frameMs) { p -> a.progress = p }
                }
                val saved = withContext(Dispatchers.IO) {
                    store.save(bytes, null, a.decor.toJson(), meta = StickerMeta(animated = true))
                }
                anim = null
                refresh()
                justSaved = saved.id
            } catch (e: Throwable) {
                message = tr("No se ha podido crear el sticker animado", "Couldn't create the animated sticker")
            } finally {
                a.working = null
                saving = false
            }
        }
    }

    // ---------- Nuevo sticker ----------

    fun startCutout(uri: Uri) {
        job?.cancel()
        viewing = null
        editor = null
        decorator = null
        cut = CutState.Working(Stage.LOADING)
        job = viewModelScope.launch {
            var original: Bitmap? = null
            try {
                original = withContext(Dispatchers.IO) { Images.load(getApplication(), uri) }
                cut = CutState.Working(Stage.CUTTING, original)
                Cutout.ensureModel(getApplication()) { cut = CutState.Working(Stage.DOWNLOADING_MODEL, original) }
                cut = CutState.Working(Stage.CUTTING, original)
                val seg = Cutout.segment(original)
                val mask = Images.confidenceToAlpha(seg.mask)
                if (StickerMath.coverage(mask) < 0.002f) {
                    cut = CutState.NoSubject(original)
                    return@launch
                }
                val subjects = withContext(Dispatchers.Default) { seg.subjects.map { Images.confidenceToAlpha(it) } }
                cut = build(original, mask, autoCut = true, decor = Decor()).copy(subjects = subjects)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                cut = CutState.Failed(
                    if (original == null) {
                        tr("No se ha podido abrir la imagen.", "The image couldn't be opened.")
                    } else {
                        tr(
                            "No se ha podido hacer el recorte. La primera vez necesitas conexión para descargar el modelo, y Google Play Services debe estar al día. También puedes usar la foto entera y recortarla tú con el lazo o el rectángulo.",
                            "The cutout failed. The first time you need a connection to download the model, and Google Play Services must be up to date. You can also use the whole photo and cut it yourself with the lasso or rectangle.",
                        )
                    },
                    original,
                )
            }
        }
    }

    /** Elige uno de los sujetos encontrados (-1 = todos juntos). */
    fun selectSubject(index: Int) {
        val ready = cut as? CutState.Ready ?: return
        if (ready.subjects.isEmpty()) return
        job?.cancel()
        job = viewModelScope.launch {
            val all = if (index < 0) {
                withContext(Dispatchers.Default) {
                    FloatArray(ready.mask.size) { i -> ready.subjects.maxOf { it[i] } }
                }
            } else ready.subjects[index]
            cut = build(ready.original, all, autoCut = true, decor = ready.decor)
                .copy(subjects = ready.subjects, subjectIndex = index)
        }
    }

    /** Usa la imagen completa, sin recortar. */
    fun useWholeImage() {
        val (original, decor) = when (val c = cut) {
            is CutState.NoSubject -> c.original to Decor()
            is CutState.Failed -> (c.original ?: return) to Decor()
            is CutState.Ready -> c.original to c.decor
            else -> return
        }
        job?.cancel()
        job = viewModelScope.launch {
            cut = build(original, Images.fullMask(original), autoCut = false, decor = decor)
        }
    }

    private suspend fun build(original: Bitmap, mask: FloatArray, autoCut: Boolean, decor: Decor): CutState.Ready =
        withContext(Dispatchers.Default) {
            val base = Images.renderSticker(Images.applyMask(original, mask), mask)
            CutState.Ready(original, mask, base, decor, DecorRenderer.render(base, decor), autoCut)
        }

    fun saveCut() {
        val ready = cut as? CutState.Ready ?: return
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    store.save(
                        webp = Images.encodeWebp(ready.sticker),
                        basePng = Images.encodePng(ready.base),
                        decorJson = ready.decor.toJson(),
                        sourceJpeg = Images.encodeJpeg(ready.original),
                        maskPng = Images.encodeMask(ready.mask, ready.original.width, ready.original.height),
                    )
                }
                refresh()
                cut = null
                justSaved = saved.id
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

    // ---------- Editor de recorte ----------

    fun editCut() {
        val ready = cut as? CutState.Ready ?: return
        editor = MaskEditor(ready.original, ready.mask, EditTarget.NewCut)
    }

    fun editSticker(sticker: Sticker) {
        if (!sticker.editable || saving) return
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                    val src = BitmapFactory.decodeFile(sticker.sourceFile.absolutePath, opts)!!
                    val maskBmp = BitmapFactory.decodeFile(sticker.maskFile.absolutePath, opts)!!
                    src to Images.decodeMask(maskBmp, src.width, src.height)
                }.getOrNull()
            }
            if (loaded == null) {
                message = tr("No se puede retocar este sticker", "This sticker can't be edited")
            } else {
                editor = MaskEditor(loaded.first, loaded.second, EditTarget.Saved(sticker.id))
            }
        }
    }

    fun closeEditor() { editor = null }

    fun finishEditor() {
        val e = editor ?: return
        if (saving) return
        val mask = e.maskFloats()
        when (val t = e.target) {
            EditTarget.Animated -> editor = null
            EditTarget.NewCut -> viewModelScope.launch {
                val decor = (cut as? CutState.Ready)?.decor ?: Decor()
                cut = build(e.source, mask, autoCut = true, decor = decor)
                editor = null
            }
            is EditTarget.Saved -> persist(
                ok = tr("Recorte actualizado", "Cutout updated"),
                done = { editor = null },
            ) {
                val sticker = store.get(t.stickerId) ?: return@persist
                changedStickerId = sticker.id
                val base = Images.renderSticker(Images.applyMask(e.source, mask), mask)
                val decor = store.loadDecor(sticker)
                store.write(
                    sticker,
                    webp = Images.encodeWebp(DecorRenderer.render(base, decor)),
                    basePng = Images.encodePng(base),
                    maskPng = Images.encodeMask(mask, e.width, e.height),
                )
            }
        }
    }

    // ---------- Decoración (textos, emojis, borde) ----------

    fun decorateCut() {
        val ready = cut as? CutState.Ready ?: return
        decorator = DecorEditor(ready.base, ready.decor, EditTarget.NewCut)
    }

    fun decorateSticker(sticker: Sticker) {
        if (saving) return
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { store.loadBase(sticker)!! to store.loadDecor(sticker) }.getOrNull()
            }
            if (loaded == null) {
                message = tr("No se puede abrir este sticker", "This sticker can't be opened")
            } else {
                decorator = DecorEditor(loaded.first, loaded.second, EditTarget.Saved(sticker.id))
            }
        }
    }

    fun closeDecorator() { decorator = null }

    fun finishDecorator() {
        val d = decorator ?: return
        if (saving) return
        val decor = d.decor
        when (val t = d.target) {
            EditTarget.Animated -> viewModelScope.launch {
                anim?.let { a ->
                    a.decor = decor
                    decorator = null
                    try { renderAnimFinals(a) } finally { a.working = null }
                } ?: run { decorator = null }
            }
            EditTarget.NewCut -> viewModelScope.launch {
                val ready = cut as? CutState.Ready
                if (ready != null) {
                    val sticker = withContext(Dispatchers.Default) { DecorRenderer.render(ready.base, decor) }
                    cut = ready.copy(decor = decor, sticker = sticker)
                }
                decorator = null
            }
            is EditTarget.Saved -> persist(
                ok = tr("Sticker actualizado", "Sticker updated"),
                done = { decorator = null },
            ) {
                val sticker = store.get(t.stickerId) ?: return@persist
                changedStickerId = sticker.id
                store.write(
                    sticker,
                    webp = Images.encodeWebp(DecorRenderer.render(d.base, decor)),
                    basePng = if (sticker.baseFile.exists()) null else Images.encodePng(d.base),
                    decorJson = decor.toJson(),
                )
            }
        }
    }

    private var changedStickerId: String? = null

    /** Guarda cambios de un sticker existente en segundo plano y refresca la vista. */
    private fun persist(ok: String, done: () -> Unit, work: suspend () -> Unit) {
        saving = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) { work() }
                changedStickerId?.let { stickerChanged(it, deleted = false) }
                changedStickerId = null
                refresh()
                viewing = viewing?.let { store.get(it.id) }
                done()
                message = ok
            } catch (x: Throwable) {
                message = tr("No se ha podido guardar", "Couldn't save it")
            } finally {
                saving = false
            }
        }
    }

    // ---------- Biblioteca ----------

    fun open(sticker: Sticker) {
        viewing = sticker
        viewingMeta = store.loadMeta(sticker)
    }
    fun closeViewer() { viewing = null }

    fun delete(sticker: Sticker) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(sticker) }
            if (viewing?.id == sticker.id) viewing = null
            stickerChanged(sticker.id, deleted = true)
            refresh()
            message = tr("Sticker eliminado", "Sticker deleted")
        }
    }

    private suspend fun refresh() {
        _stickers.value = withContext(Dispatchers.IO) { store.list() }
        _packs.value = withContext(Dispatchers.IO) { packStore.list() }
        metas = withContext(Dispatchers.IO) { _stickers.value.associate { it.id to store.loadMeta(it) } }
        animatedIds = metas.filterValues { it.animated }.keys
    }

    private suspend fun stickerChanged(id: String, deleted: Boolean) {
        withContext(Dispatchers.IO) { packStore.onStickerChanged(id, deleted) }
        WhatsApp.notifyChanged(getApplication())
    }

    // ---------- Biblioteca: filtros, duplicar ----------

    /** Stickers que se ven en la biblioteca según el filtro y la búsqueda. */
    fun visible(all: List<Sticker>, packs: List<Pack>): List<Sticker> {
        val inAnyPack = packs.flatMap { it.stickerIds }.toSet()
        val q = query.trim()
        return all.filter { s ->
            val m = metas[s.id]
            val okFilter = when (filter) {
                1 -> m?.animated != true
                2 -> m?.animated == true
                3 -> s.id !in inAnyPack
                else -> true
            }
            okFilter && (q.isEmpty() || m?.emojis?.any { it.contains(q) || q.contains(it) } == true)
        }
    }

    fun duplicate(sticker: Sticker) {
        viewModelScope.launch {
            val copy = withContext(Dispatchers.IO) { store.duplicate(sticker) }
            refresh()
            open(copy)
            message = tr("Copia creada: ahora puedes cambiarla", "Copy created: now you can change it")
        }
    }

    // ---------- Selección múltiple ----------

    fun toggleSelect(id: String) {
        selection = if (id in selection) selection - id else selection + id
    }

    fun clearSelection() { selection = emptySet() }

    fun deleteSelected() {
        val ids = selection
        selection = emptySet()
        viewModelScope.launch {
            ids.forEach { id ->
                withContext(Dispatchers.IO) { store.get(id)?.let { store.delete(it) } }
                stickerChanged(id, deleted = true)
            }
            refresh()
            message = tr("${ids.size} stickers eliminados", "${ids.size} stickers deleted")
        }
    }

    // ---------- Emojis para WhatsApp ----------

    fun setEmojis(sticker: Sticker, emojis: List<String>) {
        viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) {
                val m = store.loadMeta(sticker).copy(emojis = emojis.take(3))
                store.saveMeta(sticker, m)
                m
            }
            viewingMeta = meta
            stickerChanged(sticker.id, deleted = false)
            refresh()
        }
    }

    // ---------- Paquetes ----------

    fun openPack(id: String) {
        openPackId = id
        refreshAdded()
    }

    fun closePack() { openPackId = null }

    /** Crea un paquete (opcionalmente con stickers) y lo abre. */
    fun createPack(name: String, publisher: String, stickerIds: Collection<String> = emptyList()) {
        if (_packs.value.size >= Pack.MAX_PACKS) {
            message = tr("WhatsApp admite como máximo ${Pack.MAX_PACKS} paquetes por app", "WhatsApp allows up to ${Pack.MAX_PACKS} packs per app")
            return
        }
        prefs.edit().putString("publisher", publisher).apply()
        viewModelScope.launch {
            val pack = withContext(Dispatchers.IO) { packStore.create(name.trim(), publisher.trim()) }
            refresh()
            if (stickerIds.isNotEmpty()) addToPack(pack.id, stickerIds) else {
                tab = 1
                openPack(pack.id)
            }
        }
    }

    fun renamePack(id: String, name: String, publisher: String) {
        prefs.edit().putString("publisher", publisher).apply()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { packStore.update(id) { it.copy(name = name.trim().take(128), publisher = publisher.trim().take(128)) } }
            WhatsApp.notifyChanged(getApplication())
            refresh()
        }
    }

    fun deletePack(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { packStore.delete(id) }
            if (openPackId == id) openPackId = null
            WhatsApp.notifyChanged(getApplication())
            refresh()
            message = tr(
                "Paquete eliminado. Si estaba en WhatsApp, quítalo también desde allí.",
                "Pack deleted. If it was in WhatsApp, remove it there too.",
            )
        }
    }

    /** Añade stickers a un paquete respetando el máximo (30) y sin mezclar animados y estáticos. */
    fun addToPack(packId: String, ids: Collection<String>) {
        selection = emptySet()
        pickerPackId = null
        viewModelScope.launch {
            var skippedType = 0
            var added = 0
            var rest: List<String> = emptyList()
            withContext(Dispatchers.IO) {
                val pack = packStore.get(packId) ?: return@withContext
                fun isAnim(id: String) = store.get(id)?.let { store.loadMeta(it).animated }
                val valid = ids.filter { it !in pack.stickerIds && isAnim(it) != null }
                // El tipo lo deciden los stickers que ya tiene el paquete (o el primero que se añade).
                val packType = pack.stickerIds.firstOrNull()?.let { isAnim(it) } ?: valid.firstOrNull()?.let { isAnim(it) } ?: false
                val sameType = valid.filter { isAnim(it) == packType }
                skippedType = valid.size - sameType.size
                val room = (Pack.MAX_STICKERS - pack.count).coerceAtLeast(0)
                val toAdd = sameType.take(room)
                rest = sameType.drop(room)
                added = toAdd.size
                packStore.update(packId) { it.copy(stickerIds = it.stickerIds + toAdd, animated = packType) }
            }
            WhatsApp.notifyChanged(getApplication())
            refresh()
            tab = 1
            openPack(packId)
            message = buildString {
                append(tr("$added añadidos", "$added added"))
                if (skippedType > 0) append(
                    tr(
                        " · $skippedType son de otro tipo (animado/estático). Si alguno está mal marcado, cámbialo desde el sticker.",
                        " · $skippedType are of the other type (animated/static). If one is wrong, change it from the sticker.",
                    )
                )
            }
            if (rest.isNotEmpty()) overflow = packId to rest
        }
    }

    /** Crea un paquete de continuación con los stickers que no cupieron. */
    fun acceptOverflow() {
        val (packId, rest) = overflow ?: return
        overflow = null
        val pack = _packs.value.firstOrNull { it.id == packId } ?: return
        createPack(nextPackName(pack.name), pack.publisher, rest)
    }

    fun dismissOverflow() { overflow = null }

    private fun nextPackName(name: String): String {
        val m = Regex("^(.*) (\\d+)$").find(name)
        val (stem, n) = if (m != null) m.groupValues[1] to (m.groupValues[2].toInt() + 1) else name to 2
        var candidate = "$stem $n"
        var k = n
        while (_packs.value.any { it.name == candidate }) { k++; candidate = "$stem $k" }
        return candidate
    }

    /** Nuevo orden de los stickers de un paquete. */
    fun reorderPack(packId: String, order: List<String>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                packStore.update(packId) { p ->
                    val first = order.filter { it in p.stickerIds }
                    p.copy(stickerIds = first + p.stickerIds.filter { it !in first })
                }
            }
            WhatsApp.notifyChanged(getApplication())
            refresh()
            message = tr("Orden guardado", "Order saved")
        }
    }

    fun removeFromPack(packId: String, stickerId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { packStore.update(packId) { it.copy(stickerIds = it.stickerIds - stickerId) } }
            WhatsApp.notifyChanged(getApplication())
            refresh()
        }
    }

    fun setTray(packId: String, stickerId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { packStore.update(packId) { it.copy(trayId = stickerId) } }
            WhatsApp.notifyChanged(getApplication())
            refresh()
            message = tr("Icono del paquete cambiado", "Pack icon changed")
        }
    }

    fun openPicker(packId: String) { pickerPackId = packId }
    fun closePicker() { pickerPackId = null }

    /** Comprueba qué paquetes están ya en WhatsApp. */
    fun refreshAdded() {
        viewModelScope.launch {
            val ctx: Context = getApplication()
            val result = withContext(Dispatchers.IO) {
                whatsappInstalled = WhatsApp.installed(ctx).isNotEmpty()
                _packs.value.associate { it.id to WhatsApp.isAdded(ctx, it.id) }
            }
            addedStatus = result
        }
    }

    /** Prepara el intent para añadir el paquete a WhatsApp (null si no se puede). */
    fun addToWhatsAppIntent(pack: Pack): Intent? {
        if (!pack.ready) {
            message = tr(
                "Un paquete necesita entre ${Pack.MIN_STICKERS} y ${Pack.MAX_STICKERS} stickers",
                "A pack needs ${Pack.MIN_STICKERS} to ${Pack.MAX_STICKERS} stickers",
            )
            return null
        }
        packStore.ensureTray(pack)
        val intent = WhatsApp.addIntent(getApplication(), pack)
        if (intent == null) message = tr("WhatsApp no está instalada", "WhatsApp isn't installed")
        return intent
    }

    fun onAddResult(ok: Boolean, validationError: String?) {
        if (validationError != null) {
            message = tr("WhatsApp no ha aceptado el paquete: ", "WhatsApp rejected the pack: ") + validationError
        } else if (ok) {
            message = tr("¡Paquete añadido a WhatsApp!", "Pack added to WhatsApp!")
        }
        refreshAdded()
    }

    // ---------- Actualizaciones ----------

    fun checkUpdates(manual: Boolean) {
        if (checkingUpdate) return
        checkingUpdate = true
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { Updates.latest() }
            checkingUpdate = false
            settings.lastUpdateCheck = System.currentTimeMillis()
            when {
                r == null -> if (manual) message = tr("No se ha podido comprobar (¿sin conexión?)", "Couldn't check (offline?)")
                Updates.isNewer(r.version, BuildConfig.VERSION_NAME) -> update = r
                manual -> message = tr("Tienes la última versión (${BuildConfig.VERSION_NAME})", "You're up to date (${BuildConfig.VERSION_NAME})")
            }
        }
    }

    fun dismissUpdate() { update = null }

    // ---------- Copias de seguridad y paquetes compartidos ----------

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)!!.use { backup.export(it) }
                }.isSuccess
            }
            message = if (ok) tr("Copia guardada", "Backup saved") else tr("No se ha podido guardar la copia", "Couldn't save the backup")
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)!!.use { backup.import(it) }
                }.getOrNull()
            }
            if (r == null) {
                message = tr("Ese archivo no es una copia ni un paquete de Stikka", "That file isn't a Stikka backup or pack")
                return@launch
            }
            WhatsApp.notifyChanged(getApplication())
            refresh()
            message = buildString {
                append(tr("Importados ${r.stickers} stickers y ${r.packs} paquetes", "Imported ${r.stickers} stickers and ${r.packs} packs"))
                if (r.skippedPacks > 0) append(tr(" · ${r.skippedPacks} paquetes ya existían o no caben", " · ${r.skippedPacks} packs already existed or don't fit"))
            }
        }
    }

    /** Crea el archivo .stikka de un paquete para compartirlo. */
    suspend fun packFile(pack: Pack): java.io.File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = java.io.File(getApplication<Application>().cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val safe = pack.name.replace(Regex("[^A-Za-z0-9áéíóúñÁÉÍÓÚÑ _-]"), "").trim().ifEmpty { "paquete" }
            val f = java.io.File(dir, "$safe.stikka")
            f.outputStream().use { backup.export(it, setOf(pack.id)) }
            f
        }.getOrNull()
    }

    // ---------- Importar desde WhatsApp ----------

    fun openImport() {
        importOpen = true
        importSelected = emptySet()
        importTree?.let { loadImport(it) }
    }

    fun closeImport() {
        importOpen = false
        importFiles = null
        importSelected = emptySet()
    }

    fun chooseImportFolder(uri: Uri) {
        val ctx: Context = getApplication()
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        prefs.edit().putString("wa_tree", uri.toString()).apply()
        importTree = uri
        loadImport(uri)
    }

    private fun loadImport(tree: Uri) {
        importLoading = true
        viewModelScope.launch {
            val ctx: Context = getApplication()
            val files = withContext(Dispatchers.IO) { runCatching { WhatsAppImport.list(ctx, tree) }.getOrNull() }
            importedOrigins = withContext(Dispatchers.IO) { store.importedOrigins() }
            importLoading = false
            if (files == null) {
                importTree = null
                prefs.edit().remove("wa_tree").apply()
                message = tr("No se puede leer esa carpeta. Elígela de nuevo.", "That folder can't be read. Pick it again.")
            }
            importFiles = files
        }
    }

    fun toggleImport(name: String) {
        importSelected = if (name in importSelected) importSelected - name else importSelected + name
    }

    fun selectAllImport(all: Boolean) {
        importSelected = if (all) (importFiles.orEmpty().map { it.name } - importedOrigins).toSet() else emptySet()
    }

    fun importSelectedFiles() {
        val files = importFiles.orEmpty().filter { it.name in importSelected }
        if (files.isEmpty() || importProgress != null) return
        viewModelScope.launch {
            val ctx: Context = getApplication()
            var ok = 0
            importProgress = 0 to files.size
            files.forEachIndexed { i, f ->
                val done = withContext(Dispatchers.Default) { runCatching { importOne(ctx, f) }.getOrDefault(false) }
                if (done) ok++
                importProgress = (i + 1) to files.size
            }
            importProgress = null
            importSelected = emptySet()
            importedOrigins = withContext(Dispatchers.IO) { store.importedOrigins() }
            refresh()
            message = tr("$ok stickers importados", "$ok stickers imported")
        }
    }

    private fun importOne(ctx: Context, f: WaFile): Boolean {
        val bytes = WhatsAppImport.readBytes(ctx, f) ?: return false
        val animated = WebpInfo.isAnimated(bytes)
        if (animated) {
            if (bytes.size > 500 * 1024) return false
            store.save(bytes, null, Decor().toJson(), meta = StickerMeta(animated = true, origin = f.name))
            return true
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.copy(Bitmap.Config.ARGB_8888, false) ?: return false
        val base = Images.fitTo512(bmp)
        val webp = if (WebpInfo.isWebp(bytes) && bmp.width == 512 && bmp.height == 512 && bytes.size <= 100 * 1024) bytes
        else Images.encodeWebp(base)
        store.save(webp, Images.encodePng(base), Decor().toJson(), meta = StickerMeta(origin = f.name))
        return true
    }
}
