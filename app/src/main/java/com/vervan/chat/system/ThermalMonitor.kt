package com.vervan.chat.system

import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThermalLevel { NORMAL, ELEVATED, SEVERE }

/**
 * Wraps [PowerManager]'s thermal status API (API 29+) so the UI can warn the user that
 * generation may run slower — spec §40's thermal-state edge case. No-ops below API 29 or on
 * a device that never reports non-NORMAL status; [level] just stays NORMAL, never a false
 * alarm. ponytail: read-only monitoring + a passive banner, no automatic throttling of
 * generation itself — that would need real benchmarking to tune sensibly.
 */
class ThermalMonitor(context: Context) {
    private val _level = MutableStateFlow(ThermalLevel.NORMAL)
    val level: StateFlow<ThermalLevel> = _level

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.let { pm ->
                _level.value = toLevel(pm.currentThermalStatus)
                pm.addThermalStatusListener { status -> _level.value = toLevel(status) }
            }
        }
    }

    private fun toLevel(status: Int): ThermalLevel = when {
        status >= PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
        status >= PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.ELEVATED
        else -> ThermalLevel.NORMAL
    }
}
