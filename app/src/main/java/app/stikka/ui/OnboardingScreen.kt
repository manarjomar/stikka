package app.stikka.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.stikka.domain.tr
import kotlinx.coroutines.launch

private data class Page(val icon: ImageVector, val title: String, val text: String)

/** Bienvenida de tres pasos, solo la primera vez. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pages = listOf(
        Page(
            Icons.Rounded.AutoAwesome,
            tr("Recorte inteligente", "Smart cutout"),
            tr("Elige una foto y Stikka recorta sola a la persona, mascota u objeto. Todo en tu móvil.", "Pick a photo and Stikka cuts out the person, pet or object by itself. All on your phone."),
        ),
        Page(
            Icons.Rounded.Brush,
            tr("Retoca y decora", "Fine-tune and decorate"),
            tr("Afina el recorte con pincel, lazo o cuadrado y añade textos, emojis y borde.", "Refine the cutout with brush, lasso or square and add text, emojis and outline."),
        ),
        Page(
            Icons.Rounded.Inventory2,
            tr("Paquetes para WhatsApp", "Packs for WhatsApp"),
            tr("Agrupa tus stickers en paquetes y añádelos a WhatsApp con un toque. También puedes importar los de tus chats.", "Group your stickers into packs and add them to WhatsApp with one tap. You can also import the ones from your chats."),
        ),
    )
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .fillMaxSize()
            .background(Brand.Gradient),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDone) { Text(tr("Saltar", "Skip"), color = Color.White) }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { i ->
                val p = pages[i]
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.size(150.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(p.icon, null, tint = Color.White, modifier = Modifier.size(76.dp).pulse())
                    }
                    Spacer(Modifier.height(36.dp))
                    Text(p.title, style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    Text(p.text, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.92f), textAlign = TextAlign.Center)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                pages.indices.forEach { i ->
                    val w by animateDpAsState(if (pager.currentPage == i) 24.dp else 8.dp, label = "dot")
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = w, height = 8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (pager.currentPage == i) 1f else 0.45f)),
                    )
                }
            }
            val last = pager.currentPage == pages.lastIndex
            Button(
                onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Brand.Raspberry),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(if (last) tr("Empezar", "Get started") else tr("Siguiente", "Next"), fontWeight = FontWeight.Bold)
            }
        }
    }
}
