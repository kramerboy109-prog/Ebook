package com.lesehaus.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lesehaus.app.R
import com.lesehaus.app.data.AppThemeMode
import java.io.File

// ---------- Farben ----------
object Palette {
    val Walnut = Color(0xFF6B4630)
    val WalnutDark = Color(0xFF3F271A)
    val Cream = Color(0xFFF6EFE3)
    val Parchment = Color(0xFFEDE1CC)
    val Brass = Color(0xFFB08D57)
    val BrassLight = Color(0xFFD9B97F)
    val Burgundy = Color(0xFF7B2D3B)
    val Ink = Color(0xFF2B2420)
    val Ember = Color(0xFFE39A52)
    val Forest = Color(0xFF3F5A45)
    val Night = Color(0xFF17110D)

    val Highlights = listOf(
        Color(0xFFFFE27A), // Gelb
        Color(0xFFA8E6A1), // Grün
        Color(0xFF9CCBFF), // Blau
        Color(0xFFFFB3C7), // Rosa
        Color(0xFFFFC48A), // Orange
        Color(0xFFD4B8FF), // Lila
    )
    val HighlightNames = listOf("Gelb", "Grün", "Blau", "Rosa", "Orange", "Lila")
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF7A4B2A),
    onPrimary = Color(0xFFFFF8F0),
    primaryContainer = Color(0xFFF1D9BF),
    onPrimaryContainer = Color(0xFF2E1606),
    secondary = Color(0xFF8C6D3C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2E1C0),
    onSecondaryContainer = Color(0xFF2B1F08),
    tertiary = Palette.Burgundy,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7D9DD),
    onTertiaryContainer = Color(0xFF3B0B15),
    background = Color(0xFFF8F2E8),
    onBackground = Palette.Ink,
    surface = Color(0xFFFBF6EE),
    onSurface = Palette.Ink,
    surfaceVariant = Color(0xFFEDE2D2),
    onSurfaceVariant = Color(0xFF54473B),
    surfaceContainerLowest = Color(0xFFFFFCF7),
    surfaceContainerLow = Color(0xFFF7F0E5),
    surfaceContainer = Color(0xFFF2EADD),
    surfaceContainerHigh = Color(0xFFECE2D3),
    surfaceContainerHighest = Color(0xFFE6DBCA),
    outline = Color(0xFF8A7A69),
    outlineVariant = Color(0xFFD6C8B5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE2B47E),
    onPrimary = Color(0xFF3E2410),
    primaryContainer = Color(0xFF5B3A20),
    onPrimaryContainer = Color(0xFFFFDDBE),
    secondary = Color(0xFFD8BF8E),
    onSecondary = Color(0xFF3A2B0C),
    secondaryContainer = Color(0xFF52421F),
    onSecondaryContainer = Color(0xFFF4E0B6),
    tertiary = Color(0xFFF0B3BE),
    onTertiary = Color(0xFF4F1420),
    tertiaryContainer = Color(0xFF6A2A36),
    onTertiaryContainer = Color(0xFFFFD9DE),
    background = Color(0xFF16110E),
    onBackground = Color(0xFFEDE1D3),
    surface = Color(0xFF1B1511),
    onSurface = Color(0xFFEDE1D3),
    surfaceVariant = Color(0xFF3A3029),
    onSurfaceVariant = Color(0xFFD4C3B1),
    surfaceContainerLowest = Color(0xFF110C09),
    surfaceContainerLow = Color(0xFF1F1814),
    surfaceContainer = Color(0xFF241C17),
    surfaceContainerHigh = Color(0xFF2E251F),
    surfaceContainerHighest = Color(0xFF393029),
    outline = Color(0xFF9C8C7C),
    outlineVariant = Color(0xFF4E4339),
)

