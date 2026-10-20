package eu.kanade.tachiyomi.ui.reader.bubblezoom

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RegionOrderingTest {

    // A 2x2 grid of panels in normalized coordinates.
    private val topLeft = Region(0.05f, 0.05f, 0.45f, 0.45f)
    private val topRight = Region(0.55f, 0.05f, 0.95f, 0.45f)
    private val bottomLeft = Region(0.05f, 0.55f, 0.45f, 0.95f)
    private val bottomRight = Region(0.55f, 0.55f, 0.95f, 0.95f)

    @Test
    fun `orders a 2x2 grid row-major left-to-right when not rtl`() {
        val shuffled = listOf(bottomRight, topLeft, bottomLeft, topRight)

        RegionOrdering.order(shuffled, rtl = false) shouldBe
            listOf(topLeft, topRight, bottomLeft, bottomRight)
    }

    @Test
    fun `mirrors columns within each row when rtl`() {
        val shuffled = listOf(bottomRight, topLeft, bottomLeft, topRight)

        RegionOrdering.order(shuffled, rtl = true) shouldBe
            listOf(topRight, topLeft, bottomRight, bottomLeft)
    }

    @Test
    fun `orders rows top to bottom regardless of input order`() {
        val top = Region(0.1f, 0.00f, 0.9f, 0.30f)
        val middle = Region(0.1f, 0.35f, 0.9f, 0.65f)
        val bottom = Region(0.1f, 0.70f, 0.9f, 1.00f)

        RegionOrdering.order(listOf(bottom, top, middle), rtl = false) shouldBe
            listOf(top, middle, bottom)
    }

    @Test
    fun `groups regions into the same row despite a small vertical offset`() {
        val left = Region(0.05f, 0.10f, 0.45f, 0.40f)
        val right = Region(0.55f, 0.12f, 0.95f, 0.42f)

        // Both centers fall within one row, so they order by x, not by top.
        RegionOrdering.order(listOf(right, left), rtl = false) shouldBe listOf(left, right)
    }

    @Test
    fun `returns a single region unchanged for both directions`() {
        val only = listOf(topLeft)

        RegionOrdering.order(only, rtl = false) shouldBe only
        RegionOrdering.order(only, rtl = true) shouldBe only
    }
}
