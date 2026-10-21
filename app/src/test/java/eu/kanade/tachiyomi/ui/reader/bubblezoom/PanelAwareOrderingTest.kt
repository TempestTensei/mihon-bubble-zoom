package eu.kanade.tachiyomi.ui.reader.bubblezoom

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PanelAwareOrderingTest {

    @Test
    fun `falls back to plain reading order when there are no panels`() {
        val a = Region(0.1f, 0.1f, 0.3f, 0.2f)
        val b = Region(0.6f, 0.1f, 0.9f, 0.2f)
        // rtl: same row, right-first
        PanelAwareOrdering.order(listOf(a, b), panels = emptyList(), rtl = true) shouldBe listOf(b, a)
    }

    @Test
    fun `orders bubbles panel by panel (RTL) then top-to-bottom within a panel`() {
        val leftPanel = Region(0.05f, 0.1f, 0.45f, 0.9f)
        val rightPanel = Region(0.55f, 0.1f, 0.95f, 0.9f)

        val lTop = Region(0.1f, 0.2f, 0.4f, 0.3f)
        val lBot = Region(0.1f, 0.6f, 0.4f, 0.7f)
        val rTop = Region(0.6f, 0.2f, 0.9f, 0.3f)
        val rBot = Region(0.6f, 0.6f, 0.9f, 0.7f)

        val ordered = PanelAwareOrdering.order(
            regions = listOf(lBot, rBot, lTop, rTop),
            panels = listOf(leftPanel, rightPanel),
            rtl = true,
        )

        // right panel first (RTL), then left; top-to-bottom within each
        ordered shouldBe listOf(rTop, rBot, lTop, lBot)
    }

    @Test
    fun `appends regions not inside any panel after the panelled ones`() {
        val panel = Region(0.05f, 0.05f, 0.45f, 0.45f)
        val inside = Region(0.1f, 0.1f, 0.4f, 0.2f)
        val outside = Region(0.6f, 0.6f, 0.9f, 0.7f)

        val ordered = PanelAwareOrdering.order(listOf(outside, inside), listOf(panel), rtl = true)

        ordered shouldBe listOf(inside, outside)
    }

    @Test
    fun `assigns a region to the panel it overlaps most`() {
        val leftPanel = Region(0.0f, 0.0f, 0.5f, 1.0f)
        val rightPanel = Region(0.5f, 0.0f, 1.0f, 1.0f)
        // region mostly in the right panel
        val region = Region(0.52f, 0.4f, 0.8f, 0.5f)

        val ordered = PanelAwareOrdering.order(listOf(region), listOf(leftPanel, rightPanel), rtl = true)

        ordered shouldBe listOf(region)
    }
}
