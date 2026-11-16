package app.stikka.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.stikka.data.Sticker
import app.stikka.domain.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    stickers: List<Sticker>,
    snackbar: SnackbarHostState,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onOpen: (Sticker) -> Unit,
) {
    val grid = rememberLazyGridState()
    val heroGone by remember { derivedStateOf { grid.firstVisibleItemIndex > 0 } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            AnimatedVisibility(visible = heroGone, enter = scaleIn(), exit = scaleOut()) {
                ExtendedFloatingActionButton(
                    onClick = onGallery,
                    icon = { Icon(Icons.Rounded.AutoAwesome, null) },
                    text = { Text(tr("Nuevo sticker", "New sticker")) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            state = grid,
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Hero(onGallery, onCamera)
            }
            if (stickers.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyState() }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                    ) {
                        Text(tr("Mis stickers", "My stickers"), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(10.dp))
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                stickers.size.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                items(stickers, key = { it.id }) { sticker ->
                    Card(
                        onClick = { onOpen(sticker) },
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.animateItem(),
                    ) {
                        StickerImage(
                            sticker,
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .padding(10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(onGallery: () -> Unit, onCamera: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brand.Gradient)
            .padding(22.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.22f)) {
                    Icon(
                        Icons.Rounded.ContentCut,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp).size(20.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "Stikka",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                tr(
                    "Convierte cualquier foto en un sticker. El recorte se hace solo y en tu móvil.",
                    "Turn any photo into a sticker. The cutout is automatic and stays on your phone.",
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.92f),
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onGallery,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Brand.Raspberry),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Galería", "Gallery"), fontWeight = FontWeight.Bold)
                }
                FilledTonalButton(
                    onClick = onCamera,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color.White.copy(alpha = 0.22f),
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Icon(Icons.Rounded.PhotoCamera, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Cámara", "Camera"), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 36.dp, start = 12.dp, end = 12.dp),
    ) {
        BrandBadge(Icons.Rounded.AutoAwesome, size = 84.dp, iconSize = 40.dp, modifier = Modifier.pulse())
        Spacer(Modifier.height(18.dp))
        Text(tr("Aún no tienes stickers", "No stickers yet"), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            tr("Crea el primero en tres pasos:", "Make your first one in three steps:"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Step(Icons.Rounded.PhotoLibrary, tr("Elige una foto o haz una nueva", "Pick a photo or take a new one"))
                Step(Icons.Rounded.ContentCut, tr("Stikka recorta el personaje u objeto", "Stikka cuts out the person or object"))
                Step(Icons.Rounded.IosShare, tr("Guárdalo y compártelo", "Save it and share it"))
            }
        }
    }
}

@Composable
private fun Step(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(8.dp).size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
