package app.stikka.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stikka.data.Sticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fondo de cuadros que indica transparencia, en los colores del tema. */
@Composable
fun Modifier.checkerboard(cell: Dp = 10.dp): Modifier {
    val a = MaterialTheme.colorScheme.surfaceContainerHighest
    val b = MaterialTheme.colorScheme.surfaceContainerLow
    return this.drawBehind {
        drawRect(b)
        val s = cell.toPx()
        val cols = (size.width / s).toInt() + 1
        val rows = (size.height / s).toInt() + 1
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if ((r + c) % 2 == 0) {
                    drawRect(a, topLeft = Offset(c * s, r * s), size = Size(s, s))
                }
            }
        }
    }
}

/** Carga el WebP de un sticker sin bloquear la interfaz. */
@Composable
fun StickerImage(sticker: Sticker, modifier: Modifier = Modifier) {
    val image by produceState<ImageBitmap?>(null, sticker.id, sticker.version) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(sticker.file.absolutePath)?.asImageBitmap() }.getOrNull()
        }
    }
    image?.let {
        Image(bitmap = it, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
    }
}

/** Icono dentro de un círculo con el degradado de la marca. */
@Composable
fun BrandBadge(icon: ImageVector, size: Dp = 56.dp, iconSize: Dp = 28.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brand.Gradient),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

/** Latido suave para los estados de espera. */
@Composable
fun Modifier.pulse(): Modifier {
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse-scale",
    )
    return this.scale(s)
}

/** Emojis frecuentes para decorar y para etiquetar stickers. */
val EmojiList: List<String> = (
    "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 😉 😊 😇 🥰 😍 🤩 😘 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😏 " +
        "😒 🙄 😬 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 🧐 😕 😟 🙁 😮 😲 😳 🥺 " +
        "😦 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 💩 🤡 👻 👽 🤖 😺 😹 😻 🙈 " +
        "🙉 🙊 💋 💌 💘 💝 💖 💗 💓 💞 💕 ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 💔 ❣️ 💯 💢 💥 💫 💦 💨 💬 💤 👋 " +
        "👌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 👍 👎 ✊ 👊 👏 🙌 👐 🙏 💪 🫶 👀 🧠 🔥 ⭐ 🌟 ✨ ⚡ 🌈 ☀️ 🌙 " +
        "🎉 🎊 🎈 🎁 🎂 🏆 🥇 ⚽ 🏀 🎮 🎵 🎶 🍕 🍔 🍟 🌮 🍿 🍩 🍪 🍫 🍺 🍻 🥂 🍷 ☕ 🐶 🐱 🐭 🐰 🦊 " +
        "🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🦄 🐝 🦋 🌹 🌻 🌸 🍀 ✅ ❌ ❓ ❗ 🆗 🆒 🆕 🔝 📍 📸 💰 🚀"
    ).split(' ').filter { it.isNotBlank() }


/** Etiqueta pequeña para los stickers animados. */
@Composable
fun AnimatedBadge(modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        color = Brand.Raspberry,
        modifier = modifier,
    ) {
        androidx.compose.material3.Text(
            "GIF",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}
