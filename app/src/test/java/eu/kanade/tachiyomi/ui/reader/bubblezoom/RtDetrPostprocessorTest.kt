package eu.kanade.tachiyomi.ui.reader.bubblezoom

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.abs

class RtDetrPostprocessorTest {

    @Test
    fun `filters by score and normalizes xyxy boxes`() {
        // box0 score 0.9 at (100,120)-(200,180) on a 1000x500 page; box1 score 0.1 dropped
        val boxes = floatArrayOf(100f, 120f, 200f, 180f, 10f, 10f, 20f, 20f)
        val scores = floatArrayOf(0.9f, 0.1f)

        val regions = RtDetrPostprocessor.toRegions(
            boxes,
            scores,
            count = 2,
            origWidth = 1000,
            origHeight = 500,
            scoreThreshold = 0.5f,
        )

        regions shouldHaveSize 1
        (abs(regions[0].left - 0.1f) < 1e-4f) shouldBe true
        (abs(regions[0].top - 0.24f) < 1e-4f) shouldBe true
        (abs(regions[0].right - 0.2f) < 1e-4f) shouldBe true
        (abs(regions[0].bottom - 0.36f) < 1e-4f) shouldBe true
    }

    @Test
    fun `drops a box mostly contained in a larger one (text inside bubble)`() {
        // large bubble box + a small text box inside it, both above threshold
        val boxes = floatArrayOf(
            100f,
            100f,
            400f,
            400f, // large
            150f,
            150f,
            350f,
            350f, // small, inside the large
        )
        val scores = floatArrayOf(0.8f, 0.9f)

        val regions = RtDetrPostprocessor.toRegions(boxes, scores, 2, 1000, 1000, 0.5f)

        regions shouldHaveSize 1
        // the kept one is the large box
        (abs(regions[0].left - 0.1f) < 1e-4f) shouldBe true
        (abs(regions[0].right - 0.4f) < 1e-4f) shouldBe true
    }

    @Test
    fun `keeps two separate boxes`() {
        val boxes = floatArrayOf(
            10f,
            10f,
            100f,
            100f,
            500f,
            500f,
            600f,
            600f,
        )
        val scores = floatArrayOf(0.8f, 0.7f)

        RtDetrPostprocessor.toRegions(boxes, scores, 2, 1000, 1000, 0.5f) shouldHaveSize 2
    }

    @Test
    fun `clamps out-of-range boxes and skips degenerate ones`() {
        val boxes = floatArrayOf(
            -50f,
            -50f,
            2000f,
            2000f, // out of range -> clamps to full page
            300f,
            300f,
            300f,
            300f, // zero-area -> skipped
        )
        val scores = floatArrayOf(0.9f, 0.9f)

        val regions = RtDetrPostprocessor.toRegions(boxes, scores, 2, 800, 600, 0.5f)

        regions shouldHaveSize 1
        regions[0].left shouldBe 0f
        regions[0].top shouldBe 0f
        (abs(regions[0].right - 1f) < 1e-4f) shouldBe true
        (abs(regions[0].bottom - 1f) < 1e-4f) shouldBe true
    }
}
