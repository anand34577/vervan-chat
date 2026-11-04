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
 * Fixed palette: calm graphite neutrals in dark mode and soft paper neutrals in light mode, so
 * the conversation (not the chrome) carries the colour, with one accent for actions. Not
 * Material You dynamic colour unless the user turns it on. Typography uses the bundled Manrope
 * variable font (OFL, see assets/licenses) so the app looks the same on every phone without
 * fetching anything; system monospace carries metadata.
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
private data class AccentPair(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color
)

private val DarkAccents = mapOf(
    com.vervan.chat.data.settings.AccentTheme.AMBER to AccentPair(Color(0xFFF6B24E), Color(0xFF221402), Color(0xFF503A12), Color(0xFFFFE1B0), Color(0xFF7C9AFF), Color(0xFF0A1030), Color(0xFF1D2A52), Color(0xFFA9BDFF)),
    com.vervan.chat.data.settings.AccentTheme.BLUE to AccentPair(Color(0xFF7C9AFF), Color(0xFF0A1030), Color(0xFF24356B), Color(0xFFDDE4FF), Color(0xFFF6B24E), Color(0xFF221402), Color(0xFF503A12), Color(0xFFFFD9A0)),
    com.vervan.chat.data.settings.AccentTheme.GREEN to AccentPair(Color(0xFF5FD69C), Color(0xFF03291A), Color(0xFF1A4D35), Color(0xFFC4F1DA), Color(0xFF7C9AFF), Color(0xFF0A1030), Color(0xFF1D2A52), Color(0xFFA9BDFF)),
    com.vervan.chat.data.settings.AccentTheme.VIOLET to AccentPair(Color(0xFFA78BFA), Color(0xFF190E33), Color(0xFF3C2A6E), Color(0xFFE5DBFF), Color(0xFF53E88B), Color(0xFF04240E), Color(0xFF14532D), Color(0xFFA7F3C4)),
    com.vervan.chat.data.settings.AccentTheme.ROSE to AccentPair(Color(0xFFFB7185), Color(0xFF33060E), Color(0xFF6B1D2C), Color(0xFFFFD7E2), Color(0xFF7C9AFF), Color(0xFF0A1030), Color(0xFF1D2A52), Color(0xFFA9BDFF))
)

private val LightAccents = mapOf(
    com.vervan.chat.data.settings.AccentTheme.AMBER to AccentPair(Color(0xFF9A6400), Color.White, Color(0xFFFFE1B0), Color(0xFF332000), Color(0xFF3D5FE0), Color.White, Color(0xFFDDE4FF), Color(0xFF14224F)),
    com.vervan.chat.data.settings.AccentTheme.BLUE to AccentPair(Color(0xFF3D5FE0), Color.White, Color(0xFFDDE4FF), Color(0xFF14224F), Color(0xFFA16207), Color.White, Color(0xFFFFE1B0), Color(0xFF4A3005)),
    com.vervan.chat.data.settings.AccentTheme.GREEN to AccentPair(Color(0xFF0F7A3D), Color.White, Color(0xFFBFF2D4), Color(0xFF0B4023), Color(0xFF3D5FE0), Color.White, Color(0xFFDDE4FF), Color(0xFF14224F)),
    com.vervan.chat.data.settings.AccentTheme.VIOLET to AccentPair(Color(0xFF6D46D6), Color.White, Color(0xFFE5DBFF), Color(0xFF25104D), Color(0xFF157F45), Color.White, Color(0xFFBFF2D4), Color(0xFF0B4023)),
    com.vervan.chat.data.settings.AccentTheme.ROSE to AccentPair(Color(0xFFB72B5D), Color.White, Color(0xFFFFD7E2), Color(0xFF4D0C24), Color(0xFF3D5FE0), Color.White, Color(0xFFDDE4FF), Color(0xFF14224F))
)

private fun darkSchemeFor(accent: AccentPair) = darkColorScheme(
    primary = accent.primary,
    onPrimary = accent.onPrimary,
    primaryContainer = accent.primaryContainer,
    onPrimaryContainer = accent.onPrimaryContainer,
    secondary = accent.secondary,
    onSecondary = accent.onSecondary,
    secondaryContainer = accent.secondaryContainer,
    onSecondaryContainer = accent.onSecondaryContainer,
    tertiary = VervanSuccess,
    onTertiary = Color(0xFF04240E),
    tertiaryContainer = Color(0xFF14532D),
    onTertiaryContainer = Color(0xFFA7F3C4),
    background = Color(0xFF0E1013),
    onBackground = Color(0xFFECEEF2),
    surface = Color(0xFF0E1013),
    onSurface = Color(0xFFECEEF2),
    surfaceVariant = Color(0xFF353A44),
    onSurfaceVariant = Color(0xFFBFC4CE),
    surfaceContainerLowest = Color(0xFF08090B),
    surfaceContainerLow = Color(0xFF15171B),
    surfaceContainer = Color(0xFF1A1D22),
    surfaceContainerHigh = Color(0xFF22262C),
    surfaceContainerHighest = Color(0xFF2B3038),
    surfaceDim = Color(0xFF08090B),
    surfaceBright = Color(0xFF33383F),
    outline = Color(0xFF5B616D),
    outlineVariant = Color(0xFF353A44),
    inverseSurface = Color(0xFFEDF0F7),
    inverseOnSurface = Color(0xFF171A21),
    inversePrimary = accent.primaryContainer,
    error = DangerRed,
    onError = Color(0xFF2A0F14),
    errorContainer = Color(0xFF2A0F14),
    onErrorContainer = DangerRed,
    scrim = Color.Black
)

