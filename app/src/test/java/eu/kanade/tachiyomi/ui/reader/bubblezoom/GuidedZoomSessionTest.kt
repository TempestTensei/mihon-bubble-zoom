package eu.kanade.tachiyomi.ui.reader.bubblezoom

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GuidedZoomSessionTest {

    private val r0 = Region(0f, 0.00f, 1f, 0.30f)
    private val r1 = Region(0f, 0.35f, 1f, 0.60f)
    private val r2 = Region(0f, 0.65f, 1f, 1.00f)

    private fun session(vararg regions: Region) = GuidedZoomSession(regions.toList())

    @Test
    fun `enterFromStart on empty regions returns FullPage`() {
        session().enterFromStart() shouldBe StepResult.FullPage
    }

    @Test
    fun `enterFromEnd on empty regions returns FullPage`() {
        session().enterFromEnd() shouldBe StepResult.FullPage
    }

    @Test
    fun `forward on empty regions returns CrossPage`() {
        session().forward() shouldBe StepResult.CrossPage
    }

    @Test
    fun `enterFromStart then forward walks to the last region then crosses the page`() {
        val s = session(r0, r1, r2)
        s.enterFromStart() shouldBe StepResult.Zoom(r0, 0)
        s.forward() shouldBe StepResult.Zoom(r1, 1)
        s.forward() shouldBe StepResult.Zoom(r2, 2)
        s.forward() shouldBe StepResult.CrossPage
    }

    @Test
    fun `enterFromEnd then backward walks to the first region then crosses the page`() {
        val s = session(r0, r1, r2)
        s.enterFromEnd() shouldBe StepResult.Zoom(r2, 2)
        s.backward() shouldBe StepResult.Zoom(r1, 1)
        s.backward() shouldBe StepResult.Zoom(r0, 0)
        s.backward() shouldBe StepResult.CrossPage
    }

    @Test
    fun `backward from the first region crosses the page`() {
        val s = session(r0, r1)
        s.enterFromStart() shouldBe StepResult.Zoom(r0, 0)
        s.backward() shouldBe StepResult.CrossPage
    }

    @Test
    fun `forward from a fresh session zooms the first region`() {
        val s = session(r0, r1)
        s.forward() shouldBe StepResult.Zoom(r0, 0)
    }
}
