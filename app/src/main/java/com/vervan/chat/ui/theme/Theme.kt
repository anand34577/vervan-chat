package com.vervan.chat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Fixed "Nomad" palette (warm amber on near-black) — not Material You dynamic color.
 * ponytail: no bundled display font (Manrope/JetBrains Mono would need offline font
 * files, this app is offline-first so we don't fetch from Google Fonts) — system
 * sans-serif for body, system monospace for metadata/labels gets 90% of the visual
 * effect the mockup's type pairing was going for.
 */
private val DangerRed = Color(0xFFFF6B6B)

val VervanSuccess = Color(0xFF4ADE80)
val VervanWarn = Color(0xFFF2C94C)
val VervanSourceGrounded = Color(0xFF4ADE80)

/** Status colors intended for text/icons on the current surface. Fixed neon status colors lose
 * contrast in light mode, so resolve a darker tone there while keeping the familiar dark palette. */
internal val ColorScheme.vervanSuccess: Color
    get() = if (surface.luminance() > 0.5f) Color(0xFF0E6B38) else VervanSuccess

internal val ColorScheme.vervanWarning: Color
    get() = if (surface.luminance() > 0.5f) Color(0xFF725800) else VervanWarn

/** One accent's dark-mode primary/secondary pair — everything else in [DarkColors] (surfaces,
 * error, success) stays fixed across accents so switching accent doesn't also reflow contrast
 * everywhere else in the app. */
private data class AccentPair(val primary: Color, val onPrimary: Color, val primaryContainer: Color, val secondary: Color, val secondaryContainer: Color, val onSecondaryContainer: Color)

private val DarkAccents = mapOf(
    com.vervan.chat.data.settings.AccentTheme.AMBER to AccentPair(Color(0xFFE8A33D), Color(0xFF1A1305), Color(0xFF4A3A20), Color(0xFF5B8CFF), Color(0xFF1A2440), Color(0xFF5B8CFF)),
    com.vervan.chat.data.settings.AccentTheme.BLUE to AccentPair(Color(0xFF5B8CFF), Color(0xFF0A0F24), Color(0xFF1F3670), Color(0xFFE8A33D), Color(0xFF4A3A20), Color(0xFFE8A33D)),
    com.vervan.chat.data.settings.AccentTheme.GREEN to AccentPair(Color(0xFF4ADE80), Color(0xFF06280F), Color(0xFF1C4A2C), Color(0xFF5B8CFF), Color(0xFF1A2440), Color(0xFF5B8CFF)),
    com.vervan.chat.data.settings.AccentTheme.VIOLET to AccentPair(Color(0xFFB18CFF), Color(0xFF1E1338), Color(0xFF3A2A66), Color(0xFF4ADE80), Color(0xFF1C4A2C), Color(0xFF4ADE80)),
    com.vervan.chat.data.settings.AccentTheme.ROSE to AccentPair(Color(0xFFFF8FA3), Color(0xFF350A14), Color(0xFF632233), Color(0xFF5B8CFF), Color(0xFF1A2440), Color(0xFF5B8CFF))
)

private val LightAccents = mapOf(
    com.vervan.chat.data.settings.AccentTheme.AMBER to AccentPair(Color(0xFF8A5700), Color.White, Color(0xFFFFDCA8), Color(0xFF3B5FCC), Color(0xFFDCE4FF), Color(0xFF102050)),
    com.vervan.chat.data.settings.AccentTheme.BLUE to AccentPair(Color(0xFF3B5FCC), Color.White, Color(0xFFDCE4FF), Color(0xFFB5730A), Color(0xFFFFDCA8), Color(0xFF4A3010)),
    com.vervan.chat.data.settings.AccentTheme.GREEN to AccentPair(Color(0xFF16753E), Color.White, Color(0xFFC5F2D8), Color(0xFF3B5FCC), Color(0xFFDCE4FF), Color(0xFF102050)),
    com.vervan.chat.data.settings.AccentTheme.VIOLET to AccentPair(Color(0xFF7A4FD1), Color.White, Color(0xFFE6DBFF), Color(0xFF1E8E4E), Color(0xFFC5F2D8), Color(0xFF0E4526)),
    com.vervan.chat.data.settings.AccentTheme.ROSE to AccentPair(Color(0xFFC2477A), Color.White, Color(0xFFFFD9E4), Color(0xFF3B5FCC), Color(0xFFDCE4FF), Color(0xFF102050))
)

private fun darkSchemeFor(accent: AccentPair) = darkColorScheme(
    primary = accent.primary,
    onPrimary = accent.onPrimary,
    primaryContainer = accent.primaryContainer,
    onPrimaryContainer = Color(0xFFF1F3F7),
    secondary = accent.secondary,
    onSecondary = Color(0xFF0A0B0E),
    secondaryContainer = accent.secondaryContainer,
    onSecondaryContainer = accent.onSecondaryContainer,
    tertiary = VervanSuccess,
    onTertiary = Color(0xFF06280F),
    tertiaryContainer = Color(0xFF1C4A2C),
    onTertiaryContainer = Color(0xFFB8F5CB),
    background = Color(0xFF111318),
    onBackground = Color(0xFFF1F3F7),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFF1F3F7),
    surfaceVariant = Color(0xFF404652),
    onSurfaceVariant = Color(0xFFD2D7E0),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF171A21),
    surfaceContainer = Color(0xFF1D222B),
    surfaceContainerHigh = Color(0xFF252A34),
    surfaceContainerHighest = Color(0xFF303643),
    surfaceDim = Color(0xFF0C0E13),
    surfaceBright = Color(0xFF373D49),
    outline = Color(0xFF5C6270),
    outlineVariant = Color(0xFF454B58),
    error = DangerRed,
    onError = Color(0xFF231414),
    errorContainer = Color(0xFF231414),
    onErrorContainer = DangerRed
)

