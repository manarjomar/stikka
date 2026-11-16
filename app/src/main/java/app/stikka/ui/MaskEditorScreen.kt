package app.stikka.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.AutoFixOff
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.stikka.domain.MaskEditor
import app.stikka.domain.ShapeOp
import app.stikka.domain.Tool
import app.stikka.domain.tr
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaskEditorScreen(
    editor: MaskEditor,
    saving: Boolean,
    onClose: () -> Unit,
    onDone: () -> Unit,
) {
    val density = LocalDensity.current
    val image = remember(editor) { editor.composite.asImageBitmap() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var lens by remember { mutableStateOf<Offset?>(null) }
    var shape by remember { mutableStateOf<List<Offset>>(emptyList()) }

    val checkA = MaterialTheme.colorScheme.surfaceContainerHighest
    val checkB = MaterialTheme.colorScheme.surfaceContainerLow
    val brushColor = MaterialTheme.colorScheme.primary
    val lensRing = MaterialTheme.colorScheme.primary

    fun baseScale(): Float =
        if (canvasSize.width == 0) 1f
        else min(canvasSize.width / editor.width.toFloat(), canvasSize.height / editor.height.toFloat()) * 0.94f

    fun scale() = baseScale() * zoom
    fun origin(): Offset {
        val k = scale()
        return Offset(
            (canvasSize.width - editor.width * k) / 2f + pan.x,
            (canvasSize.height - editor.height * k) / 2f + pan.y,
        )
    }
    fun toImage(p: Offset): Offset {
        val o = origin()
        val k = scale()
        return Offset((p.x - o.x) / k, (p.y - o.y) / k)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(tr("Retocar recorte", "Fine-tune cutout")) },
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
                    .clipToBounds()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(editor) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var transforming = false
                            var last = down.position
                            val tool = editor.tool
                            if (tool == Tool.BRUSH) {
                                editor.beginStroke()
                                val radius0 = with(density) { editor.brushDp.dp.toPx() } / 2f / scale()
                                val p0 = toImage(down.position)
                                editor.line(p0.x, p0.y, p0.x, p0.y, radius0)
                            } else {
                                shape = listOf(down.position)
                            }
                            lens = down.position
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    if (!transforming) {
                                        if (tool == Tool.BRUSH) editor.cancelStroke()
                                        shape = emptyList()
                                        transforming = true
                                        lens = null
                                    }
                                    val c = event.calculateCentroid()
                                    val z = event.calculateZoom()
                                    val pc = event.calculatePan()
                                    if (c != Offset.Unspecified) {
                                        val before = toImage(c)
                                        zoom = (zoom * z).coerceIn(1f, 16f)
                                        val k = scale()
                                        val newOrigin = Offset(c.x - before.x * k, c.y - before.y * k) + pc
                                        pan = Offset(
                                            newOrigin.x - (canvasSize.width - editor.width * k) / 2f,
                                            newOrigin.y - (canvasSize.height - editor.height * k) / 2f,
                                        )
                                    }
                                    event.changes.forEach { it.consume() }
                                } else if (!transforming) {
                                    val p = pressed[0].position
                                    when (tool) {
                                        Tool.BRUSH -> {
                                            val r = with(density) { editor.brushDp.dp.toPx() } / 2f / scale()
                                            val a = toImage(last)
                                            val b = toImage(p)
                                            editor.line(a.x, a.y, b.x, b.y, r)
                                        }
                                        Tool.LASSO -> if ((p - last).getDistance() > 3f) shape = shape + p
                                        Tool.RECT -> shape = listOf(shape.first(), squared(shape.first(), p, editor.squareLock))
                                    }
                                    if (tool != Tool.LASSO || (p - last).getDistance() > 3f) last = p
                                    lens = p
                                    pressed[0].consume()
                                }
                            }
                            if (!transforming && tool != Tool.BRUSH) {
                                val pts = shape
                                if (tool == Tool.LASSO && pts.size >= 3) {
                                    val arr = FloatArray(pts.size * 2)
                                    pts.forEachIndexed { i, sp ->
                                        val ip = toImage(sp)
                                        arr[2 * i] = ip.x
                                        arr[2 * i + 1] = ip.y
                                    }
                                    editor.applyPolygon(arr)
                                } else if (tool == Tool.RECT && pts.size == 2) {
                                    val a = toImage(pts[0])
                                    val b = toImage(pts[1])
                                    if (kotlin.math.abs(a.x - b.x) > 2f && kotlin.math.abs(a.y - b.y) > 2f) {
                                        editor.applyRect(a.x, a.y, b.x, b.y)
                                    }
                                }
                            }
                            shape = emptyList()
                            lens = null
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    if (editor.frame < 0) return@Canvas // se lee para redibujar al pintar
                    // Fondo de cuadros
                    val cell = 12.dp.toPx()
                    drawRect(checkB)
                    var y = 0f
                    var row = 0
                    while (y < size.height) {
                        var x = if (row % 2 == 0) 0f else cell
                        while (x < size.width) {
                            drawRect(checkA, Offset(x, y), Size(cell, cell))
                            x += cell * 2
                        }
                        y += cell
                        row++
                    }
                    val k = scale()
                    val o = origin()
                    drawImage(
                        image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(editor.width, editor.height),
                        dstOffset = IntOffset(o.x.roundToInt(), o.y.roundToInt()),
                        dstSize = IntSize((editor.width * k).roundToInt(), (editor.height * k).roundToInt()),
                        filterQuality = if (k > 3f) FilterQuality.None else FilterQuality.Low,
                    )

                    // Vista previa del lazo o del rectángulo
                    if (shape.size >= 2) {
                        val path = Path()
                        if (editor.tool == Tool.RECT) {
                            val a = shape[0]
                            val b = shape[1]
                            path.addRect(androidx.compose.ui.geometry.Rect(
                                Offset(minOf(a.x, b.x), minOf(a.y, b.y)),
                                Offset(maxOf(a.x, b.x), maxOf(a.y, b.y)),
                            ))
                        } else {
                            path.moveTo(shape[0].x, shape[0].y)
                            for (i in 1 until shape.size) path.lineTo(shape[i].x, shape[i].y)
                            path.close()
                        }
                        drawPath(path, brushColor.copy(alpha = 0.18f))
                        drawPath(path, Color.White, style = Stroke(4.dp.toPx()))
                        drawPath(
                            path,
                            brushColor,
                            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                        )
                    }

                    val finger = lens
                    if (finger != null) {
                        val brushPx = if (editor.tool == Tool.BRUSH) editor.brushDp.dp.toPx() else 6.dp.toPx()
                        if (editor.tool == Tool.BRUSH) {
                            drawCircle(Color.White, brushPx / 2f, finger, style = Stroke(3.dp.toPx()))
                            drawCircle(brushColor, brushPx / 2f, finger, style = Stroke(1.5.dp.toPx()))
                        }

                        // Lupa: en la esquina contraria al dedo, con el doble de aumento.
                        val lr = 64.dp.toPx()
                        val margin = 16.dp.toPx()
                        val left = finger.x > size.width / 2f || finger.y > lr * 2 + margin * 2
                        val center = Offset(
                            if (left) margin + lr else size.width - margin - lr,
                            margin + lr,
                        )
                        val lensPath = Path().apply {
                            addOval(androidx.compose.ui.geometry.Rect(center, lr))
                        }
                        val k2 = k * 2f
                        val fi = toImage(finger)
                        clipPath(lensPath) {
                            drawRect(checkB, center - Offset(lr, lr), Size(lr * 2, lr * 2))
                            drawImage(
                                image,
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(editor.width, editor.height),
                                dstOffset = IntOffset(
                                    (center.x - fi.x * k2).roundToInt(),
                                    (center.y - fi.y * k2).roundToInt(),
                                ),
                                dstSize = IntSize((editor.width * k2).roundToInt(), (editor.height * k2).roundToInt()),
                                filterQuality = FilterQuality.None,
                            )
                            drawCircle(Color.White, brushPx, center, style = Stroke(3.dp.toPx()))
                            drawCircle(brushColor, brushPx, center, style = Stroke(1.5.dp.toPx()))
                        }
                        drawCircle(Color.White, lr, center, style = Stroke(5.dp.toPx()))
                        drawCircle(lensRing, lr, center, style = Stroke(2.dp.toPx()))
                    }
                }
                if (zoom > 1.01f) {
                    Surface(
                        onClick = { zoom = 1f; pan = Offset.Zero },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CenterFocusStrong, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${(zoom * 100).roundToInt()} %", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            EditorToolbar(editor)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorToolbar(editor: MaskEditor) {
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val tools = listOf(
                Triple(Tool.BRUSH, tr("Pincel", "Brush"), Icons.Rounded.Brush),
                Triple(Tool.LASSO, tr("Lazo", "Lasso"), Icons.Rounded.Gesture),
                Triple(Tool.RECT, tr("Rectángulo", "Rectangle"), Icons.Rounded.CropSquare),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                tools.forEachIndexed { i, (tool, label, icon) ->
                    SegmentedButton(
                        selected = editor.tool == tool,
                        onClick = { editor.tool = tool },
                        shape = SegmentedButtonDefaults.itemShape(i, tools.size),
                        icon = { SegmentedButtonDefaults.Icon(editor.tool == tool) { Icon(icon, null, Modifier.size(18.dp)) } },
                    ) { Text(label, maxLines = 1) }
                }
            }

            if (editor.tool == Tool.BRUSH) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = editor.erase,
                        onClick = { editor.erase = true },
                        label = { Text(tr("Borrar", "Erase")) },
                        leadingIcon = { Icon(Icons.Rounded.AutoFixOff, null, Modifier.size(18.dp)) },
                    )
                    FilterChip(
                        selected = !editor.erase,
                        onClick = { editor.erase = false },
                        label = { Text(tr("Restaurar", "Restore")) },
                        leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(18.dp)) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        val d = (editor.brushDp / 2f).coerceIn(6f, 36f)
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(d.dp)) {}
                    }
                    Spacer(Modifier.width(8.dp))
                    Slider(
                        value = editor.brushDp,
                        onValueChange = { editor.brushDp = it },
                        valueRange = 8f..120f,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                val ops = listOf(
                    ShapeOp.CUT to tr("Recortar", "Cut"),
                    ShapeOp.KEEP to tr("Solo dentro", "Keep inside"),
                    ShapeOp.ADD to tr("Añadir", "Add"),
                    ShapeOp.REMOVE to tr("Quitar", "Remove"),
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ops.forEach { (op, label) ->
                        FilterChip(
                            selected = editor.shapeOp == op,
                            onClick = { editor.shapeOp = op },
                            label = { Text(label) },
                        )
                    }
                    if (editor.tool == Tool.RECT) {
                        FilterChip(
                            selected = editor.squareLock,
                            onClick = { editor.squareLock = !editor.squareLock },
                            label = { Text("1:1") },
                            leadingIcon = { Icon(Icons.Rounded.Lock, null, Modifier.size(16.dp)) },
                        )
                    }
                }
                Text(
                    when (editor.shapeOp) {
                        ShapeOp.CUT -> tr("La forma que dibujes será el sticker.", "The shape you draw becomes the sticker.")
                        ShapeOp.KEEP -> tr("Conserva el recorte solo dentro de la forma.", "Keeps the cutout only inside the shape.")
                        ShapeOp.ADD -> tr("Añade al sticker lo que quede dentro.", "Adds whatever is inside to the sticker.")
                        ShapeOp.REMOVE -> tr("Quita del sticker lo que quede dentro.", "Removes whatever is inside from the sticker.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = editor::undo, enabled = editor.canUndo) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, tr("Deshacer", "Undo"))
                }
                IconButton(onClick = editor::redo, enabled = editor.canRedo) {
                    Icon(Icons.AutoMirrored.Rounded.Redo, tr("Rehacer", "Redo"))
                }
                FilledIconToggleButton(checked = editor.overlay, onCheckedChange = { editor.overlay = it }) {
                    Icon(Icons.Rounded.Layers, tr("Ver máscara", "Show mask"))
                }
                IconButton(onClick = editor::reset) {
                    Icon(Icons.Rounded.RestartAlt, tr("Volver al inicio", "Reset"))
                }
            }
            Text(
                if (editor.tool == Tool.BRUSH) tr(
                    "Un dedo para pintar · dos dedos para mover y hacer zoom",
                    "One finger to paint · two fingers to move and zoom",
                ) else tr(
                    "Un dedo para dibujar la forma · dos dedos para mover y hacer zoom",
                    "One finger to draw the shape · two fingers to move and zoom",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** Esquina final del rectángulo; con el bloqueo 1:1 lo convierte en cuadrado. */
private fun squared(start: Offset, end: Offset, lock: Boolean): Offset {
    if (!lock) return end
    val dx = end.x - start.x
    val dy = end.y - start.y
    val side = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
    return Offset(start.x + side * kotlin.math.sign(if (dx == 0f) 1f else dx), start.y + side * kotlin.math.sign(if (dy == 0f) 1f else dy))
}
