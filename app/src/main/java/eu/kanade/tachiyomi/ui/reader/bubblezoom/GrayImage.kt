package eu.kanade.tachiyomi.ui.reader.bubblezoom

/**
 * A downscaled grayscale page used for panel detection.
 *
 * [pixels] holds row-major luminance values in `0..255` (0 = black ink,
 * 255 = white paper) and must have exactly [width] * [height] entries.
 */
class GrayImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(pixels.size == width * height) {
            "pixels size ${pixels.size} must equal width*height (${width * height})"
        }
    }

    fun luma(x: Int, y: Int): Int = pixels[y * width + x]
}
