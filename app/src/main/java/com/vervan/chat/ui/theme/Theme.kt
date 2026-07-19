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
import androidx.compose.ui.unit.sp

/**
 * Fixed "Aurora" palette — deep indigo-ink neutrals with vivid electric accents in dark mode,
 * cool porcelain neutrals in light mode. Not Material You dynamic color. No bundled display
 * font (offline-first app, nothing is fetched from Google Fonts) — system sans-serif for body,
 * system monospace for metadata/labels carries the technical texture instead.
 */
private val DangerRed = Color(0xFFFF6B7A)

val VervanSuccess = Color(0xFF53E88B)
val VervanWarn = Color(0xFFF5C542)
val VervanSourceGrounded = Color(0xFF53E88B)

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
    com.vervan.chat.data.settings.AccentTheme.AMBER to AccentPair(Color(0xFFF6B24E), Color(0xFF221402), Color(0xFF503A12), Color(0xFF7C9AFF), Color(0xFF1D2A52), Color(0xFFA9BDFF)),
    com.vervan.chat.data.settings.AccentTheme.BLUE to AccentPair(Color(0xFF7C9AFF), Color(0xFF0A1030), Color(0xFF24356B), Color(0xFFF6B24E), Color(0xFF503A12), Color(0xFFFFD9A0)),
    com.vervan.chat.data.settings.AccentTheme.GREEN to AccentPair(Color(0xFF53E88B), Color(0xFF04240E), Color(0xFF14532D), Color(0xFF7C9AFF), Color(0xFF1D2A52), Color(0xFFA9BDFF)),
    com.vervan.chat.data.settings.AccentTheme.VIOLET to AccentPair(Color(0xFFA78BFA), Color(0xFF190E33), Color(0xFF3C2A6E), Color(0xFF53E88B), Color(0xFF14532D), Color(0xFFA7F3C4)),
    com.vervan.chat.data.settings.AccentTheme.ROSE to AccentPair(Color(0xFFFB7185), Color(0xFF33060E), Color(0xFF6B1D2C), Color(0xFF7C9AFF), Color(0xFF1D2A52), Color(0xFFA9BDFF))
)

private val LightAccents = mapOf(
    com.vervan.chat.data.settings.AccentTheme.AMBER to AccentPair(Color(0xFF9A6400), Color.White, Color(0xFFFFE1B0), Color(0xFF3D5FE0), Color(0xFFDDE4FF), Color(0xFF14224F)),
    com.vervan.chat.data.settings.AccentTheme.BLUE to AccentPair(Color(0xFF3D5FE0), Color.White, Color(0xFFDDE4FF), Color(0xFFA16207), Color(0xFFFFE1B0), Color(0xFF4A3005)),
    com.vervan.chat.data.settings.AccentTheme.GREEN to AccentPair(Color(0xFF0F7A3D), Color.White, Color(0xFFBFF2D4), Color(0xFF3D5FE0), Color(0xFFDDE4FF), Color(0xFF14224F)),
    com.vervan.chat.data.settings.AccentTheme.VIOLET to AccentPair(Color(0xFF6D46D6), Color.White, Color(0xFFE5DBFF), Color(0xFF157F45), Color(0xFFBFF2D4), Color(0xFF0B4023)),
    com.vervan.chat.data.settings.AccentTheme.ROSE to AccentPair(Color(0xFFCC3D6E), Color.White, Color(0xFFFFD7E2), Color(0xFF3D5FE0), Color(0xFFDDE4FF), Color(0xFF14224F))
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
    onTertiary = Color(0xFF04240E),
    tertiaryContainer = Color(0xFF14532D),
    onTertiaryContainer = Color(0xFFA7F3C4),
    background = Color(0xFF0A0D14),
    onBackground = Color(0xFFEDF0F7),
    surface = Color(0xFF0A0D14),
    onSurface = Color(0xFFEDF0F7),
    surfaceVariant = Color(0xFF39415A),
    onSurfaceVariant = Color(0xFFC7CEDE),
    surfaceContainerLowest = Color(0xFF060810),
    surfaceContainerLow = Color(0xFF10141F),
    surfaceContainer = Color(0xFF161B29),
    surfaceContainerHigh = Color(0xFF1E2434),
    surfaceContainerHighest = Color(0xFF283042),
    surfaceDim = Color(0xFF060810),
    surfaceBright = Color(0xFF303950),
    outline = Color(0xFF545E78),
    outlineVariant = Color(0xFF3B4359),
    error = DangerRed,
    onError = Color(0xFF2A0F14),
    errorContainer = Color(0xFF2A0F14),
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
    tertiary = Color(0xFF127A41),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBFF2D4),
    onTertiaryContainer = Color(0xFF0B4023),
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF171A21),
    surface = Color(0xFFFCFCFE),
    onSurface = Color(0xFF171A21),
    surfaceVariant = Color(0xFFE4E6EF),
    onSurfaceVariant = Color(0xFF474C5B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F2F8),
    surfaceContainer = Color(0xFFE9ECF4),
    surfaceContainerHigh = Color(0xFFE2E6F0),
    surfaceContainerHighest = Color(0xFFDBE0EC),
    surfaceDim = Color(0xFFD3D8E4),
    surfaceBright = Color(0xFFFCFCFE),
    outline = Color(0xFF8A90A5),
    outlineVariant = Color(0xFFC4C9D9),
    error = Color(0xFFBA1A2E)
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
    // A more confident, high-contrast type scale: headlines get heavier weight and tighter
    // tracking so titles read as *display* type rather than large body text, while body/label
    // roles keep comfortable spacing for readability. This is what makes screens feel designed
    // rather than default-Material, and it propagates to every `MaterialTheme.typography` call.
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
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
