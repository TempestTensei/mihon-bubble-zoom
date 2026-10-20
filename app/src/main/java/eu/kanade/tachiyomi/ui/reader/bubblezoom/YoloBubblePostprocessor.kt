package eu.kanade.tachiyomi.ui.reader.bubblezoom

import kotlin.math.max
import kotlin.math.min

/**
 * Pure post-processing for a YOLOv8 speech-bubble model: decode the raw tensor into boxes,
 * run non-max-suppression, and map letterboxed model coordinates back to normalized page
 * coordinates. Kept free of Android and the inference runtime so it can be unit-tested.
 *
 * Works with the standard Ultralytics YOLOv8 ONNX output where the box is channels 0..3
 * (cx, cy, w, h in model-input pixels) and the single bubble class score is channel 4;
 * any further channels (e.g. segmentation mask coefficients) are ignored.
 */
object YoloBubblePostprocessor {

    data class Box(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val score: Float)

    /** Letterbox mapping from a [size]x[size] model input back to the original image. */
    data class Letterbox(val scale: Float, val padX: Float, val padY: Float, val size: Int)

    fun computeLetterbox(origWidth: Int, origHeight: Int, size: Int): Letterbox {
        val scale = min(size.toFloat() / origWidth, size.toFloat() / origHeight)
        val padX = (size - origWidth * scale) / 2f
        val padY = (size - origHeight * scale) / 2f
        return Letterbox(scale, padX, padY, size)
    }

    fun decode(
        output: FloatArray,
        channels: Int,
        anchors: Int,
        channelsFirst: Boolean,
        confThreshold: Float,
    ): List<Box> {
        val boxes = ArrayList<Box>()
        for (a in 0 until anchors) {
            val base = if (channelsFirst) a else a * channels
            val stride = if (channelsFirst) anchors else 1
            val score = output[base + 4 * stride]
            if (score < confThreshold) continue
            val cx = output[base + 0 * stride]
            val cy = output[base + 1 * stride]
            val wv = output[base + 2 * stride]
            val hv = output[base + 3 * stride]
            boxes.add(Box(cx - wv / 2f, cy - hv / 2f, cx + wv / 2f, cy + hv / 2f, score))
        }
        return boxes
    }

    fun nms(boxes: List<Box>, iouThreshold: Float): List<Box> {
        val sorted = boxes.sortedByDescending { it.score }
        val removed = BooleanArray(sorted.size)
        val kept = ArrayList<Box>()
        for (i in sorted.indices) {
            if (removed[i]) continue
            kept.add(sorted[i])
            for (j in i + 1 until sorted.size) {
                if (!removed[j] && iou(sorted[i], sorted[j]) > iouThreshold) {
                    removed[j] = true
                }
            }
        }
        return kept
    }

    fun toRegions(boxes: List<Box>, letterbox: Letterbox, origWidth: Int, origHeight: Int): List<Region> {
        val w = origWidth.toFloat()
        val h = origHeight.toFloat()
        return boxes.mapNotNull { b ->
            val x0 = ((b.x0 - letterbox.padX) / letterbox.scale).coerceIn(0f, w)
            val y0 = ((b.y0 - letterbox.padY) / letterbox.scale).coerceIn(0f, h)
            val x1 = ((b.x1 - letterbox.padX) / letterbox.scale).coerceIn(0f, w)
            val y1 = ((b.y1 - letterbox.padY) / letterbox.scale).coerceIn(0f, h)
            if (x1 <= x0 || y1 <= y0) {
                null
            } else {
                Region(x0 / w, y0 / h, x1 / w, y1 / h, b.score)
            }
        }
    }

    private fun iou(a: Box, b: Box): Float {
        val interX0 = max(a.x0, b.x0)
        val interY0 = max(a.y0, b.y0)
        val interX1 = min(a.x1, b.x1)
        val interY1 = min(a.y1, b.y1)
        val interW = max(0f, interX1 - interX0)
        val interH = max(0f, interY1 - interY0)
        val inter = interW * interH
        val areaA = max(0f, a.x1 - a.x0) * max(0f, a.y1 - a.y0)
        val areaB = max(0f, b.x1 - b.x0) * max(0f, b.y1 - b.y0)
        val union = areaA + areaB - inter
        return if (union <= 0f) 0f else inter / union
    }
}