// ---------- Schriften ----------
private fun varFont(res: Int, weight: Int, italic: Boolean = false) = Font(
    res,
    FontWeight(weight),
    if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun family(regular: Int, italic: Int? = null): FontFamily {
    val weights = listOf(300, 400, 500, 600, 700)
    val fonts = weights.map { varFont(regular, it) } + (italic?.let { i -> weights.map { varFont(i, it, true) } } ?: emptyList())
    return FontFamily(fonts)
}

object Fonts {
    val Playfair = family(R.font.playfair)
    val Lora = family(R.font.lora, R.font.lora_italic)
    val Literata = family(R.font.literata, R.font.literata_italic)
    val Garamond = family(R.font.ebgaramond, R.font.ebgaramond_italic)
    val Crimson = family(R.font.crimsonpro, R.font.crimsonpro_italic)
    val Nunito = family(R.font.nunito)
    val Caveat = family(R.font.caveat)
    val Atkinson = FontFamily(
        Font(R.font.atkinson_regular, FontWeight.Normal),
        Font(R.font.atkinson_bold, FontWeight.Bold),
        Font(R.font.atkinson_italic, FontWeight.Normal, FontStyle.Italic),
    )
}

data class ReadingFont(val key: String, val label: String, val description: String, val family: FontFamily)

object ReadingFonts {
    val builtIn = listOf(
        ReadingFont("literata", "Literata", "Für Bildschirme gemacht, warm & klar", Fonts.Literata),
        ReadingFont("garamond", "EB Garamond", "Klassische Buchschrift", Fonts.Garamond),
        ReadingFont("lora", "Lora", "Elegant mit weichen Kurven", Fonts.Lora),
        ReadingFont("crimson", "Crimson Pro", "Wie ein alter Roman", Fonts.Crimson),
        ReadingFont("atkinson", "Atkinson Hyperlegible", "Besonders gut lesbar", Fonts.Atkinson),
        ReadingFont("nunito", "Nunito", "Rund & freundlich (serifenlos)", Fonts.Nunito),
        ReadingFont("serif", "System Serif", "Serifenschrift des Geräts", FontFamily.Serif),
        ReadingFont("sans", "System Sans", "Serifenlose Schrift des Geräts", FontFamily.SansSerif),
        ReadingFont("mono", "Schreibmaschine", "Monospace", FontFamily.Monospace),
    )

    fun custom(paths: List<String>): List<ReadingFont> = paths.mapNotNull { p ->
        val f = File(p)
        if (!f.exists()) null else runCatching {
            ReadingFont("file:$p", f.nameWithoutExtension.replace('_', ' '), "Eigene Schrift", FontFamily(Font(f)))
        }.getOrNull()
    }

    fun resolve(key: String, custom: List<String>): FontFamily =
        builtIn.firstOrNull { it.key == key }?.family
            ?: custom(custom).firstOrNull { it.key == key }?.family
            ?: Fonts.Literata
}

private val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = Fonts.Playfair),
        displayMedium = t.displayMedium.copy(fontFamily = Fonts.Playfair),
        displaySmall = t.displaySmall.copy(fontFamily = Fonts.Playfair),
        headlineLarge = t.headlineLarge.copy(fontFamily = Fonts.Playfair, fontWeight = FontWeight.SemiBold),
        headlineMedium = t.headlineMedium.copy(fontFamily = Fonts.Playfair, fontWeight = FontWeight.SemiBold),
        headlineSmall = t.headlineSmall.copy(fontFamily = Fonts.Playfair, fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontFamily = Fonts.Playfair, fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontFamily = Fonts.Lora, fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontFamily = Fonts.Lora, fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(fontFamily = Fonts.Lora),
        bodyMedium = t.bodyMedium.copy(fontFamily = Fonts.Lora),
        bodySmall = t.bodySmall.copy(fontFamily = Fonts.Lora),
        labelLarge = t.labelLarge.copy(fontFamily = Fonts.Nunito, fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.copy(fontFamily = Fonts.Nunito, fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.copy(fontFamily = Fonts.Nunito, fontWeight = FontWeight.SemiBold),
    )
}

val QuoteStyle = TextStyle(fontFamily = Fonts.Garamond, fontStyle = FontStyle.Italic, fontSize = 20.sp, lineHeight = 29.sp)
val HandwritingStyle = TextStyle(fontFamily = Fonts.Caveat, fontSize = 21.sp, lineHeight = 26.sp)

@Composable
fun LesehausTheme(mode: AppThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val colors: ColorScheme = if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
