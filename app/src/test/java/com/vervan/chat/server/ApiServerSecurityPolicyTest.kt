package com.vervan.chat.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiServerSecurityPolicyTest {
    @Test
    fun `localhost basic mode may honor deliberate no-auth configuration`() {
        assertFalse(requiresApiAuth(configuredAuth = false, allowLan = false, fullMode = false))
    }

    @Test
    fun `configured authentication is honored in basic localhost mode`() {
        assertTrue(requiresApiAuth(configuredAuth = true, allowLan = false, fullMode = false))
    }

    @Test
    fun `LAN access always requires authentication`() {
        assertTrue(requiresApiAuth(configuredAuth = false, allowLan = true, fullMode = false))
    }

    @Test
    fun `full web workspace always requires authentication`() {
        assertTrue(requiresApiAuth(configuredAuth = false, allowLan = false, fullMode = true))
    }

    @Test
    fun `full web workspace on LAN always requires authentication`() {
        assertTrue(requiresApiAuth(configuredAuth = false, allowLan = true, fullMode = true))
    }

    @Test
    fun `app tools always require authentication`() {
        assertTrue(requiresApiAuth(false, false, false, appToolsEnabled = true))
    }

    @Test
    fun `request cannot enable app tools disabled in settings`() {
        assertFalse(effectiveAppToolsEnabled(false, requestEnabled = true, toolChoiceEnabled = true))
    }

    @Test
    fun `request may disable globally enabled app tools`() {
        assertFalse(effectiveAppToolsEnabled(true, requestEnabled = false, toolChoiceEnabled = true))
    }

    @Test
    fun `app tools require all three gates`() {
        assertTrue(effectiveAppToolsEnabled(true, requestEnabled = true, toolChoiceEnabled = true))
        assertFalse(effectiveAppToolsEnabled(true, requestEnabled = true, toolChoiceEnabled = false))
    }
}
