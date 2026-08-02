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
        popUpTo(AppRoutes.HOME) { saveState = false }
        launchSingleTop = true
        restoreState = false
    }
}
