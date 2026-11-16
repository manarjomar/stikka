package app.stikka.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BorderOuter
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stikka.domain.DecorEditor
import app.stikka.domain.DecorRenderer
import app.stikka.domain.Layer
import app.stikka.domain.LayerFont
import app.stikka.domain.LayerKind
import app.stikka.domain.tr

private val TextColors = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFE0336F, 0xFFFF8A3D, 0xFFFFD54F, 0xFF66BB6A,
    0xFF29B6F6, 0xFF5C6BC0, 0xFFAB47BC, 0xFFEF5350, 0xFF8D6E63, 0xFF78909C,
).map { it.toInt() }

private val TextTemplates: List<String>
    get() = if (tr("es", "en") == "es") listOf(
        "JAJAJA", "OK", "¡Buenos días!", "Buenas noches", "Gracias", "Te quiero", "¡Feliz cumple!",
        "¿Qué?", "Sí", "No", "Jo", "¡Olé!", "Ánimo", "Lo siento", "¡Vamos!",
    ) else listOf(
        "LOL", "OK", "Good morning!", "Good night", "Thanks", "Love you", "Happy birthday!",
        "What?", "Yes", "No", "Oops", "Wow!", "You got this", "Sorry", "Let's go!",
    )

private val OutlineColors = listOf(0xFFFFFFFF, 0xFF000000, 0xFFE0336F, 0xFFFFD54F, 0xFF29B6F6).map { it.toInt() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecorScreen(
    editor: DecorEditor,
    saving: Boolean,
    onClose: () -> Unit,
    onDone: () -> Unit,
) {
    var textDialog by remember { mutableStateOf<Layer?>(null) }
    var newText by remember { mutableStateOf(false) }
    var emojiSheet by remember { mutableStateOf<Long?>(null) } // -1 = nuevo
    val selected = editor.selected
    val selColor = MaterialTheme.colorScheme.primary.toArgb()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(tr("Decorar", "Decorate")) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, tr("Cancelar", "Cancel")) }
                },
                actions = {
                    if (saving) {
                        CircularProgressIndicator(Modifier.padding(end = 16.dp).size(24.dp), strokeWidth = 2.5.dp)
                    } else {
                        FilledTonalButton(onClick = onDone, modifier = Modifier.padding(end = 8.dp)) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Listo", "Done"))
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                        .clip(MaterialTheme.shapes.large)
                        .checkerboard()
                        .pointerInput(editor) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val k = size.width / 512f
                                val hit = editor.hitTest(down.position.x / k, down.position.y / k)
                                editor.selectedId = hit
                                if (hit != null) {
                                    editor.bringToFront(hit)
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.none { it.pressed }) break
                                        val pan = event.calculatePan()
                                        val zoom = event.calculateZoom()
                                        val rot = event.calculateRotation()
                                        editor.update(hit) {
                                            it.copy(
                                                x = (it.x + pan.x / k).coerceIn(0f, 512f),
                                                y = (it.y + pan.y / k).coerceIn(0f, 512f),
                                                scale = (it.scale * zoom).coerceIn(0.2f, 6f),
                                                rotation = it.rotation + rot,
                                            )
                                        }
                                        event.changes.forEach { c -> c.consume() }
                                    }
                                }
                            }
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val k = size.width / 512f
                        val decor = editor.decor
                        val sel = editor.selectedId
                        drawIntoCanvas { c ->
                            val nc = c.nativeCanvas
                            nc.save()
                            nc.scale(k, k)
                            nc.drawBitmap(editor.outlinedBase(), 0f, 0f, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                            decor.layers.forEach { DecorRenderer.drawLayer(nc, it) }
                            decor.layers.firstOrNull { it.id == sel }?.let { l ->
                                val b = DecorRenderer.localBounds(l)
                                nc.save()
                                nc.translate(l.x, l.y)
                                nc.rotate(l.rotation)
                                nc.scale(l.scale, l.scale)
                                val w = 2.5f / k / l.scale
                                val white = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                    style = android.graphics.Paint.Style.STROKE
                                    strokeWidth = w * 2.2f
                                    color = android.graphics.Color.WHITE
                                }
                                val dash = android.graphics.Paint(white).apply {
                                    strokeWidth = w
                                    color = selColor
                                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f * w, 6f * w), 0f)
                                }
                                val r = 10f / l.scale
                                nc.drawRoundRect(b, r, r, white)
                                nc.drawRoundRect(b, r, r, dash)
                                nc.restore()
                            }
                            nc.restore()
                        }
                    }
                }
            }

            DecorPanel(
                editor = editor,
                selected = selected,
                onAddText = { newText = true },
                onAddEmoji = { emojiSheet = -1L },
                onEditText = { textDialog = it },
                onChangeEmoji = { emojiSheet = it.id },
            )
        }
    }

    if (newText || textDialog != null) {
        TextDialog(
            initial = textDialog?.text.orEmpty(),
            onDismiss = { newText = false; textDialog = null },
            onConfirm = { text ->
                val editing = textDialog
                if (editing != null) editor.update(editing.id) { it.copy(text = text) } else editor.addText(text)
                newText = false
                textDialog = null
            },
        )
    }

    emojiSheet?.let { target ->
        EmojiSheet(
            onDismiss = { emojiSheet = null },
            onPick = { e ->
                if (target == -1L) editor.addEmoji(e) else editor.update(target) { it.copy(text = e) }
                emojiSheet = null
            },
        )
    }
}

