package app.stikka.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

object Brand {
    val Raspberry = Color(0xFFE0336F)
    val Orange = Color(0xFFFF8A3D)
    val Gradient = Brush.linearGradient(listOf(Raspberry, Orange))
}

private val Light = lightColorScheme(
    primary = Color(0xFFC2185B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3E001D),
    secondary = Color(0xFF74565F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF2B151C),
    tertiary = Color(0xFFA2440D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBCB),
    onTertiaryContainer = Color(0xFF351000),
    background = Color(0xFFFFF8F8),
    onBackground = Color(0xFF22191C),
    surface = Color(0xFFFFF8F8),
    onSurface = Color(0xFF22191C),
    surfaceVariant = Color(0xFFF2DDE2),
    onSurfaceVariant = Color(0xFF514347),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF0F3),
    surfaceContainer = Color(0xFFFAEAED),
    surfaceContainerHigh = Color(0xFFF4E4E7),
    surfaceContainerHighest = Color(0xFFEEDEE1),
    outline = Color(0xFF837377),
    outlineVariant = Color(0xFFD5C2C6),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB1C8),
    onPrimary = Color(0xFF650033),
    primaryContainer = Color(0xFF8E1F4B),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFE3BDC6),
    onSecondary = Color(0xFF422931),
    secondaryContainer = Color(0xFF5B3F47),
    onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFFFFB691),
    onTertiary = Color(0xFF571F00),
    tertiaryContainer = Color(0xFF7D2E00),
    onTertiaryContainer = Color(0xFFFFDBCB),
    background = Color(0xFF191113),
    onBackground = Color(0xFFF0DEE1),
    surface = Color(0xFF191113),
    onSurface = Color(0xFFF0DEE1),
    surfaceVariant = Color(0xFF514347),
    onSurfaceVariant = Color(0xFFD5C2C6),
    surfaceContainerLowest = Color(0xFF130C0E),
    surfaceContainerLow = Color(0xFF22191C),
    surfaceContainer = Color(0xFF261D20),
    surfaceContainerHigh = Color(0xFF31272A),
    surfaceContainerHighest = Color(0xFF3C3235),
    outline = Color(0xFF9E8C91),
    outlineVariant = Color(0xFF514347),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val Base = Typography()
private val AppTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge,
    bodyMedium = Base.bodyMedium,
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

@Composable
fun StikkaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
