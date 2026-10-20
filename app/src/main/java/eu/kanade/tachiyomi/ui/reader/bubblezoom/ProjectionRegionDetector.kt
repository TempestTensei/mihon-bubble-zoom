package eu.kanade.tachiyomi.ui.reader.bubblezoom

/**
 * Classical-CV panel detector using recursive XY-cut over whitespace gutters.
 *
 * The page is binarized into ink/paper, then recursively split along rows and
 * columns wherever a whitespace band at least [Config.minGutterFraction] of the
 * page wide separates content. Leaf rectangles are the candidate panels.
 *
 * To avoid bad zooms the detector returns an empty list (meaning "show the full
 * page") when the result is not usable: no leaves, a single leaf, or a leaf that
 * covers more than [Config.maxSingleRegionCoverage] of the page.
 *
 * Output regions are in normalized `[0,1]` coordinates and are NOT ordered;
 * reading-order sorting is applied separately by [RegionOrdering].
 */
class ProjectionRegionDetector(
    val config: Config = Config(),
) : RegionDetector {

    data class Config(
        /** Luminance below this is treated as ink. */
        val inkThreshold: Int = 200,
        /** A line counts as whitespace when its ink fraction is at most `1 - this`. */
        val whitespaceRowFraction: Float = 0.99f,
        /** Leaves smaller than this fraction of the page area are discarded. */
        val minRegionFraction: Float = 0.015f,
        /** If a single leaf covers more than this fraction, detection is abandoned. */
        val maxSingleRegionCoverage: Float = 0.85f,
        /** A whitespace band must be at least this fraction of the page to split. */
        val minGutterFraction: Float = 0.015f,
    )

    override fun detect(image: GrayImage): List<Region> {
        val w = image.width
        val h = image.height
        if (w <= 0 || h <= 0) return emptyList()

        val ink = BooleanArray(w * h)
        val pixels = image.pixels
        for (i in ink.indices) {
            ink[i] = pixels[i] < config.inkThreshold
        }

        val minGutterX = maxOf(1, (config.minGutterFraction * w).toInt())
        val minGutterY = maxOf(1, (config.minGutterFraction * h).toInt())

        val leaves = ArrayList<IntArray>()
        cut(ink, w, 0, 0, w, h, minGutterX, minGutterY, leaves)

        val pageArea = (w.toLong() * h).toFloat()
        val filtered = leaves.filter { box ->
            (box[2] - box[0]).toLong() * (box[3] - box[1]) / pageArea >= config.minRegionFraction
        }
        if (filtered.size <= 1) return emptyList()

        val maxCoverage = filtered.maxOf { box ->
            (box[2] - box[0]).toLong() * (box[3] - box[1]) / pageArea
        }
        if (maxCoverage > config.maxSingleRegionCoverage) return emptyList()

        return filtered.map { box ->
            Region(
                left = box[0] / w.toFloat(),
                top = box[1] / h.toFloat(),
                right = box[2] / w.toFloat(),
                bottom = box[3] / h.toFloat(),
            )
        }
    }

    private fun cut(
        ink: BooleanArray,
        w: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        minGutterX: Int,
        minGutterY: Int,
        out: ArrayList<IntArray>,
    ) {
        val tb = tightBounds(ink, w, x0, y0, x1, y1) ?: return
        val tx0 = tb[0]
        val ty0 = tb[1]
        val tx1 = tb[2]
        val ty1 = tb[3]

        val rowBands = bands(ink, w, tx0, ty0, tx1, ty1, horizontal = true, minGutter = minGutterY)
        if (rowBands.size > 1) {
            for (b in rowBands) cut(ink, w, tx0, b[0], tx1, b[1], minGutterX, minGutterY, out)
            return
        }

        val colBands = bands(ink, w, tx0, ty0, tx1, ty1, horizontal = false, minGutter = minGutterX)
        if (colBands.size > 1) {
            for (b in colBands) cut(ink, w, b[0], ty0, b[1], ty1, minGutterX, minGutterY, out)
            return
        }

        out.add(intArrayOf(tx0, ty0, tx1, ty1))
    }

    /** Shrinks the rectangle to the content that is not whitespace, or null when it is all paper. */
    private fun tightBounds(ink: BooleanArray, w: Int, x0: Int, y0: Int, x1: Int, y1: Int): IntArray? {
        val allowedX = ((1f - config.whitespaceRowFraction) * (x1 - x0)).toInt()
        val allowedY = ((1f - config.whitespaceRowFraction) * (y1 - y0)).toInt()

        var top = -1
        var bottom = -1
        for (y in y0 until y1) {
            if (rowInk(ink, w, y, x0, x1) > allowedX) {
                if (top == -1) top = y
                bottom = y
            }
        }
        if (top == -1) return null

        var left = -1
        var right = -1
        for (x in x0 until x1) {
            if (colInk(ink, w, x, y0, y1) > allowedY) {
                if (left == -1) left = x
                right = x
            }
        }
        return intArrayOf(left, top, right + 1, bottom + 1)
    }

    /**
     * Splits the rectangle into content bands separated by whitespace runs of at
     * least [minGutter]. Returns `[start, end)` content extents along the chosen
     * axis (rows when [horizontal], columns otherwise).
     */
    private fun bands(
        ink: BooleanArray,
        w: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        horizontal: Boolean,
        minGutter: Int,
    ): List<IntArray> {
        val lo = if (horizontal) y0 else x0
        val hi = if (horizontal) y1 else x1
        val n = hi - lo
        if (n <= 0) return emptyList()

        val allowed = if (horizontal) {
            ((1f - config.whitespaceRowFraction) * (x1 - x0)).toInt()
        } else {
            ((1f - config.whitespaceRowFraction) * (y1 - y0)).toInt()
        }

        val white = BooleanArray(n)
        for (i in 0 until n) {
            val line = lo + i
            val inkCount = if (horizontal) rowInk(ink, w, line, x0, x1) else colInk(ink, w, line, y0, y1)
            white[i] = inkCount <= allowed
        }

        val result = ArrayList<IntArray>()
        var i = 0
        while (i < n && white[i]) i++
        var segStart = i
        while (i < n) {
            if (white[i]) {
                var j = i
                while (j < n && white[j]) j++
                if (j - i >= minGutter) {
                    if (i > segStart) result.add(intArrayOf(lo + segStart, lo + i))
                    segStart = j
                }
                i = j
            } else {
                i++
            }
        }
        if (segStart < n) {
            var end = n
            while (end > segStart && white[end - 1]) end--
            if (end > segStart) result.add(intArrayOf(lo + segStart, lo + end))
        }
        return result
    }

    private fun rowInk(ink: BooleanArray, w: Int, y: Int, x0: Int, x1: Int): Int {
        val base = y * w
        var count = 0
        for (x in x0 until x1) {
            if (ink[base + x]) count++
        }
        return count
    }

    private fun colInk(ink: BooleanArray, w: Int, x: Int, y0: Int, y1: Int): Int {
        var count = 0
        for (y in y0 until y1) {
            if (ink[y * w + x]) count++
        }
        return count
    }
}
