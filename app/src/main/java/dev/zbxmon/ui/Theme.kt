package dev.zbxmon.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.zbxmon.data.ThemeStyle

private val Terminal = darkColorScheme(
    primary = Color(0xFF39FF14),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF0F3D0F),
    onPrimaryContainer = Color(0xFFB6FFB6),
    secondary = Color(0xFF7FD67F),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF14301A),
    onSecondaryContainer = Color(0xFFB6FFB6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFB6FFB6),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFB6FFB6),
    surfaceVariant = Color(0xFF102010),
    onSurfaceVariant = Color(0xFF7FD67F),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF070D07),
    surfaceContainer = Color(0xFF0A140A),
    surfaceContainerHigh = Color(0xFF0D1A0D),
    surfaceContainerHighest = Color(0xFF0F1F0F),
    outline = Color(0xFF2E7D32),
    outlineVariant = Color(0xFF1B3A1B),
    error = Color(0xFFFF5252),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF3B0A0A),
    onErrorContainer = Color(0xFFFFB4B4)
)

/** Every text style switched to a monospace font for the terminal look. */
private fun Typography.mono(): Typography {
    val f = FontFamily.Monospace
    return copy(
        displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f),
        displaySmall = displaySmall.copy(fontFamily = f), headlineLarge = headlineLarge.copy(fontFamily = f),
        headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
        titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f),
        titleSmall = titleSmall.copy(fontFamily = f), bodyLarge = bodyLarge.copy(fontFamily = f),
        bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
        labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f),
        labelSmall = labelSmall.copy(fontFamily = f)
    )
}

private val SquareShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp), small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(2.dp), large = RoundedCornerShape(4.dp), extraLarge = RoundedCornerShape(4.dp)
)

@Composable
fun ZbxTheme(style: ThemeStyle, content: @Composable () -> Unit) {
    when (style) {
        ThemeStyle.TERMINAL -> MaterialTheme(
            colorScheme = Terminal,
            typography = Typography().mono(),
            shapes = SquareShapes,
            content = content
        )
        ThemeStyle.MINIMAL -> {
            val dark = isSystemInDarkTheme()
            val ctx = LocalContext.current
            val scheme = when {
                Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
                Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme, content = content)
        }
    }
}
