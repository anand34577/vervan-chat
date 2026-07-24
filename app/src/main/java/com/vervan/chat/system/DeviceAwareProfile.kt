package com.vervan.chat.system

import android.content.Context
import android.os.PowerManager
import com.vervan.chat.VervanApp
import com.vervan.chat.llm.ModelProfileType
import kotlinx.coroutines.flow.first

/** Downgrades workload only when Android or the thermal monitor signals genuine pressure. */
object DeviceAwareProfile {
    suspend fun resolve(app: VervanApp, requested: ModelProfileType): ModelProfileType {
        if (!app.container.settingsRepository.deviceAwarePerformance.first()) return requested
        val powerManager = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        return when {
            app.container.thermalMonitor.level.value == ThermalLevel.SEVERE -> ModelProfileType.THERMAL_SAFE
            powerManager.isPowerSaveMode -> ModelProfileType.BATTERY_SAVER
            else -> requested
        }
    }
}
