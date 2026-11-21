package app.stikka.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.stikka.domain.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CutoutScreen(
    state: CutState,
    saving: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onWholeImage: () -> Unit,
    onPickAnother: () -> Unit,
    onEdit: () -> Unit,
    onDecorate: () -> Unit,
    onSubject: (Int) -> Unit,
    onFindSubjects: (() -> Unit)?,
    findingSubjects: Boolean,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(tr("Nuevo sticker", "New sticker")) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, tr("Cerrar", "Close")) }
                },
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "cut-state",
            ) { s ->
                when (s) {
                    is CutState.Working -> WorkingView(s)
                    is CutState.Ready -> ReadyView(s, saving, onSave, onWholeImage, onEdit, onDecorate, onSubject, onFindSubjects, findingSubjects)
                    is CutState.NoSubject -> ProblemView(
                        icon = Icons.Rounded.SearchOff,
                        title = tr("No encuentro a nadie", "Nothing to cut out"),
                        text = tr(
                            "No he detectado ninguna persona, animal u objeto claro en la foto. Puedes usar la foto entera o probar con otra.",
                            "No clear person, animal or object was found. You can use the whole photo or try another one.",
                        ),
                        preview = s.original,
                        onWholeImage = onWholeImage,
                        onPickAnother = onPickAnother,
                    )
                    is CutState.Failed -> ProblemView(
                        icon = Icons.Rounded.ErrorOutline,
                        title = tr("Algo ha fallado", "Something went wrong"),
                        text = s.message,
                        preview = s.original,
                        onWholeImage = if (s.original != null) onWholeImage else null,
                        onPickAnother = onPickAnother,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.large)
            .checkerboard(),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun WorkingView(s: CutState.Working) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        PreviewFrame {
            s.original?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(6.dp)
                        .alpha(0.55f),
                )
            }
            BrandBadge(
                icon = if (s.stage == Stage.DOWNLOADING_MODEL) Icons.Rounded.CloudDownload else Icons.Rounded.AutoAwesome,
                size = 88.dp,
                iconSize = 42.dp,
                modifier = Modifier.pulse(),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            when (s.stage) {
                Stage.LOADING -> tr("Abriendo la foto…", "Opening the photo…")
                Stage.DOWNLOADING_MODEL -> tr("Preparando el recorte inteligente…", "Getting smart cutout ready…")
                Stage.CUTTING -> tr("Recortando…", "Cutting out…")
            },
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (s.stage == Stage.DOWNLOADING_MODEL) {
                tr(
                    "Solo la primera vez: Google Play Services descarga el modelo. Luego funciona sin conexión.",
                    "First time only: Google Play Services downloads the model. Then it works offline.",
                )
            } else {
                tr("Todo ocurre en tu móvil.", "Everything happens on your phone.")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth(0.6f).clip(MaterialTheme.shapes.small))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyView(s: CutState.Ready, saving: Boolean, onSave: () -> Unit, onWholeImage: () -> Unit, onEdit: () -> Unit, onDecorate: () -> Unit, onSubject: (Int) -> Unit, onFindSubjects: (() -> Unit)?, findingSubjects: Boolean) {
    var showOriginal by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        PreviewFrame {
            Crossfade(targetState = showOriginal, label = "orig") { orig ->
                Image(
                    (if (orig) s.original else s.sticker).asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(if (orig) 0.dp else 8.dp),
                )
            }
        }
        if (s.subjects.isEmpty() && s.autoCut && onFindSubjects != null) {
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onFindSubjects, enabled = !findingSubjects && !saving) {
                if (findingSubjects) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(tr("¿Hay varias personas u objetos? Separarlos", "Several people or objects? Separate them"))
            }
        }
        if (s.subjects.size > 1) {
            Spacer(Modifier.height(12.dp))
            Text(
                tr("He encontrado ${s.subjects.size} sujetos. ¿Cuál quieres?", "I found ${s.subjects.size} subjects. Which one?"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = s.subjectIndex < 0, onClick = { onSubject(-1) }, label = { Text(tr("Todos", "All")) })
                s.subjects.indices.forEach { i ->
                    FilterChip(selected = s.subjectIndex == i, onClick = { onSubject(i) }, label = { Text("${i + 1}") })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !showOriginal,
                onClick = { showOriginal = false },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                icon = { SegmentedButtonDefaults.Icon(!showOriginal) { Icon(Icons.Rounded.Crop, null, Modifier.size(18.dp)) } },
            ) { Text(tr("Sticker", "Sticker")) }
            SegmentedButton(
                selected = showOriginal,
                onClick = { showOriginal = true },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                icon = { SegmentedButtonDefaults.Icon(showOriginal) { Icon(Icons.Rounded.Image, null, Modifier.size(18.dp)) } },
            ) { Text(tr("Original", "Original")) }
        }
        Spacer(Modifier.height(16.dp))
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        if (s.autoCut) tr("Recorte automático listo", "Automatic cutout ready")
                        else tr("Foto entera, sin recortar", "Whole photo, not cut out"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        tr("512 × 512 px · formato de WhatsApp", "512 × 512 px · WhatsApp format"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = onEdit, enabled = !saving, modifier = Modifier.weight(1f).height(56.dp)) {
                Icon(Icons.Rounded.Brush, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Recorte", "Cutout"))
            }
            FilledTonalButton(onClick = onDecorate, enabled = !saving, modifier = Modifier.weight(1f).height(56.dp)) {
                Icon(Icons.Rounded.EmojiEmotions, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Decorar", "Decorate"))
            }
        }
        Text(
            tr("Pincel, lazo o cuadrado · textos, emojis y borde", "Brush, lasso or square · text, emojis and outline"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onSave,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            if (saving) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(tr("Guardar sticker", "Save sticker"), style = MaterialTheme.typography.titleMedium)
            }
        }
        if (s.autoCut) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onWholeImage, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(tr("Usar la foto entera", "Use the whole photo"))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProblemView(
    icon: ImageVector,
    title: String,
    text: String,
    preview: android.graphics.Bitmap?,
    onWholeImage: (() -> Unit)?,
    onPickAnother: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        PreviewFrame {
            preview?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().alpha(0.35f),
                )
            }
            Box(
                Modifier
                    .size(88.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(42.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            if (onWholeImage != null) {
                Button(onClick = onWholeImage, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(tr("Usar la foto entera", "Use the whole photo"))
                }
            }
            OutlinedButton(onClick = onPickAnother, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(tr("Elegir otra foto", "Pick another photo"))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
