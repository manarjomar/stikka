package app.stikka.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.stikka.domain.AnimFrames
import app.stikka.domain.tr
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedScreen(vm: MainViewModel, a: AnimSession) {
    val busy = a.working != null

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(tr("Sticker animado", "Animated sticker")) },
                navigationIcon = { IconButton(onClick = vm::closeAnim) { Icon(Icons.Rounded.Close, tr("Cancelar", "Cancel")) } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Vista previa en bucle
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.large)
                    .checkerboard(),
                contentAlignment = Alignment.Center,
            ) {
                val frames = a.finals
                var index by remember(frames) { mutableIntStateOf(0) }
                LaunchedEffect(frames, a.fps) {
                    while (frames.isNotEmpty()) {
                        delay(a.frameMs.toLong())
                        index = (index + 1) % frames.size
                    }
                }
                frames.getOrNull(index)?.let {
                    Image(it.asImageBitmap(), null, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                }
                if (busy) {
                    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            BrandBadge(Icons.Rounded.AutoAwesome, size = 64.dp, iconSize = 30.dp, modifier = Modifier.pulse())
                            Spacer(Modifier.height(12.dp))
                            Text(a.working.orEmpty(), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(progress = { a.progress }, modifier = Modifier.width(180.dp).clip(MaterialTheme.shapes.small))
                        }
                    }
                }
            }

            // Tramo
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Movie, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(tr("Tramo", "Clip"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            "${secs(a.startMs)} – ${secs(a.endMs)}  (${secs(a.endMs - a.startMs)})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val total = a.source.durationMs.coerceAtLeast(100L).toFloat()
                    RangeSlider(
                        value = a.startMs.toFloat()..a.endMs.toFloat(),
                        onValueChange = { r ->
                            var s = r.start.toLong()
                            var e = r.endInclusive.toLong()
                            if (e - s > AnimFrames.MAX_WINDOW_MS) {
                                if (s != a.startMs) e = s + AnimFrames.MAX_WINDOW_MS else s = e - AnimFrames.MAX_WINDOW_MS
                            }
                            if (e - s < 300) e = (s + 300).coerceAtMost(total.toLong())
                            a.startMs = s.coerceAtLeast(0)
                            a.endMs = e
                            a.dirty = true
                        },
                        valueRange = 0f..total,
                        enabled = !busy,
                    )
                    Text(
                        tr("Máximo 8 segundos (WhatsApp admite 10).", "Up to 8 seconds (WhatsApp allows 10)."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Fluidez y encuadre
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("Fluidez", "Smoothness"), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(6, 8, 10, 12, 15).forEach { f ->
                            FilterChip(
                                selected = a.fps == f,
                                onClick = { a.fps = f; a.dirty = true },
                                label = { Text("$f fps") },
                                enabled = !busy,
                            )
                        }
                    }
                    Text(
                        tr(
                            "${a.frameCount} fotogramas" + if (a.frameCount >= AnimFrames.MAX_FRAMES) " (máximo; acorta el tramo o baja la fluidez)" else "",
                            "${a.frameCount} frames" + if (a.frameCount >= AnimFrames.MAX_FRAMES) " (maximum; shorten the clip or lower smoothness)" else "",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(tr("Encuadre", "Framing"), style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !a.square,
                            onClick = { a.square = false; a.dirty = true },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            enabled = !busy,
                        ) { Text(tr("Entero", "Whole")) }
                        SegmentedButton(
                            selected = a.square,
                            onClick = { a.square = true; a.dirty = true },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            enabled = !busy,
                        ) { Text(tr("Cuadrado", "Square")) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("Quitar el fondo", "Remove background"), style = MaterialTheme.typography.titleMedium)
                            Text(
                                tr("Recorte inteligente en cada fotograma (tarda un poco)", "Smart cutout on every frame (takes a while)"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = a.removeBg, onCheckedChange = { a.removeBg = it; a.dirty = true }, enabled = !busy)
                    }
                }
            }

            if (a.dirty) {
                FilledTonalButton(onClick = vm::generateAnim, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Rounded.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Actualizar vista previa", "Update preview"))
                }
            }
            FilledTonalButton(
                onClick = vm::decorateAnim,
                enabled = !busy && a.frames.isNotEmpty() && !a.dirty,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Rounded.EmojiEmotions, null)
                Spacer(Modifier.width(8.dp))
                Text(tr("Decorar: textos, emojis y borde", "Decorate: text, emojis and outline"))
            }
            Button(
                onClick = vm::saveAnim,
                enabled = !busy && !vm.saving && a.finals.isNotEmpty() && !a.dirty,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (vm.saving) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(tr("Guardar sticker animado", "Save animated sticker"), style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(
                tr(
                    "Se ajusta solo al límite de WhatsApp (500 KB): si hace falta, baja la calidad o la fluidez.",
                    "It automatically fits WhatsApp's limit (500 KB): if needed, quality or smoothness is lowered.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun secs(ms: Long) = String.format(Locale.ROOT, "%.1f s", ms / 1000f)