private fun lightSchemeFor(accent: AccentPair) = lightColorScheme(
    primary = accent.primary,
    onPrimary = accent.onPrimary,
    primaryContainer = accent.primaryContainer,
    onPrimaryContainer = accent.onSecondaryContainer,
    secondary = accent.secondary,
    onSecondary = Color.White,
    secondaryContainer = accent.secondaryContainer,
    onSecondaryContainer = accent.onSecondaryContainer,
    tertiary = Color(0xFF147A43),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC5F2D8),
    onTertiaryContainer = Color(0xFF0E4526),
    background = Color(0xFFF8F7F4),
    onBackground = Color(0xFF1B1C1E),
    surface = Color(0xFFFDFCF9),
    onSurface = Color(0xFF1B1C1E),
    surfaceVariant = Color(0xFFE7E2DC),
    onSurfaceVariant = Color(0xFF4A4D52),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F1EC),
    surfaceContainer = Color(0xFFEDEBE6),
    surfaceContainerHigh = Color(0xFFE7E5E0),
    surfaceContainerHighest = Color(0xFFE1DFDA),
    surfaceDim = Color(0xFFDAD8D3),
    surfaceBright = Color(0xFFFDFCF9),
    outline = Color(0xFF9B958C),
    outlineVariant = Color(0xFFC9C2B8),
    error = Color(0xFFBA1A1A)
)

private val VervanShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    // M3 Expressive pushes `large` past 16dp toward 20–24dp for surfaces that should feel soft
    // and friendly (cards, sheets, composer). 24dp here gives bubbles and modern surfaces a
    // rounder, more "physical" feel than the previous conservative 24 already on large.
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** Shapes outside Material3's fixed five-token [Shapes] scale (§3.5 + M3 Expressive additions).
 *  - hero/composer surfaces read as one deliberate size, distinct from both cards and dialogs
 *  - message bubbles get an asymmetric "tail" shape instead of every screen hand-rolling one
 *  - pill/full shape for chips, suggestion replies, model-switcher, quick-action pills
 *  - extra-extra-large (48dp) is the M3 Expressive max — used for hero gradients and big FABs. */
object VervanExtraShapes {
    val hero = RoundedCornerShape(28.dp)
    val composer = RoundedCornerShape(28.dp)
    val userBubble = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
    val assistantBubble = RoundedCornerShape(20.dp)
    /** Perfect pill — `CircleShape` is its own thing in Compose; this is the M3 "full" shape. */
    val pill = RoundedCornerShape(100.dp)
    val datePill = RoundedCornerShape(100.dp)
    val extraExtraLarge = RoundedCornerShape(48.dp)
}

/** Reserved for technical/metadata text (timestamps, token counts, model backend
 * names) — applied selectively via [Modifier], not baked into the type scale, since
 * most labelSmall/labelMedium usage in this app is plain UI text, not metadata. */
val VervanMono = FontFamily.Monospace

/**
 * App-wide spacing scale. Every screen used to hardcode its own dp padding/gap values
 * (10dp/12dp/13dp/14dp all meaning "card interior padding" in different files) — use
 * these instead of a bare `.dp` literal so spacing stays consistent as screens evolve.
 */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
}

private val VervanTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Bold)
    )
}

/** The swatch color Settings shows for each accent option — always the dark-mode primary,
 * since that reads clearly on both a light and dark settings row. */
fun com.vervan.chat.data.settings.AccentTheme.swatchColor(): Color = DarkAccents.getValue(this).primary

/** §3.2 high-contrast pass — applied on top of any resolved scheme, independent of accent/theme.
 * A hand-picked custom palette (not Material's dynamic contrast API, which only applies to
 * dynamic/harmonized schemes) so it works the same for both accent and Material You colors:
 * pulls muted text/borders to full-strength so state is never conveyed by a faint tint alone. */
private fun ColorScheme.withHighContrast(darkTheme: Boolean): ColorScheme = copy(
    onSurfaceVariant = onSurface,
    outline = if (darkTheme) Color(0xFFB8BFCC) else Color(0xFF5C5F66),
    outlineVariant = outline
)

@Composable
fun VervanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    oledTrueBlack: Boolean = false,
    dynamicColor: Boolean = false,
    highContrast: Boolean = false,
    accent: com.vervan.chat.data.settings.AccentTheme = com.vervan.chat.data.settings.AccentTheme.AMBER,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) {
        val scheme = darkSchemeFor(DarkAccents.getValue(accent))
        // OLED true-black variant (Phase 7, spec §35) — same accent, background/surface pushed
        // to pure black so OLED panels can actually turn those pixels off.
        if (oledTrueBlack) scheme.copy(background = Color(0xFF000000), surface = Color(0xFF000000), surfaceVariant = Color(0xFF121212)) else scheme
    } else {
        lightSchemeFor(LightAccents.getValue(accent))
    }
    if (highContrast) colorScheme = colorScheme.withHighContrast(darkTheme)
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = VervanShapes,
        typography = VervanTypography,
        content = content
    )
}