@Composable
private fun DecorPanel(
    editor: DecorEditor,
    selected: Layer?,
    onAddText: () -> Unit,
    onAddEmoji: () -> Unit,
    onEditText: (Layer) -> Unit,
    onChangeEmoji: (Layer) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large.copy(
            bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
            bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selected == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    BigAction(Icons.Rounded.TextFields, tr("Texto", "Text"), Modifier.weight(1f), onAddText)
                    BigAction(Icons.Rounded.EmojiEmotions, tr("Emoji", "Emoji"), Modifier.weight(1f), onAddEmoji)
                }
                Text(tr("Recorte", "Cutout"), style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { editor.setBase(rotation = editor.decor.baseRotation - 90) }) {
                        Icon(Icons.Rounded.RotateLeft, tr("Girar a la izquierda", "Rotate left"))
                    }
                    IconButton(onClick = { editor.setBase(rotation = editor.decor.baseRotation + 90) }) {
                        Icon(Icons.Rounded.RotateRight, tr("Girar a la derecha", "Rotate right"))
                    }
                    androidx.compose.material3.FilledIconToggleButton(
                        checked = editor.decor.baseFlip,
                        onCheckedChange = { editor.setBase(flip = it) },
                    ) { Icon(Icons.Rounded.Flip, tr("Espejo", "Mirror")) }
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = editor.decor.shadow,
                        onClick = { editor.setBase(shadow = !editor.decor.shadow) },
                        label = { Text(tr("Sombra", "Shadow")) },
                    )
                }
                LabeledSlider(tr("Tamaño", "Size"), editor.decor.baseScale, 0.5f..1f) { editor.setBase(scale = it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.BorderOuter, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(tr("Borde alrededor", "Outline"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(checked = editor.decor.outline, onCheckedChange = { editor.setOutline(enabled = it) })
                }
                if (editor.decor.outline) {
                    ColorRow(OutlineColors, editor.decor.outlineColor) { editor.setOutline(color = it) }
                    LabeledSlider(
                        tr("Grosor", "Width"),
                        editor.decor.outlineWidth,
                        3f..15f,
                    ) { editor.setOutline(width = it) }
                }
                Text(
                    tr(
                        "Toca un texto o emoji para elegirlo · arrastra para moverlo · dos dedos para girar y escalar",
                        "Tap a text or emoji to select it · drag to move · two fingers to rotate and resize",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (selected.kind == LayerKind.TEXT) tr("Texto", "Text") else tr("Emoji", "Emoji"),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        if (selected.kind == LayerKind.TEXT) onEditText(selected) else onChangeEmoji(selected)
                    }) { Icon(Icons.Rounded.Edit, tr("Cambiar", "Change")) }
                    IconButton(onClick = { editor.duplicate(selected.id) }) { Icon(Icons.Rounded.ContentCopy, tr("Duplicar", "Duplicate")) }
                    IconButton(onClick = { editor.bringToFront(selected.id) }) { Icon(Icons.Rounded.FlipToFront, tr("Delante", "Bring to front")) }
                    IconButton(onClick = { editor.delete(selected.id) }) {
                        Icon(Icons.Rounded.DeleteOutline, tr("Eliminar", "Delete"), tint = MaterialTheme.colorScheme.error)
                    }
                }
                if (selected.kind == LayerKind.TEXT) {
                    ColorRow(TextColors, selected.color) { c -> editor.update(selected.id) { it.copy(color = c) } }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LayerFont.entries.forEach { f ->
                            FilterChip(
                                selected = selected.font == f,
                                onClick = { editor.update(selected.id) { it.copy(font = f) } },
                                label = { Text("Aa", fontFamily = f.preview(), fontWeight = FontWeight.Bold) },
                            )
                        }
                        FilterChip(
                            selected = selected.stroke && !selected.bubble,
                            onClick = { editor.update(selected.id) { it.copy(stroke = !it.stroke, bubble = false) } },
                            label = { Text(tr("Contorno", "Outline")) },
                        )
                        FilterChip(
                            selected = selected.bubble,
                            onClick = { editor.update(selected.id) { it.copy(bubble = !it.bubble) } },
                            label = { Text(tr("Bocadillo", "Bubble")) },
                        )
                    }
                }
                LabeledSlider(tr("Tamaño", "Size"), selected.scale, 0.2f..6f) { v ->
                    editor.update(selected.id) { it.copy(scale = v) }
                }
                LabeledSlider(tr("Giro", "Rotation"), normalized(selected.rotation), -180f..180f) { v ->
                    editor.update(selected.id) { it.copy(rotation = v) }
                }
                TextButton(onClick = { editor.selectedId = null }, modifier = Modifier.align(Alignment.End)) {
                    Text(tr("Hecho con esta capa", "Done with this layer"))
                }
            }
        }
    }
}

