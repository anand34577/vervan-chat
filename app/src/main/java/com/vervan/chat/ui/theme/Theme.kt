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
 * Aurora palette â€” the product owns color. Modernist contributes type, spacing, rules, and
 * geometry without replacing the user's chosen theme. No bundled display font (offline-first app, nothing
 * is fetched from Google Fonts) — system sans-serif is tuned with weight/tracking instead.
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
    com.vervan.chat.data.settings.AccentTheme.GREEN to AccentPair(Color(0xFF53E88B), Color(0xFF04240E), Color(0xFF14532D), Color(0xFFBFF2D4), Color(0xFF7C9AFF), Color(0xFF0A1030), Color(0xFF1D2A52), Color(0xFFA9BDFF)),
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
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F2F8),
    surfaceContainer = Color(0xFFE9ECF4),
    surfaceContainerHigh = Color(0xFFE2E6F0),
    surfaceContainerHighest = Color(0xFFDBE0EC),
    surfaceDim = Color(0xFFD3D8E4),
    surfaceBright = Color.White,
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

// Shapes are intentionally quieter than the prototype's black-and-white canvas. Containers
// support grouping and touch affordance; they are not the content of the design.
private val VervanShapes = Shapes(
    extraSmall = RoundedCornerShape(ModernistTokens.Component.radiusXs),
    small = RoundedCornerShape(ModernistTokens.Component.radiusSm),
    medium = RoundedCornerShape(ModernistTokens.Component.radiusMd),
    // Large shape slots feed Material buttons/dialogs. Keep them rectangular enough to match
    // the product's compact control language instead of silently restoring stadium controls.
    large = RoundedCornerShape(ModernistTokens.Component.radiusSm),
    // Material3 uses the larger shape slot for default buttons and several controls. Keep that
    // slot compact too, otherwise an omitted per-button shape silently reintroduces a pill-like
    // control into an otherwise rectangular surface system.
    extraLarge = RoundedCornerShape(ModernistTokens.Component.radiusSm)
)


/** Shapes outside Material3's fixed five-token [Shapes] scale (+ M3 Expressive additions). */
object VervanExtraShapes {
    val hero = RoundedCornerShape(ModernistTokens.Component.radiusXl)
    val composer = RoundedCornerShape(ModernistTokens.Component.radiusLg)
    val userBubble = RoundedCornerShape(
        topStart = ModernistTokens.Component.radiusLg,
        topEnd = ModernistTokens.Component.radiusLg,
        bottomStart = ModernistTokens.Component.radiusLg,
        bottomEnd = ModernistTokens.Component.radiusSm
    )
    val assistantBubble = RoundedCornerShape(
        topStart = ModernistTokens.Component.radiusLg,
        topEnd = ModernistTokens.Component.radiusLg,
        bottomStart = ModernistTokens.Component.radiusSm,
        bottomEnd = ModernistTokens.Component.radiusLg
    )
    // Tags remain compact, but a stadium shape is reserved for genuinely continuous controls.
    // This keeps chips from making every action look like the previous UI's floating button.
    val pill = RoundedCornerShape(ModernistTokens.Component.radiusSm)
    val datePill = RoundedCornerShape(ModernistTokens.Component.radiusSm)
    val extraExtraLarge = RoundedCornerShape(ModernistTokens.Component.radiusXl)
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

private val VervanTypography = Typography().let { base ->
    // A more confident, high-contrast type scale: headlines get heavier weight and tighter
    // tracking so titles read as *display* type rather than large body text, while body/label
    // roles keep comfortable spacing for readability. This is what makes screens feel designed
    // rather than default-Material, and it propagates to every `MaterialTheme.typography` call.
    base.copy(
        displaySmall = base.displaySmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.0).sp),
        headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp),
        headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        titleLarge = base.titleLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        titleMedium = base.titleMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp),
        labelSmall = base.labelSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, letterSpacing = 0.55.sp)
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
        scheme
    } else {
        lightSchemeFor(LightAccents.getValue(accent))
    }
    // Apply the OLED preference after resolving the color source so it also works with Material
    // You dynamic color. Previously the dynamic-color branch returned early from this adjustment,
    // which made the setting appear to do nothing on Android 12+ when device color was enabled.
    if (darkTheme && oledTrueBlack) {
        colorScheme = colorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF121212),
            surfaceContainerLowest = Color.Black,
            surfaceDim = Color.Black,
        )
    }
    if (highContrast) colorScheme = colorScheme.withHighContrast(darkTheme)
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = VervanShapes,
        typography = VervanTypography,
        content = content
    )
}
