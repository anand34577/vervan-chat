package com.vervan.chat.data.db.entities

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTraitsTest {

    /** The point of the table is that a new engine has to answer every question deliberately.
     *  A blank label means someone added an entry without filling it in. */
    @Test
    fun everyEngineDeclaresATable() {
        ModelEngine.entries.forEach { engine ->
            assertTrue("$engine has no label", engine.traits.label.isNotBlank())
        }
    }

    /** Guards the invariant behind the bugs this table exists to prevent: an engine with no local
     *  weights has no file size to show, no hardware backend to report, and nothing to feature-test,
     *  so its capabilities must come from the user. If a future engine genuinely breaks this pairing
     *  (a remote runtime that does report hardware), that's a deliberate change to make here. */
    @Test
    fun offDeviceEnginesDeclareTheirCapabilitiesAndOwnNoWeights() {
        ModelEngine.entries.filterNot { it.traits.runsOnDevice }.forEach { engine ->
            assertFalse("$engine runs off-device but claims local weights", engine.traits.storesWeightsLocally)
            assertTrue("$engine runs off-device but doesn't declare capabilities", engine.traits.capabilitiesUserDeclared)
            assertFalse("$engine runs off-device but claims native tuning", engine.traits.hasNativeTuningKnobs)
        }
    }

    @Test
    fun remoteApiIsTheOffDeviceEngine() {
        assertFalse(ModelEngine.REMOTE_API.traits.runsOnDevice)
        assertTrue(ModelEngine.LITERT_LM.traits.runsOnDevice)
        assertTrue(ModelEngine.LLAMA_CPP.traits.runsOnDevice)
    }
}
