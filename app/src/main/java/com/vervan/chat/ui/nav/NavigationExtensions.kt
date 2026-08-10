package com.vervan.chat.ui.nav

import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController

/**
 * Keeps top-level navigation stack policy out of the destination registry. Primary destinations
 * are flat peers, so switching tabs replaces the current peer and never restores a stale child
 * above Home.
 */
internal fun NavHostController.navigatePrimaryRoot(route: String) {
    if (currentDestination?.hierarchy?.any { it.route == route } == true) return
    if (route == AppRoutes.HOME) {
        popBackStack(AppRoutes.HOME, inclusive = false)
        return
    }
    navigate(route) {
        // Preserve each primary destination's scroll, filter, pager, and detail state. The
        // previous policy deliberately discarded it, which made switching tabs feel like the
        // app forgot the user's place every time. The shell still keeps one copy of each root
        // because launchSingleTop prevents duplicate destinations.
        popUpTo(AppRoutes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
