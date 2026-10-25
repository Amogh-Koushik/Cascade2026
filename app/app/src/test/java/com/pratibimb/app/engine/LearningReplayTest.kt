package com.pratibimb.app.engine

import com.pratibimb.twin.Category
import com.pratibimb.twin.PhoneState
import com.pratibimb.twin.RealTwinEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LearningReplayTest {
    // Gradle runs unit tests from the module folder; walk up until the repo's contracts/.
    private val contractsDir: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "contracts") }
        .first { File(it, "demo/${LearningReplay.ASSET}").isFile }

    private val events = LearningReplay.parse(File(contractsDir, "demo/${LearningReplay.ASSET}").readText())

    @Test
    fun replayHasThirtySteadyFocusSocialOutcomes() {
        assertEquals(30, events.size)
        assertTrue(events.all { it.category == Category.SOCIAL && it.state == PhoneState.FOCUS_STEADY })
        assertTrue(events.all { it.opened == (it.minutesLost != null) })
    }

    @Test
    fun replayChangesWhatTheEngineHasLearned() {
        val engine = RealTwinEngine()
        engine.loadParams(File(contractsDir, "params/twin_params_student_coder.json").readText())
        val before = engine.exportCounts()

        events.forEach { engine.applyFeedback(it) }

        assertNotEquals(before, engine.exportCounts())
    }

    @Test
    fun everyPersonaHasABundledParamsFile() {
        for (persona in Personas.all) {
            assertTrue(persona.asset, File(contractsDir, "params/${persona.asset}").isFile)
        }
        assertEquals(Personas.default, Personas.find(Personas.default.label))
    }
}
