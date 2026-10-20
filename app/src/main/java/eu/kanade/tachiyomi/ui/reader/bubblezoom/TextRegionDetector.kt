package eu.kanade.tachiyomi.ui.reader.bubblezoom

/**
 * Detects speech-bubble / caption text blocks for bubble zoom.
 *
 * Approach (classical, no dependencies): binarize the dark text, smear it together
 * with a Run-Length Smoothing pass (horizontal then vertical) so the letters, words
 * and lines of one bubble merge into a single blob, label the blobs with connected
 * components, and keep those that look like text sitting on a light background
 * (bubble/caption interior) — discarding specks, long thin strokes, solid-black
 * artwork and near-whole-page blobs.
 *
 * Output regions are normalized `[0,1]` and unordered; [RegionOrdering] applies
 * reading order separately. An empty result means "no usable text — show the full page".
 */
class TextRegionDetector(
    val config: Config = Config(),
) : RegionDetector {

    data class Config(
        /** Luminance below this is treated as text ink. */
        val inkThreshold: Int = 160,
        /** Luminance above this counts as light background. */
        val whiteThreshold: Int = 200,
        /** Horizontal whitespace up to this fraction of the page width is smeared over. */
        val horizontalGapFraction: Float = 0.03f,
        /** Vertical whitespace up to this fraction of the page height is smeared over. */
        val verticalGapFraction: Float = 0.02f,
        val minRegionHeightFraction: Float = 0.02f,
        val maxRegionHeightFraction: Float = 0.6f,
        val minRegionWidthFraction: Float = 0.03f,
        val maxRegionWidthFraction: Float = 0.9f,
        val minAreaFraction: Float = 0.002f,
        val maxAreaFraction: Float = 0.6f,
        /** Fraction of the blob's bounding box that is ink; filters specks and solid fills. */
        val minInkDensity: Float = 0.03f,
        val maxInkDensity: Float = 0.7f,
        /** Minimum fraction of the bounding box that is light (the bubble/caption paper). */
        val minWhiteBackground: Float = 0.35f,
        /** Reject long thin strokes (panel borders, speed lines). */
        val maxAspect: Float = 15f,
    )

    override fun detect(image: GrayImage): List<Region> {
        val w = image.width
        val h = image.height
        if (w <= 0 || h <= 0) return emptyList()
        val n = w * h
        val pixels = image.pixels

        val ink = BooleanArray(n)
        for (i in 0 until n) ink[i] = pixels[i] < config.inkThreshold

        val hGap = maxOf(1, (config.horizontalGapFraction * w).toInt())
        val vGap = maxOf(1, (config.verticalGapFraction * h).toInt())

        // Run-Length Smoothing: merge letters -> words/lines (horizontal), then lines -> block (vertical).
        val smear = smearVertical(smearHorizontal(ink, w, h, hGap), w, h, vGap)

        val regions = labelRegions(smear, ink, pixels, w, h)
        val filtered = regions.filter { it.area <= config.maxAreaFraction }
        return if (filtered.isEmpty()) emptyList() else filtered
    }

    private fun smearHorizontal(ink: BooleanArray, w: Int, h: Int, gap: Int): BooleanArray {
        val out = ink.copyOf()
        for (y in 0 until h) {
            val base = y * w
            var x = 0
            while (x < w) {
                if (ink[base + x]) {
                    x++
                    continue
                }
                var end = x
                while (end < w && !ink[base + end]) end++
                if (x > 0 && end < w && end - x <= gap) {
                    for (k in x until end) out[base + k] = true
                }
                x = end
            }
        }
        return out
    }

    private fun smearVertical(src: BooleanArray, w: Int, h: Int, gap: Int): BooleanArray {
        val out = src.copyOf()
        for (x in 0 until w) {
            var y = 0
            while (y < h) {
                if (src[y * w + x]) {
                    y++
                    continue
                }
                var end = y
                while (end < h && !src[end * w + x]) end++
                if (y > 0 && end < h && end - y <= gap) {
                    for (k in y until end) out[k * w + x] = true
                }
                y = end
            }
        }
        return out
    }

    private fun labelRegions(
        smear: BooleanArray,
        ink: BooleanArray,
        pixels: IntArray,
        w: Int,
        h: Int,
    ): List<Region> {
        val n = w * h
        val visited = BooleanArray(n)
        val stack = IntArray(n)
        val regions = ArrayList<Region>()

        for (start in 0 until n) {
            if (!smear[start] || visited[start]) continue
            var sp = 0
            stack[sp++] = start
            visited[start] = true
            var minX = w
            var minY = h
            var maxX = -1
            var maxY = -1
            while (sp > 0) {
                val p = stack[--sp]
                val px = p % w
                val py = p / w
                if (px < minX) minX = px
                if (px > maxX) maxX = px
                if (py < minY) minY = py
                if (py > maxY) maxY = py
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = px + dx
                        val ny = py + dy
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                        val np = ny * w + nx
                        if (smear[np] && !visited[np]) {
                            visited[np] = true
                            stack[sp++] = np
                        }
                    }
                }
            }

            val bw = maxX - minX + 1
            val bh = maxY - minY + 1
            if (!passesShape(bw, bh, w, h)) continue

            var inkCount = 0
            var whiteCount = 0
            var total = 0
            for (yy in minY..maxY) {
                val rb = yy * w
                for (xx in minX..maxX) {
                    total++
                    if (ink[rb + xx]) inkCount++
                    if (pixels[rb + xx] > config.whiteThreshold) whiteCount++
                }
            }
            val density = inkCount.toFloat() / total
            val whiteRatio = whiteCount.toFloat() / total
            if (density < config.minInkDensity || density > config.maxInkDensity) continue
            if (whiteRatio < config.minWhiteBackground) continue

            regions.add(
                Region(
                    left = minX / w.toFloat(),
                    top = minY / h.toFloat(),
                    right = (maxX + 1) / w.toFloat(),
                    bottom = (maxY + 1) / h.toFloat(),
                ),
            )
        }
        return regions
    }

    private fun passesShape(bw: Int, bh: Int, w: Int, h: Int): Boolean {
        val widthFraction = bw / w.toFloat()
        val heightFraction = bh / h.toFloat()
        val areaFraction = (bw.toLong() * bh) / (w.toLong() * h).toFloat()
        if (heightFraction < config.minRegionHeightFraction || heightFraction > config.maxRegionHeightFraction) {
            return false
        }
        if (widthFraction < config.minRegionWidthFraction || widthFraction > config.maxRegionWidthFraction) {
            return false
        }
        if (areaFraction < config.minAreaFraction || areaFraction > config.maxAreaFraction) return false
        val aspect = maxOf(bw.toFloat() / bh, bh.toFloat() / bw)
        return aspect <= config.maxAspect
    }
}
