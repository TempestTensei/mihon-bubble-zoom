package eu.kanade.tachiyomi.ui.reader.bubblezoom

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SpeechBubbleDetectorTest {

    private val detector = SpeechBubbleDetector()

    private fun blank(w: Int, h: Int) = IntArray(w * h) { 255 }

    private fun fill(px: IntArray, w: Int, x0: Int, y0: Int, x1: Int, y1: Int, color: Int = 0) {
        for (y in y0 until y1) for (x in x0 until x1) px[y * w + x] = color
    }

    /** Draws a closed rectangular outline (a simple bubble wall). */
    private fun outline(px: IntArray, w: Int, x0: Int, y0: Int, x1: Int, y1: Int, t: Int = 2) {
        fill(px, w, x0, y0, x1, y0 + t)
        fill(px, w, x0, y1 - t, x1, y1)
        fill(px, w, x0, y0, x0 + t, y1)
        fill(px, w, x1 - t, y0, x1, y1)
    }

    /** Paints a block of small "letters" (text) inside a region. */
    private fun text(px: IntArray, w: Int, x0: Int, y0: Int, cols: Int, rows: Int) {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val lx = x0 + c * 7
                val ly = y0 + r * 10
                fill(px, w, lx, ly, lx + 4, ly + 6)
            }
        }
    }

    @Test
    fun `detects an enclosed text bubble`() {
        val w = 200
        val h = 200
        val px = blank(w, h)
        outline(px, w, 40, 40, 150, 120)
        text(px, w, 55, 55, cols = 6, rows = 4)

        val result = detector.detect(GrayImage(w, h, px))

        result shouldHaveSize 1
        (result[0].centerX in 0.3f..0.7f) shouldBe true
        (result[0].centerY in 0.25f..0.55f) shouldBe true
    }

    @Test
    fun `ignores loose text that is not inside a bubble`() {
        val w = 200
        val h = 200
        val px = blank(w, h)
        text(px, w, 60, 60, cols = 6, rows = 4)

        detector.detect(GrayImage(w, h, px)) shouldBe emptyList()
    }

    @Test
    fun `ignores a small icon`() {
        val w = 200
        val h = 200
        val px = blank(w, h)
        fill(px, w, 90, 90, 104, 104) // small solid blob

        detector.detect(GrayImage(w, h, px)) shouldBe emptyList()
    }

    @Test
    fun `ignores a large artwork panel`() {
        val w = 200
        val h = 200
        val px = blank(w, h)
        outline(px, w, 10, 10, 190, 190, t = 2)
        // dense "artwork" inside: lots of ink, not white interior
        for (y in 20 until 180 step 3) fill(px, w, 20, y, 180, y + 2)

        detector.detect(GrayImage(w, h, px)) shouldBe emptyList()
    }

    @Test
    fun `detects two separate bubbles ignoring loose labels`() {
        val w = 240
        val h = 200
        val px = blank(w, h)
        outline(px, w, 20, 40, 110, 110)
        text(px, w, 32, 55, cols = 5, rows = 3)
        outline(px, w, 140, 40, 220, 110)
        text(px, w, 152, 55, cols = 5, rows = 3)
        // a loose label out in the open, should be ignored
        text(px, w, 90, 160, cols = 4, rows = 1)

        val result = detector.detect(GrayImage(w, h, px))

        result shouldHaveSize 2
        result.any { it.centerX < 0.5f } shouldBe true
        result.any { it.centerX > 0.5f } shouldBe true
    }

    @Test
    fun `blank page returns no regions`() {
        detector.detect(GrayImage(200, 200, blank(200, 200))) shouldBe emptyList()
    }

    @Test
    fun `ignores an enclosed white region containing only large strokes (artwork face)`() {
        val w = 200
        val h = 200
        val px = blank(w, h)
        outline(px, w, 40, 40, 150, 160)
        // a few big strokes spanning most of the interior height, like facial linework
        fill(px, w, 60, 50, 66, 150)
        fill(px, w, 90, 50, 96, 150)
        fill(px, w, 120, 55, 126, 145)

        detector.detect(GrayImage(w, h, px)) shouldBe emptyList()
    }

    @Test
    fun `ignores an enclosed screentone region`() {
        val w = 200
        val h = 200
        val px = blank(w, h)
        outline(px, w, 40, 40, 150, 120)
        // gray screentone fill (not pure white) with a few marks
        fill(px, w, 44, 44, 146, 116, color = 180)
        text(px, w, 55, 55, cols = 5, rows = 3)

        detector.detect(GrayImage(w, h, px)) shouldBe emptyList()
    }

    @Test
    fun `ignores an enclosed region with a few scattered marks (facial features)`() {
        val w = 200
        val h = 200
        val px = blank(w, h)
        outline(px, w, 40, 40, 150, 160)
        // a handful of small marks scattered, not aligned in a text row (eyes, nostril, etc.)
        fill(px, w, 60, 60, 66, 66)
        fill(px, w, 120, 70, 126, 76)
        fill(px, w, 90, 120, 96, 126)

        detector.detect(GrayImage(w, h, px)) shouldBe emptyList()
    }

    @Test
    fun `detected regions stay within normalized bounds`() {
        val w = 200
        val h = 200
        val px = blank(w, h)
        outline(px, w, 20, 20, 120, 90)
        text(px, w, 30, 32, cols = 5, rows = 3)

        detector.detect(GrayImage(w, h, px)).forEach {
            (it.left >= 0f && it.right <= 1f && it.top >= 0f && it.bottom <= 1f) shouldBe true
        }
    }
}
