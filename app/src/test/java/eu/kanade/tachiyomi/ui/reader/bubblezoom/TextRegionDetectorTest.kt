package eu.kanade.tachiyomi.ui.reader.bubblezoom

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TextRegionDetectorTest {

    private val detector = TextRegionDetector()

    private fun blankPage(width: Int, height: Int) = IntArray(width * height) { 255 }

    /** Paints a grid of small black "letters" (a block of text) onto [pixels]. */
    private fun paintText(
        pixels: IntArray,
        width: Int,
        x0: Int,
        y0: Int,
        cols: Int,
        rows: Int,
        letterW: Int = 4,
        letterH: Int = 6,
        gapX: Int = 3,
        gapY: Int = 4,
    ) {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val lx = x0 + c * (letterW + gapX)
                val ly = y0 + r * (letterH + gapY)
                for (yy in ly until ly + letterH) {
                    for (xx in lx until lx + letterW) {
                        pixels[yy * width + xx] = 0
                    }
                }
            }
        }
    }

    private fun paintSolid(pixels: IntArray, width: Int, x0: Int, y0: Int, x1: Int, y1: Int) {
        for (y in y0 until y1) for (x in x0 until x1) pixels[y * width + x] = 0
    }

    @Test
    fun `detects two separated text blocks as two regions`() {
        val w = 200
        val h = 200
        val px = blankPage(w, h)
        paintText(px, w, x0 = 20, y0 = 20, cols = 6, rows = 2)
        paintText(px, w, x0 = 120, y0 = 20, cols = 6, rows = 2)

        val result = detector.detect(GrayImage(w, h, px))

        result shouldHaveSize 2
        result.any { it.centerX < 0.5f } shouldBe true
        result.any { it.centerX > 0.5f } shouldBe true
    }

    @Test
    fun `merges letters and lines of one block into a single region`() {
        val w = 200
        val h = 200
        val px = blankPage(w, h)
        paintText(px, w, x0 = 40, y0 = 40, cols = 6, rows = 3)

        val result = detector.detect(GrayImage(w, h, px))

        result shouldHaveSize 1
        // Merged into a block, not one region per letter.
        (result[0].width > 0.1f) shouldBe true
        (result[0].height > 0.05f) shouldBe true
    }

    @Test
    fun `blank page returns no regions`() {
        detector.detect(GrayImage(200, 200, blankPage(200, 200))) shouldBe emptyList()
    }

    @Test
    fun `ignores a lone speck`() {
        val w = 200
        val h = 200
        val px = blankPage(w, h)
        paintSolid(px, w, 100, 100, 103, 103)

        detector.detect(GrayImage(w, h, px)) shouldBe emptyList()
    }

    @Test
    fun `ignores a large solid black area`() {
        val w = 200
        val h = 200
        val px = blankPage(w, h)
        paintSolid(px, w, 40, 40, 150, 150)

        detector.detect(GrayImage(w, h, px)) shouldBe emptyList()
    }

    @Test
    fun `returns only the text block when a solid black area is also present`() {
        val w = 240
        val h = 200
        val px = blankPage(w, h)
        paintSolid(px, w, 10, 10, 90, 170) // artwork-like solid block on the left
        paintText(px, w, x0 = 140, y0 = 60, cols = 6, rows = 3) // text on the right

        val result = detector.detect(GrayImage(w, h, px))

        result shouldHaveSize 1
        (result[0].centerX > 0.5f) shouldBe true
    }

    @Test
    fun `detected regions stay within normalized bounds`() {
        val w = 200
        val h = 200
        val px = blankPage(w, h)
        paintText(px, w, x0 = 20, y0 = 20, cols = 6, rows = 2)
        paintText(px, w, x0 = 120, y0 = 140, cols = 6, rows = 2)

        detector.detect(GrayImage(w, h, px)).forEach {
            (it.left >= 0f && it.right <= 1f && it.top >= 0f && it.bottom <= 1f) shouldBe true
        }
    }
}
