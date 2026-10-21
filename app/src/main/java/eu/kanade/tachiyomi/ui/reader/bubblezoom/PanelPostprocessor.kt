package eu.kanade.tachiyomi.ui.reader.bubblezoom

import kotlin.math.min

/**
 * Pure post-processing for the end-to-end YOLO manga panel detector.
 *
 * The ONNX model is NMS-free and outputs up to 300 rows of `[x0, y0, x1, y1, score, class]`
 * in letterboxed model pixels (class 0 = panel, 1 = text). This keeps the panel class above a
 * score threshold, undoes the letterbox and normalizes to `[0,1]`.
 */
object PanelPostprocessor {

    data class Letterbox(val scale: Float, val padX: Float, val padY: Float, val size: Int)

    fun computeLetterbox(origWidth: Int, origHeight: Int, size: Int): Letterbox {
        val scale = min(size.toFloat() / origWidth, size.toFloat() / origHeight)
        val padX = (size - origWidth * scale) / 2f
        val padY = (size - origHeight * scale) / 2f
        return Letterbox(scale, padX, padY, size)
    }

    fun toPanels(
        output: FloatArray,
        count: Int,
        letterbox: Letterbox,
        origWidth: Int,
        origHeight: Int,
        scoreThreshold: Float,
        panelClass: Int = 0,
    ): List<Region> {
        val w = origWidth.toFloat()
        val h = origHeight.toFloat()
        val panels = ArrayList<Region>()
        for (i in 0 until count) {
            val base = i * STRIDE
            val score = output[base + 4]
            if (score < scoreThreshold) continue
            if (output[base + 5].toInt() != panelClass) continue
            val x0 = ((output[base] - letterbox.padX) / letterbox.scale).coerceIn(0f, w)
            val y0 = ((output[base + 1] - letterbox.padY) / letterbox.scale).coerceIn(0f, h)
            val x1 = ((output[base + 2] - letterbox.padX) / letterbox.scale).coerceIn(0f, w)
            val y1 = ((output[base + 3] - letterbox.padY) / letterbox.scale).coerceIn(0f, h)
            if (x1 <= x0 || y1 <= y0) continue
            panels.add(Region(x0 / w, y0 / h, x1 / w, y1 / h, score))
        }
        return panels
    }

    private const val STRIDE = 6
}
