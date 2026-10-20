package eu.kanade.tachiyomi.ui.reader.bubblezoom

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.abs

class YoloBubblePostprocessorTest {

    /** Builds a channels-first YOLOv8 output [1, channels, anchors] as a flat array. */
    private fun channelsFirst(anchors: Int, channels: Int, rows: List<FloatArray>): FloatArray {
        val out = FloatArray(channels * anchors)
        rows.forEachIndexed { a, row ->
            for (c in 0 until channels) out[c * anchors + a] = row[c]
        }
        return out
    }

    @Test
    fun `decode keeps boxes above the confidence threshold and converts xywh to xyxy`() {
        // one anchor score 0.9 (cx=100,cy=120,w=40,h=60), one anchor score 0.1 (dropped)
        val output = channelsFirst(
            anchors = 2,
            channels = 5,
            rows = listOf(
                floatArrayOf(100f, 120f, 40f, 60f, 0.9f),
                floatArrayOf(10f, 10f, 5f, 5f, 0.1f),
            ),
        )

        val boxes = YoloBubblePostprocessor.decode(
            output,
            channels = 5,
            anchors = 2,
            channelsFirst = true,
            confThreshold = 0.5f,
        )

        boxes shouldHaveSize 1
        val b = boxes[0]
        (abs(b.x0 - 80f) < 0.01f) shouldBe true
        (abs(b.y0 - 90f) < 0.01f) shouldBe true
        (abs(b.x1 - 120f) < 0.01f) shouldBe true
        (abs(b.y1 - 150f) < 0.01f) shouldBe true
        b.score shouldBe 0.9f
    }

    @Test
    fun `decode reads channels-last layout equivalently`() {
        val channels = 5
        val anchors = 1
        val last = floatArrayOf(100f, 120f, 40f, 60f, 0.9f) // a*channels + c, single anchor

        val boxes = YoloBubblePostprocessor.decode(last, channels, anchors, channelsFirst = false, confThreshold = 0.5f)

        boxes shouldHaveSize 1
        (abs(boxes[0].x0 - 80f) < 0.01f) shouldBe true
    }

    @Test
    fun `nms drops a lower-scoring box that overlaps a higher one`() {
        val a = YoloBubblePostprocessor.Box(0f, 0f, 100f, 100f, 0.9f)
        val b = YoloBubblePostprocessor.Box(10f, 10f, 100f, 100f, 0.6f) // heavy overlap
        val c = YoloBubblePostprocessor.Box(500f, 500f, 600f, 600f, 0.8f) // disjoint

        val kept = YoloBubblePostprocessor.nms(listOf(a, b, c), iouThreshold = 0.5f)

        kept shouldHaveSize 2
        kept.any { it.score == 0.9f } shouldBe true
        kept.any { it.score == 0.8f } shouldBe true
        kept.any { it.score == 0.6f } shouldBe false
    }

    @Test
    fun `toRegions undoes letterbox padding and normalizes`() {
        // page 1000x500 letterboxed into 640: scale=0.64, newW=640, newH=320, padX=0, padY=160
        val lb = YoloBubblePostprocessor.computeLetterbox(1000, 500, 640)
        (abs(lb.scale - 0.64f) < 1e-4f) shouldBe true
        (abs(lb.padX - 0f) < 1e-4f) shouldBe true
        (abs(lb.padY - 160f) < 1e-4f) shouldBe true

        // a box at model coords (0..640 x, 160..480 y) should map to the full page
        val box = YoloBubblePostprocessor.Box(0f, 160f, 640f, 480f, 0.9f)
        val regions = YoloBubblePostprocessor.toRegions(listOf(box), lb, origWidth = 1000, origHeight = 500)

        regions shouldHaveSize 1
        regions[0].left shouldBe 0f
        regions[0].top shouldBe 0f
        (abs(regions[0].right - 1f) < 1e-3f) shouldBe true
        (abs(regions[0].bottom - 1f) < 1e-3f) shouldBe true
    }

    @Test
    fun `toRegions keeps coordinates within normalized bounds`() {
        val lb = YoloBubblePostprocessor.computeLetterbox(800, 1200, 1024)
        val box = YoloBubblePostprocessor.Box(-50f, -50f, 5000f, 5000f, 0.9f) // out of range
        val r = YoloBubblePostprocessor.toRegions(listOf(box), lb, 800, 1200)[0]
        (r.left >= 0f && r.top >= 0f && r.right <= 1f && r.bottom <= 1f) shouldBe true
        r.right shouldBeGreaterThan r.left
        r.bottom shouldBeGreaterThan r.top
    }

    @Test
    fun `full pipeline decodes, suppresses and maps`() {
        val output = channelsFirst(
            anchors = 2,
            channels = 5,
            rows = listOf(
                floatArrayOf(320f, 320f, 100f, 80f, 0.95f),
                floatArrayOf(322f, 319f, 98f, 82f, 0.80f), // overlaps the first
            ),
        )
        val lb = YoloBubblePostprocessor.computeLetterbox(640, 640, 640)
        val boxes = YoloBubblePostprocessor.decode(output, 5, 2, true, 0.5f)
        val kept = YoloBubblePostprocessor.nms(boxes, 0.5f)
        val regions = YoloBubblePostprocessor.toRegions(kept, lb, 640, 640)

        regions shouldHaveSize 1
        regions[0].centerX shouldBeLessThan 0.6f
        regions[0].centerX shouldBeGreaterThan 0.4f
    }
}
