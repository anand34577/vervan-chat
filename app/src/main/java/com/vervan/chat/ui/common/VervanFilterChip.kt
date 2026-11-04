package com.vervan.chat.ui.common

import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.Color

/**
 * Unified Vervan-styled FilterChip. The codebase already had `SemanticChip`, `StatusChip`,
 * `IconAffordance`, etc. unified, but `FilterChip` was still using default M3 styling on every
 * call site (ChatListScreen, OnboardingScreen). This single wrapper standardizes the look so
 * FilterChips read as part of the same Vervan design language:
 *  - selected state uses `primaryContainer` plus a check mark, matching the selected NavigationBar
 *    item (never colour alone).
 *  - unselected state uses `surfaceContainerHigh` so chips visually echo the search field and
 *    status chips used elsewhere, instead of `surfaceVariant`'s cooler gray.
 *
 * Pass-through API so existing FilterChip call sites flip to this with one rename.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun VervanFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon ?: if (selected) ({
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize)
            )
        }) else null,
        trailingIcon = trailingIcon,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}
