package eu.kanade.tachiyomi.ui.reader.bubblezoom

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.abs

class PanelPostprocessorTest {

    @Test
    fun `computeLetterbox pads the shorter side`() {
        val lb = PanelPostprocessor.computeLetterbox(1000, 500, 1024)
        (abs(lb.scale - 1.024f) < 1e-3f) shouldBe true
        (abs(lb.padX - 0f) < 1e-3f) shouldBe true
        (abs(lb.padY - 256f) < 1e-1f) shouldBe true
    }

    @Test
    fun `keeps only the panel class and undoes letterbox`() {
        // 1000x500 page, letterbox to 1024: scale 1.024, padY 256
        val lb = PanelPostprocessor.computeLetterbox(1000, 500, 1024)
        // row0: a panel (class 0) spanning the whole page in model coords
        // model x = pageX*scale + padX; y = pageY*scale + padY
        // full page: x 0..1024, y 256..768
        val output = floatArrayOf(
            0f, 256f, 1024f, 768f, 0.95f, 0f, // panel, full page
            100f, 300f, 200f, 400f, 0.9f, 1f, // text class -> dropped
            100f, 300f, 200f, 400f, 0.1f, 0f, // panel but low score -> dropped
        )

        val panels = PanelPostprocessor.toPanels(
            output,
            count = 3,
            letterbox = lb,
            origWidth = 1000,
            origHeight = 500,
            scoreThreshold = 0.5f,
        )

        panels shouldHaveSize 1
        panels[0].left shouldBe 0f
        panels[0].top shouldBe 0f
        (abs(panels[0].right - 1f) < 1e-2f) shouldBe true
        (abs(panels[0].bottom - 1f) < 1e-2f) shouldBe true
    }

    @Test
    fun `clamps and skips degenerate boxes`() {
        val lb = PanelPostprocessor.computeLetterbox(800, 800, 1024)
        val output = floatArrayOf(
            -100f, -100f, 5000f, 5000f, 0.9f, 0f, // out of range -> clamps to full page
            500f, 500f, 500f, 500f, 0.9f, 0f, // zero area -> skipped
        )
        val panels = PanelPostprocessor.toPanels(output, 2, lb, 800, 800, 0.5f)
        panels shouldHaveSize 1
        (panels[0].left >= 0f && panels[0].top >= 0f && panels[0].right <= 1f && panels[0].bottom <= 1f) shouldBe true
    }
}
