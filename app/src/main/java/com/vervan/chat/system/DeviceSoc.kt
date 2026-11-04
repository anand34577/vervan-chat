package com.vervan.chat.system

import android.os.Build

/**
 * Identifies the phone's system-on-chip so LiteRT-LM's NPU path is only attempted, and
 * NPU-compiled model files only offered, where they can actually run.
 *
 * NPU model packages are compiled ahead of time for one chip family (e.g. `sm8750` = Snapdragon
 * 8 Elite, `mt6993` = Dimensity 9500, Tensor G5). Loading one on a different chip fails, and
 * trying the NPU backend with a generic model wastes seconds on every first load, so both
 * decisions key off the same identifiers.
 */
object DeviceSoc {
    enum class Vendor { QUALCOMM, MEDIATEK, GOOGLE_TENSOR, SAMSUNG_EXYNOS, OTHER }

    data class Info(val vendor: Vendor, val model: String)

    val current: Info by lazy { detect(socManufacturer(), socModel(), Build.HARDWARE.orEmpty(), Build.BOARD.orEmpty()) }

    /** Chips that LiteRT's bundled dispatch libraries (Qualcomm QNN, MediaTek NeuroPilot, Google
     * Tensor) can drive. Older generations lack the NPU ops LLM graphs need. */
    val hasLiteRtNpu: Boolean get() = isNpuCapable(current)

    internal fun isNpuCapable(info: Info): Boolean = when (info.vendor) {
        // Snapdragon 8 Gen 2 (SM8550) and newer flagships, plus Snapdragon 8s Gen 3/4 class parts.
        Vendor.QUALCOMM -> qualcommGeneration(info.model)?.let { it >= 8550 } == true
        // Dimensity 8300/9300 (MT6897/MT6989) and newer.
        Vendor.MEDIATEK -> mediatekNumber(info.model)?.let { it >= 6897 } == true
        // Tensor G3 and newer.
        Vendor.GOOGLE_TENSOR -> tensorGeneration(info.model)?.let { it >= 3 } == true
        else -> false
    }

    /** True when [socIds] (catalogue tags such as "sm8750", "mt6993", "tensor-g5") names this chip. */
    fun matchesAny(socIds: Collection<String>): Boolean = socIds.any { matches(current, it) }

    internal fun matches(info: Info, socId: String): Boolean {
        val id = socId.lowercase().replace(" ", "").replace("_", "-")
        val model = info.model.lowercase().replace(" ", "").replace("_", "-")
        return when {
            id.startsWith("tensor-g") || id.startsWith("tensorg") ->
                info.vendor == Vendor.GOOGLE_TENSOR && tensorGeneration(model) == id.filter(Char::isDigit).toIntOrNull()
            else -> model.contains(id)
        }
    }

    /** Heuristic for a model file compiled for an NPU, from the naming litert-community uses. */
    fun looksNpuCompiled(fileName: String): Boolean {
        val n = fileName.lowercase()
        return listOf("npu", "qualcomm", "mediatek", "tensor_g", "tensor-g", "_sm8", ".sm8", "_mt6", ".mt6").any { it in n }
    }

    internal fun detect(manufacturer: String, model: String, hardware: String, board: String): Info {
        val m = model.trim()
        val all = "$manufacturer $m $hardware $board".lowercase()
        val vendor = when {
            "qualcomm" in all || "qcom" in all || Regex("""\bsm\d{4}""").containsMatchIn(all) -> Vendor.QUALCOMM
            "mediatek" in all || Regex("""\bmt\d{4}""").containsMatchIn(all) -> Vendor.MEDIATEK
            "tensor" in all || "google" in manufacturer.lowercase() -> Vendor.GOOGLE_TENSOR
            "exynos" in all || "samsung" in manufacturer.lowercase() -> Vendor.SAMSUNG_EXYNOS
            else -> Vendor.OTHER
        }
        val bestModel = m.ifBlank { hardware.ifBlank { board } }
        return Info(vendor, bestModel)
    }

    private fun qualcommGeneration(model: String): Int? =
        Regex("""sm(\d{4})""", RegexOption.IGNORE_CASE).find(model)?.groupValues?.get(1)?.toIntOrNull()

    private fun mediatekNumber(model: String): Int? =
        Regex("""mt(\d{4})""", RegexOption.IGNORE_CASE).find(model)?.groupValues?.get(1)?.toIntOrNull()

    private fun tensorGeneration(model: String): Int? =
        Regex("""g(\d+)""", RegexOption.IGNORE_CASE).find(model.substringAfter("tensor", model))?.groupValues?.get(1)?.toIntOrNull()

    private fun socManufacturer(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER.orEmpty() else ""

    private fun socModel(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.orEmpty() else ""
}
