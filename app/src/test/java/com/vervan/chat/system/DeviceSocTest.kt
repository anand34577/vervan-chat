package com.vervan.chat.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSocTest {
    @Test fun `detects vendors from SoC fields`() {
        assertEquals(DeviceSoc.Vendor.QUALCOMM, DeviceSoc.detect("QTI", "SM8750", "qcom", "sun").vendor)
        assertEquals(DeviceSoc.Vendor.MEDIATEK, DeviceSoc.detect("Mediatek", "MT6897", "mt6897", "").vendor)
        assertEquals(DeviceSoc.Vendor.GOOGLE_TENSOR, DeviceSoc.detect("Google", "Tensor G5", "", "").vendor)
    }

    @Test fun `npu capability follows chip generation`() {
        assertTrue(DeviceSoc.isNpuCapable(DeviceSoc.Info(DeviceSoc.Vendor.QUALCOMM, "SM8750")))
        assertTrue(DeviceSoc.isNpuCapable(DeviceSoc.Info(DeviceSoc.Vendor.QUALCOMM, "SM8550")))
        assertFalse(DeviceSoc.isNpuCapable(DeviceSoc.Info(DeviceSoc.Vendor.QUALCOMM, "SM7325")))
        assertTrue(DeviceSoc.isNpuCapable(DeviceSoc.Info(DeviceSoc.Vendor.MEDIATEK, "MT6897")))
        assertFalse(DeviceSoc.isNpuCapable(DeviceSoc.Info(DeviceSoc.Vendor.MEDIATEK, "MT6833")))
        assertTrue(DeviceSoc.isNpuCapable(DeviceSoc.Info(DeviceSoc.Vendor.GOOGLE_TENSOR, "Tensor G4")))
        assertFalse(DeviceSoc.isNpuCapable(DeviceSoc.Info(DeviceSoc.Vendor.OTHER, "Unisoc T606")))
    }

    @Test fun `catalogue soc tags match only their chip`() {
        val elite = DeviceSoc.Info(DeviceSoc.Vendor.QUALCOMM, "SM8750")
        assertTrue(DeviceSoc.matches(elite, "sm8750"))
        assertFalse(DeviceSoc.matches(elite, "sm8650"))
        val g5 = DeviceSoc.Info(DeviceSoc.Vendor.GOOGLE_TENSOR, "Tensor G5")
        assertTrue(DeviceSoc.matches(g5, "tensor-g5"))
        assertFalse(DeviceSoc.matches(g5, "tensor-g6"))
    }

    @Test fun `npu compiled file names are recognised`() {
        assertTrue(DeviceSoc.looksNpuCompiled("gemma-4-E2B-it_qualcomm_sm8750.litertlm"))
        assertTrue(DeviceSoc.looksNpuCompiled("Qwen3-0.6B.mediatek.mt6993.litertlm"))
        assertTrue(DeviceSoc.looksNpuCompiled("gemma-4-E2B-it_Google_Tensor_G5.litertlm"))
        assertFalse(DeviceSoc.looksNpuCompiled("gemma-4-E2B-it.litertlm"))
        assertFalse(DeviceSoc.looksNpuCompiled("qwen3_0_6b_mixed_int4.litertlm"))
    }
}
