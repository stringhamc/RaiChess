package com.raichess.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurriculumTest {

    @Test
    fun `steps ascend by floor rating from zero`() {
        assertEquals(0, Curriculum.STEPS.first().floorRating)
        assertTrue(
            Curriculum.STEPS.zipWithNext().all { (a, b) -> a.floorRating < b.floorRating }
        )
    }

    @Test
    fun `unit ids are unique, stable-prefixed, with themes and intros`() {
        val units = Curriculum.STEPS.flatMap { it.units }
        assertEquals(units.size, units.map { it.id }.toSet().size)
        Curriculum.STEPS.forEach { step ->
            step.units.forEach { unit ->
                assertTrue("id ${unit.id}", unit.id.startsWith("${step.id}:"))
                assertTrue("themes ${unit.id}", unit.themes.isNotEmpty())
                assertTrue("intro ${unit.id}", !unit.intro.isNullOrBlank())
            }
        }
    }

    @Test
    fun `rating band picks the entry step`() {
        assertEquals("step1", Curriculum.stepForRating(400).id)
        assertEquals("step1", Curriculum.stepForRating(799).id)
        assertEquals("step2", Curriculum.stepForRating(800).id)
        assertEquals("step3", Curriculum.stepForRating(1250).id)
        assertEquals("step4", Curriculum.stepForRating(2000).id)
    }

    @Test
    fun `a completed step advances the active step`() {
        val step1 = Curriculum.STEPS[0]
        val step1Done = step1.units.associate { it.id to it.targetSolves }
        assertEquals("step1", Curriculum.activeStep(500, emptyMap()).id)
        assertEquals("step2", Curriculum.activeStep(500, step1Done).id)
    }

    @Test
    fun `a strong newcomer starts at their band, not step one`() {
        assertEquals("step3", Curriculum.activeStep(1300, emptyMap()).id)
    }

    @Test
    fun `everything complete stays on the last step`() {
        val allDone = Curriculum.STEPS.flatMap { it.units }
            .associate { it.id to it.targetSolves }
        assertEquals(Curriculum.STEPS.last().id, Curriculum.activeStep(600, allDone).id)
        assertTrue(Curriculum.isComplete(Curriculum.STEPS.last(), allDone))
    }
}
