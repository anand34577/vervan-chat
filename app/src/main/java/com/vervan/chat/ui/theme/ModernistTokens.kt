package com.vervan.chat.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Vervan's Modernist visual language, expressed as primitive -> semantic -> component tokens.
 *
 * The prototype contributes structure, not a forced palette. Color remains semantic and is
 * resolved through [MaterialTheme.colorScheme], so Aurora, dynamic color, light/dark mode, and
 * accessibility overrides continue to work everywhere.
 */
object ModernistTokens {
    object Primitive {
        val unit = 4.dp
        // The prototype's geometry was useful as a reference, but its Android translation
        // over-rounded almost every surface. Shapes now have a stronger hierarchy: controls
        // are compact, modules are calm, and only the composer/hero gets a visibly soft frame.
        // The product uses a compact 4/8/12/16/20 scale. Stadium shapes are reserved for
        // continuous controls; app modules and icon badges never use the pill token.
        // A restrained radius ladder keeps list rows, fields, and cards visibly related.
        // Only hero/composer surfaces use the largest value; controls never become stadiums.
        // A calm radius ladder: small controls feel intentional, while hero/composer
        // surfaces carry the softer signature of the product.
        val radiusXs = 4.dp
        val radiusSm = 10.dp
        val radiusMd = 14.dp
        val radiusLg = 18.dp
        val radiusXl = 24.dp
        val radiusPill = 999.dp
        /** Compatibility alias for older call sites; new components use the named radii. */
        val radius = radiusMd
        val rule = 1.dp
        val innerRule = 1.dp
        val phoneGutter = 16.dp
        val tabletGutter = 24.dp
        val sectionGap = 20.dp
    }

    object Semantic {
        val radius = Primitive.radius
        val rule = Primitive.rule
        val innerRule = Primitive.innerRule
        val phoneGutter = Primitive.phoneGutter
        val tabletGutter = Primitive.tabletGutter
        val sectionGap = Primitive.sectionGap
    }

    object Component {
        val radius = Semantic.radius
        val radiusXs = Primitive.radiusXs
        val radiusSm = Primitive.radiusSm
        val radiusMd = Primitive.radiusMd
        val radiusLg = Primitive.radiusLg
        val radiusXl = Primitive.radiusXl
        val radiusPill = Primitive.radiusPill
        val rule = Semantic.rule
        val innerRule = Semantic.innerRule
        val phoneGutter = Semantic.phoneGutter
        val tabletGutter = Semantic.tabletGutter
        val sectionGap = Semantic.sectionGap
        val minTouchTarget = 48.dp
        // Toggle geometry: the visual track is compact, while the transparent wrapper keeps
        // the control accessible without introducing a second visible frame around it.
        val toggleTrackWidth = 48.dp
        val toggleTrackHeight = 28.dp
        val toggleThumbSize = 24.dp
        val toggleThumbOffset = 20.dp
        val toggleTrackInset = 2.dp
    }

    /** Layout tokens describe behavior, not just dimensions: rows are the primary navigation
     * primitive, while modules are deliberately quieter than floating overlays. */
    object Layout {
        // Collection rows reserve one stable two-line rhythm. Callers can still opt into a
        // larger module, but list scanning should not jump when optional metadata appears.
        // One compact rhythm for list rows across chats, library, settings, and tools.
        // 72dp preserves a comfortable touch target without introducing dead vertical space.
        val rowMinHeight = 72.dp
        val compactRowMinHeight = 56.dp
        // The dock includes a real primary action button, so reserve enough vertical room for
        // the 44dp target plus its label without clipping or relying on a tiny hit area.
        val bottomNavigationHeight = 88.dp
        val bottomNavigationItemHeight = 76.dp
        val navigationRailItemHeight = 72.dp
        val moduleInset = 16.dp
        val maxReadingWidth = 720.dp
    }

    object Interaction {
        val minimumTouchTarget = 48.dp
        val primaryRule = 2.dp
        val focusOffset = 2.dp
    }

    object Motion {
        const val fastMillis = 120
        const val standardMillis = 220
        const val deliberateMillis = 360
    }
}
