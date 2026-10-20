package eu.kanade.tachiyomi.ui.reader.bubblezoom

/**
 * A rectangular region of a page in normalized source-image coordinates, where
 * (0f, 0f) is the top-left corner and (1f, 1f) is the bottom-right corner.
 *
 * Used by the bubble-zoom feature to describe a detected panel on a page.
 */
data class Region(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float = 1f,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}
