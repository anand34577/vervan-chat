package com.vervan.chat.server

/**
 * Returns the effective API authentication requirement.
 *
 * LAN access and the full browser workspace are data-bearing trust boundaries and therefore
 * always require a bearer token. Only the localhost Basic API surface may be deliberately run
 * without one.
 */
internal fun requiresApiAuth(
    configuredAuth: Boolean,
    allowLan: Boolean,
    fullMode: Boolean,
    appToolsEnabled: Boolean = false,
): Boolean = configuredAuth || allowLan || fullMode || appToolsEnabled

/** A request may narrow the user's app-tool permission, never widen it. */
internal fun effectiveAppToolsEnabled(
    settingEnabled: Boolean,
    requestEnabled: Boolean,
    toolChoiceEnabled: Boolean,
): Boolean = settingEnabled && requestEnabled && toolChoiceEnabled
