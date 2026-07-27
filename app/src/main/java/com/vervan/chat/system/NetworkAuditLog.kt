package com.vervan.chat.system

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class NetworkAuditEntry(val timestamp: Long, val reason: String)

/**
 * Network trust dashboard — every intentional network call this app ever makes is
 * meant to call [record] first, so "no silent networking" is something the user can verify in
 * Diagnostics instead of just a claim in a settings screen nobody can check.
 *
 * Current call sites include model and voice downloads, Model Store catalogue/artifact access,
 * and local API-server lifecycle events. Conversation inference itself remains on-device.
 */
class NetworkAuditLog {
    private val _entries = MutableStateFlow<List<NetworkAuditEntry>>(emptyList())
    val entries: StateFlow<List<NetworkAuditEntry>> = _entries

    fun record(reason: String) {
        // Atomic CAS loop instead of read-then-write — both the model downloader and the store
        // pipeline call into this from independent dispatchers, and the previous
        // `_entries.value = (_entries.value + …)` lost one update per concurrent pair.
        _entries.update { (it + NetworkAuditEntry(System.currentTimeMillis(), reason)).takeLast(MAX_ENTRIES) }
    }

    companion object {
        private const val MAX_ENTRIES = 200
    }
}
