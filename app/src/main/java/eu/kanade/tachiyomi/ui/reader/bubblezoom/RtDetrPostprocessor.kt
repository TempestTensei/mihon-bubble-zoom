package eu.kanade.tachiyomi.ui.reader.bubblezoom

import kotlin.math.max
import kotlin.math.min

/**
 * Pure post-processing for the RT-DETR comic text/bubble detector.
 *
 * The ONNX model already applies its own decoding (it is NMS-free and, given
 * `orig_target_sizes`, returns up to 300 xyxy boxes in original-image pixels with per-box
 * scores). This step filters by score, normalizes to `[0,1]`, and drops boxes that are
 * mostly contained in a larger one — so the text-inside-a-bubble detection collapses into
 * the bubble, while free-floating text (not inside any bubble) is kept.
 */
object RtDetrPostprocessor {

    fun toRegions(
        boxes: FloatArray,
        scores: FloatArray,
        count: Int,
        origWidth: Int,
        origHeight: Int,
        scoreThreshold: Float,
        containmentThreshold: Float = 0.7f,
    ): List<Region> {
        val w = origWidth.toFloat()
        val h = origHeight.toFloat()
        val raw = ArrayList<Region>()
        for (i in 0 until count) {
            val score = scores[i]
            if (score < scoreThreshold) continue
            val bx0 = boxes[i * 4]
            val by0 = boxes[i * 4 + 1]
            val bx1 = boxes[i * 4 + 2]
            val by1 = boxes[i * 4 + 3]
            val left = (min(bx0, bx1) / w).coerceIn(0f, 1f)
            val top = (min(by0, by1) / h).coerceIn(0f, 1f)
            val right = (max(bx0, bx1) / w).coerceIn(0f, 1f)
            val bottom = (max(by0, by1) / h).coerceIn(0f, 1f)
            if (right <= left || bottom <= top) continue
            raw.add(Region(left, top, right, bottom, score))
        }
        return dedupeContained(raw, containmentThreshold)
    }

    /** Keeps larger boxes; drops a box whose area is mostly inside an already-kept larger box. */
    private fun dedupeContained(regions: List<Region>, threshold: Float): List<Region> {
        val sorted = regions.sortedByDescending { it.area }
        val kept = ArrayList<Region>()
        for (region in sorted) {
            if (kept.none { containment(region, it) >= threshold }) {
                kept.add(region)
            }
        }
        return kept
    }

    /** Fraction of [inner]'s area covered by its intersection with [outer]. */
    private fun containment(inner: Region, outer: Region): Float {
        val ix0 = max(inner.left, outer.left)
        val iy0 = max(inner.top, outer.top)
        val ix1 = min(inner.right, outer.right)
        val iy1 = min(inner.bottom, outer.bottom)
        val iw = max(0f, ix1 - ix0)
        val ih = max(0f, iy1 - iy0)
        val innerArea = inner.area
        return if (innerArea <= 0f) 0f else (iw * ih) / innerArea
    }
}