private fun normalized(deg: Float): Float {
    var d = deg % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

private fun LayerFont.preview(): FontFamily = when (this) {
    LayerFont.BOLD, LayerFont.CLASSIC -> FontFamily.SansSerif
    LayerFont.SERIF -> FontFamily.Serif
    LayerFont.MONO -> FontFamily.Monospace
    LayerFont.SCRIPT -> FontFamily.Cursive
}

@Composable
private fun BigAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.height(64.dp),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun ColorRow(colors: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { c ->
            val isSel = c == selected
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(
                        width = if (isSel) 3.dp else 1.dp,
                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .clickable { onPick(c) },
            )
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(72.dp))
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TextDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.TextFields, null) },
        title = { Text(if (initial.isEmpty()) tr("Añadir texto", "Add text") else tr("Cambiar texto", "Edit text")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(tr("Escribe aquí…", "Type here…")) },
                    minLines = 1,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(tr("Rápidos", "Quick"), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextTemplates.forEach { t ->
                        androidx.compose.material3.SuggestionChip(onClick = { text = t }, label = { Text(t) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(tr("Aceptar", "OK")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel")) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmojiSheet(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var custom by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(tr("Elige un emoji", "Pick an emoji"), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.take(8) },
                    placeholder = { Text(tr("…o escribe cualquier emoji", "…or type any emoji")) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { onPick(custom.trim()) }, enabled = custom.isNotBlank()) {
                    Text(tr("Usar", "Use"))
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(52.dp),
                modifier = Modifier.fillMaxWidth().height(380.dp),
            ) {
                items(EmojiList) { e ->
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onPick(e) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(e, fontSize = 30.sp, modifier = Modifier.graphicsLayer { })
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}
