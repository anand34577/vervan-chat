package com.vervan.chat.store.eligibility

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.vervan.chat.store.model.AcceleratorRequirement
import com.vervan.chat.store.model.ArtifactRole
import com.vervan.chat.store.model.ModelVariant

/**
 * What this device can actually offer. Probed once per session (see [DeviceProfile.probe]) because
 * none of it changes while the process lives, and [ActivityManager.getMemoryInfo] is not free.
 */
data class DeviceProfile(
    val supportedAbis: List<String>,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val hasGpuDelegate: Boolean,
    val hasNpuDelegate: Boolean,
    val appVersionCode: Int
) {
    companion object {
        fun probe(
            context: Context,
            appVersionCode: Int,
            hasGpuDelegate: Boolean,
            hasNpuDelegate: Boolean
        ): DeviceProfile {
            val memory = ActivityManager.MemoryInfo().also {
                context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
            }
            return DeviceProfile(
                supportedAbis = Build.SUPPORTED_ABIS.toList(),
                totalRamBytes = memory.totalMem,
                availableRamBytes = memory.availMem,
                hasGpuDelegate = hasGpuDelegate,
                hasNpuDelegate = hasNpuDelegate,
                appVersionCode = appVersionCode
            )
        }
    }
}

/**
 * The verdict shown next to a variant *before* the user can tap install — the whole
 * point is that incompatibility is never discovered after a multi-gigabyte download.
 *
 * [DEGRADED] is a deliberate third state rather than folding into installable/incompatible: a
 * variant that wants a GPU on a CPU-only device will run, just slowly, and silently hiding it
 * would be worse than letting the user make an informed choice.
 */
enum class EligibilityVerdict { INSTALLABLE, DEGRADED, INCOMPATIBLE }

data class EligibilityResult(
    val verdict: EligibilityVerdict,
    /** Human-readable, ordered most-important-first. Empty when [INSTALLABLE]. */
    val reasons: List<String>
) {
    val canInstall: Boolean get() = verdict != EligibilityVerdict.INCOMPATIBLE
}

/**
 * Checks a variant's requirements block against a probed device.
 *
 * The RAM check is the subtle one. A variant's `estimatedMinRamBytes` is published against its
 * *default* context size, but the user can raise the context before loading, and KV cache growth
 * is linear in context — so the caller passes the context it actually intends to use and the
 * estimate is scaled accordingly. Comparing a user's 32k-context intent against a 4k-context
 * estimate is exactly how a model gets installed and then OOMs on first load.
 */
class VariantEligibilityChecker(private val device: DeviceProfile) {

    fun check(variant: ModelVariant, intendedContextTokens: Int? = null): EligibilityResult {
        val blockers = mutableListOf<String>()
        val degradations = mutableListOf<String>()

        if (variant.requirements.minAppVersionCode > device.appVersionCode) {
            blockers += "Requires a newer version of the app"
        }

        // An empty supportedAbis set means "no ABI constraint", not "no ABI supported" — a pure
        // data model (an espeak dictionary, say) is architecture-independent.
        val requiredAbis = variant.requirements.supportedAbis
        if (requiredAbis.isNotEmpty() && device.supportedAbis.none { it in requiredAbis }) {
            blockers += "Not supported on this device's CPU (${device.supportedAbis.firstOrNull() ?: "unknown"})"
        }

        val neededRam = scaledRamRequirement(variant, intendedContextTokens)
        if (neededRam > 0 && neededRam > device.totalRamBytes) {
            blockers += "Needs ${formatBytes(neededRam)} of RAM; this device has ${formatBytes(device.totalRamBytes)}"
        } else if (neededRam > 0 && neededRam > device.availableRamBytes) {
            // Fits the device but not right now — closing other apps genuinely fixes this, so it
            // must not read as a hard incompatibility.
            degradations += "May not load until other apps are closed " +
                "(${formatBytes(device.availableRamBytes)} free of ${formatBytes(neededRam)} needed)"
        }

        when (variant.requirements.acceleratorRequirement) {
            AcceleratorRequirement.NPU ->
                if (!device.hasNpuDelegate) blockers += "Requires an NPU this device does not expose"
            AcceleratorRequirement.GPU ->
                if (!device.hasGpuDelegate) degradations += "No GPU acceleration available — will run on CPU, slowly"
            AcceleratorRequirement.NONE -> Unit
        }

        // Schema parity role that cannot be satisfied on Android at all. Catching it here rather
        // than at parse time keeps the reason explainable to the user.
        if (variant.artifacts.any { it.role == ArtifactRole.COREML_ENCODER }) {
            blockers += "This variant is built for Core ML, which Android cannot run"
        }

        return when {
            blockers.isNotEmpty() -> EligibilityResult(EligibilityVerdict.INCOMPATIBLE, blockers + degradations)
            degradations.isNotEmpty() -> EligibilityResult(EligibilityVerdict.DEGRADED, degradations)
            else -> EligibilityResult(EligibilityVerdict.INSTALLABLE, emptyList())
        }
    }

    /**
     * Scales the published RAM estimate from the variant's default context to the context the user
     * intends. Weights are a fixed cost and the KV cache is the part that scales, but the catalogue
     * publishes one combined number — so we attribute the *difference* in context to the scalable
     * portion and leave the rest alone, rather than naively multiplying the whole estimate (which
     * would claim a 2 GB model needs 16 GB at 8x context, wildly over-rejecting).
     */
    private fun scaledRamRequirement(variant: ModelVariant, intendedContextTokens: Int?): Long {
        val base = variant.requirements.estimatedMinRamBytes
        val defaultContext = variant.defaultContextTokens
        if (base <= 0 || intendedContextTokens == null || defaultContext == null || defaultContext <= 0) {
            return base
        }
        if (intendedContextTokens <= defaultContext) return base

        // Weights dominate the estimate; treat the portion above the on-disk size as the
        // context-proportional part, since that is what the KV cache actually is.
        val weightBytes = variant.totalSizeBytes.coerceAtMost(base)
        val kvAtDefault = (base - weightBytes).coerceAtLeast(0L)
        val ratio = intendedContextTokens.toDouble() / defaultContext.toDouble()
        return weightBytes + (kvAtDefault * ratio).toLong()
    }

    private fun formatBytes(bytes: Long): String {
        val gib = bytes / (1024.0 * 1024 * 1024)
        return if (gib >= 1) "%.1f GB".format(gib) else "%.0f MB".format(bytes / (1024.0 * 1024))
    }
}
