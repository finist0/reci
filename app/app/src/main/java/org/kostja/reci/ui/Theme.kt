package org.kostja.reci.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import org.kostja.reci.R
import org.kostja.reci.data.Prefs

data class Palette(
    val name: String,
    val bg: Color,
    val card: Color,
    val accent: Color,
    val word: Color,
    val highlight: Color,
    val dark: Boolean,
) {
    val band get() = bg
    val content get() = bg
    val text get() = if (dark) Color(0xFFF1EFEC) else Color(0xFF1E1F24)
    val meaning get() = text
    val secondary get() = if (dark) Color(0xFFA3A7AF) else Color(0xFF6E6A64)
    val faint get() = if (dark) Color(0xFF6F737B) else Color(0xFFA8A29A)
    val clock get() = text
    val divider get() = if (dark) Color(0xFF2B2E35) else Color(0xFFEAE3D8)
    val panel get() = if (dark) Color(0xFF262A32) else Color(0xFFF3EDE3)
    val track get() = panel
    val chipText get() = secondary
    val markOff get() = faint
}

val MarkDontKnow = Color(0xFFD64545)
val MarkUnsure = Color(0xFFE0A526)

val Palettes = listOf(
    Palette("Кајмак", Color(0xFFF5EFE6), Color(0xFFFFFCF7), Color(0xFFC2502D), Color(0xFF2B2D42), Color(0xFFF6DDD2), false),
    Palette("Дунав", Color(0xFFEAF1F2), Color(0xFFFFFFFF), Color(0xFF1F7A8C), Color(0xFF103B52), Color(0xFFD3E9EE), false),
    Palette("Липа", Color(0xFFEEF2E6), Color(0xFFFFFFFB), Color(0xFF4F7F34), Color(0xFF263D1A), Color(0xFFDCE9CF), false),
    Palette("Шљива", Color(0xFFF3EDF2), Color(0xFFFFFCFE), Color(0xFF8A3D6E), Color(0xFF3E1834), Color(0xFFEFDCE9), false),
    Palette("Сунцокрет", Color(0xFFFAF4E3), Color(0xFFFFFDF6), Color(0xFFB87A00), Color(0xFF4A3400), Color(0xFFF6E6B8), false),
    Palette("Ноћ", Color(0xFF14161B), Color(0xFF1D2027), Color(0xFFE59A55), Color(0xFFF3E7D9), Color(0xFF3A2C20), true),
    Palette("Мастило", Color(0xFF0E1626), Color(0xFF172238), Color(0xFF6CB4D4), Color(0xFFE2EEF7), Color(0xFF1E3A4D), true),
    Palette("Шума", Color(0xFF111612), Color(0xFF1A211B), Color(0xFF8DC26F), Color(0xFFE6F0E0), Color(0xFF26382A), true),
)

val Serif = FontFamily(
    Font(R.font.literata, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.literata, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.literata, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.literata, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

val Display = FontFamily(Font(R.font.ruslan_display))

val Palette.titleRed get() = if (dark) Color(0xFFE5574A) else Color(0xFFB8322A)

val LocalPalette =staticCompositionLocalOf { Palettes[2] }

@Composable
fun ReciTheme(content: @Composable () -> Unit) {
    val p = Palettes.getOrElse(Prefs.theme) { Palettes[2] }
    val scheme = if (p.dark) {
        darkColorScheme(
            primary = p.accent,
            onPrimary = Color.White,
            background = p.bg,
            surface = p.card,
            onSurface = p.text,
            onSurfaceVariant = p.secondary,
            surfaceVariant = p.panel,
        )
    } else {
        lightColorScheme(
            primary = p.accent,
            onPrimary = Color.White,
            background = p.bg,
            surface = p.card,
            onSurface = p.text,
            onSurfaceVariant = p.secondary,
            surfaceVariant = p.panel,
        )
    }
    // enableEdgeToEdge() follows the system theme; the bar icons must follow ours.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !p.dark
                isAppearanceLightNavigationBars = !p.dark
            }
        }
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