private fun lightSchemeFor(accent: AccentPair) = lightColorScheme(
    primary = accent.primary,
    onPrimary = accent.onPrimary,
    primaryContainer = accent.primaryContainer,
    onPrimaryContainer = accent.onPrimaryContainer,
    secondary = accent.secondary,
    onSecondary = accent.onSecondary,
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
    inverseSurface = Color(0xFF30313A),
    inverseOnSurface = Color(0xFFF4F1F7),
    inversePrimary = accent.primaryContainer,
    error = Color(0xFFBA1A2E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color.Black
)

// One proportional corner scale: the radius grows with the element, so a small thing inside a
// bigger thing always reads as nested rather than as a competing shape.
//   extraSmall  6  tags, badges, inline code, small thumbnails (≤ 24dp tall)
//   small      10  icon tiles, text fields, menus, inner tiles that sit inside a card
//   medium     16  every content card (Material's default Card shape)
//   large      16  custom cards that ask for "large"; the same 16 so all cards match
//   extraLarge 28  dialogs, bottom sheets
// Controls people press (buttons, chips, the composer, segmented filters) are full pills, which
// is radius = height / 2 and so proportional by definition. Nested rule: an element inset by
// padding p inside a container of radius r uses roughly r - p, which is why inner tiles are 10.
private val VervanShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Shapes outside Material3's five-token [Shapes] scale, on the same proportional rules. */
object VervanExtraShapes {
    /** Large standalone panels (empty states, previews); one step above a card. */
    val hero = RoundedCornerShape(20.dp)
    /** The composer is a pill at its resting 56dp height and keeps that radius as it grows. */
    val composer = RoundedCornerShape(28.dp)
    // The small "tail" corner keeps the bubble's direction cue; the rest match a card's radius
    // plus a little, since bubbles are the conversation's primary surface.
    val userBubble = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
    val assistantBubble = RoundedCornerShape(16.dp)
    /** Full pill for chips, quick replies, filters, and pressable controls. */
    val pill = RoundedCornerShape(100.dp)
    val datePill = RoundedCornerShape(100.dp)
    /** Same as dialogs and sheets. */
    val extraExtraLarge = RoundedCornerShape(28.dp)
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
    val xxxl = 32.dp
    val huge = 48.dp
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun manrope(weight: Int) = androidx.compose.ui.text.font.Font(
    com.vervan.chat.R.font.manrope_variable,
    weight = FontWeight(weight),
    variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(weight))
)

val VervanSans = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

private val VervanTypography = Typography().let { base0 ->
    val base = Typography(
        displayLarge = base0.displayLarge.copy(fontFamily = VervanSans),
        displayMedium = base0.displayMedium.copy(fontFamily = VervanSans),
        displaySmall = base0.displaySmall.copy(fontFamily = VervanSans),
        headlineLarge = base0.headlineLarge.copy(fontFamily = VervanSans),
        headlineMedium = base0.headlineMedium.copy(fontFamily = VervanSans),
        headlineSmall = base0.headlineSmall.copy(fontFamily = VervanSans),
        titleLarge = base0.titleLarge.copy(fontFamily = VervanSans),
        titleMedium = base0.titleMedium.copy(fontFamily = VervanSans),
        titleSmall = base0.titleSmall.copy(fontFamily = VervanSans),
        bodyLarge = base0.bodyLarge.copy(fontFamily = VervanSans, lineHeight = 26.sp),
        bodyMedium = base0.bodyMedium.copy(fontFamily = VervanSans, lineHeight = 22.sp),
        bodySmall = base0.bodySmall.copy(fontFamily = VervanSans),
        labelLarge = base0.labelLarge.copy(fontFamily = VervanSans),
        labelMedium = base0.labelMedium.copy(fontFamily = VervanSans),
        labelSmall = base0.labelSmall.copy(fontFamily = VervanSans)
    )
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
        labelSmall = base.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
    )
}

/** The swatch color Settings shows for each accent option — always the dark-mode primary,
 * since that reads clearly on both a light and dark settings row. */
fun com.vervan.chat.data.settings.AccentTheme.swatchColor(): Color = DarkAccents.getValue(this).primary

/** high-contrast pass — applied on top of any resolved scheme, independent of accent/theme.
 * A hand-picked custom palette (not Material's dynamic contrast API, which only applies to
 * dynamic/harmonized schemes) so it works the same for both accent and Material You colors:
 * pulls muted text/borders to full-strength so state is never conveyed by a faint tint alone. */
private fun ColorScheme.withHighContrast(darkTheme: Boolean): ColorScheme {
    val contrastOutline = if (darkTheme) Color(0xFFB8BFCC) else Color(0xFF5C5F66)
    return copy(
        onSurfaceVariant = onSurface,
        outline = contrastOutline,
        outlineVariant = contrastOutline
    )
}

@Composable
fun VervanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    oledTrueBlack: Boolean = false,
    dynamicColor: Boolean = false,
    highContrast: Boolean = false,
    accent: com.vervan.chat.data.settings.AccentTheme = com.vervan.chat.data.settings.AccentTheme.GREEN,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) {
        val scheme = darkSchemeFor(DarkAccents.getValue(accent))
        // OLED true-black variant — same accent, background/surface pushed
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
