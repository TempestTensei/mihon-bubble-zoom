package eu.kanade.tachiyomi.ui.reader.bubblezoom

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ProjectionRegionDetectorTest {

    private val detector = ProjectionRegionDetector()

    /** Builds a white page and paints black rectangles given as [x0, y0, x1, y1] (pixel, half-open). */
    private fun page(width: Int, height: Int, vararg rects: IntArray): GrayImage {
        val pixels = IntArray(width * height) { 255 }
        for (r in rects) {
            for (y in r[1] until r[3]) {
                for (x in r[0] until r[2]) {
                    pixels[y * width + x] = 0
                }
            }
        }
        return GrayImage(width, height, pixels)
    }

    @Test
    fun `detects two panels separated by a vertical gutter`() {
        val img = page(100, 100, intArrayOf(10, 10, 45, 90), intArrayOf(55, 10, 90, 90))

        val result = detector.detect(img)

        result shouldHaveSize 2
        result.any { it.centerX < 0.5f } shouldBe true
        result.any { it.centerX > 0.5f } shouldBe true
    }

    @Test
    fun `detects a 2x2 grid of panels`() {
        val img = page(
            100,
            100,
            intArrayOf(10, 10, 45, 45),
            intArrayOf(55, 10, 90, 45),
            intArrayOf(10, 55, 45, 90),
            intArrayOf(55, 55, 90, 90),
        )

        detector.detect(img) shouldHaveSize 4
    }

    @Test
    fun `blank white page returns no regions`() {
        detector.detect(page(100, 100)) shouldBe emptyList()
    }

    @Test
    fun `single panel covering most of the page returns no regions`() {
        detector.detect(page(100, 100, intArrayOf(5, 5, 95, 95))) shouldBe emptyList()
    }

    @Test
    fun `ignores speckle noise below the minimum region size`() {
        val img = page(
            100,
            100,
            intArrayOf(10, 10, 45, 90),
            intArrayOf(55, 10, 90, 90),
            // scattered single-pixel specks
            intArrayOf(2, 2, 3, 3),
            intArrayOf(50, 50, 51, 51),
            intArrayOf(97, 5, 98, 6),
        )

        val result = detector.detect(img)

        result shouldHaveSize 2
        result.all { it.area >= detector.config.minRegionFraction } shouldBe true
    }

    @Test
    fun `thin full-width line on a tall blank page returns no regions without throwing`() {
        // Rows qualify (full-width ink) but no column does (allowedY >= 2 on a 200px page),
        // which previously produced a negative left bound and an out-of-bounds access.
        val img = page(100, 200, intArrayOf(0, 0, 100, 1))

        detector.detect(img) shouldBe emptyList()
    }

    @Test
    fun `detected regions stay within normalized bounds`() {
        val img = page(
            120,
            80,
            intArrayOf(10, 10, 50, 35),
            intArrayOf(70, 10, 110, 35),
            intArrayOf(10, 45, 50, 70),
            intArrayOf(70, 45, 110, 70),
        )

        detector.detect(img).forEach {
            (it.left >= 0f && it.right <= 1f && it.top >= 0f && it.bottom <= 1f) shouldBe true
        }
    }
}
