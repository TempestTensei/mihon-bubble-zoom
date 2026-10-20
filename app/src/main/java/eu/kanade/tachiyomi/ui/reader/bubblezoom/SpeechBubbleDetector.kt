package eu.kanade.tachiyomi.ui.reader.bubblezoom

/**
 * Detects comic/manga **speech bubbles** for bubble zoom.
 *
 * The distinguishing property of a speech bubble (versus loose text, labels, icons,
 * UI boxes or whole panels) is that it is an *enclosed white region* — walled off
 * from the page background by the bubble outline — that *contains text*. The detector:
 *
 * 1. Binarizes the dark ink and dilates it slightly to close small gaps in outlines.
 * 2. Flood-fills the page background inward from the borders through non-ink pixels;
 *    anything it cannot reach is "enclosed".
 * 3. Labels the enclosed white blobs and keeps those that are bubble-sized, mostly
 *    white inside, and contain a moderate amount of ink (text) — rejecting open-page
 *    text, icons, dense artwork and panels.
 *
 * Output regions are normalized `[0,1]` and unordered; [RegionOrdering] applies reading
 * order (including right-to-left) separately. Empty means "no bubble — show the full page".
 */
class SpeechBubbleDetector(
    val config: Config = Config(),
) : RegionDetector {

    data class Config(
        val inkThreshold: Int = 160,
        /** Interior pixels brighter than this count as the bubble's clean white paper. */
        val pureWhiteThreshold: Int = 235,
        /** Dilate ink by this radius to close small breaks in bubble outlines. */
        val closeRadius: Int = 1,
        val minAreaFraction: Float = 0.004f,
        val maxAreaFraction: Float = 0.5f,
        val minWidthFraction: Float = 0.04f,
        val maxWidthFraction: Float = 0.9f,
        val minHeightFraction: Float = 0.02f,
        val maxHeightFraction: Float = 0.7f,
        /** The bounding box must be at least this fraction clean white (rejects screentone/art). */
        val minPureWhiteRatio: Float = 0.5f,
        /** The bounding box must contain this fraction of ink (the text). */
        val minTextDensity: Float = 0.015f,
        val maxTextDensity: Float = 0.5f,
        /** The enclosed blob must fill this fraction of its bounding box (not a sliver). */
        val minSolidity: Float = 0.45f,
        val maxAspect: Float = 6f,
        /** Minimum number of ink blobs inside — text has many, artwork has few. */
        val minInkComponents: Int = 3,
        /** Fraction of ink blobs that must be text-sized (small) rather than large strokes. */
        val minSmallComponentRatio: Float = 0.6f,
        /** A text-sized blob is at most this fraction of the page tall. */
        val maxLetterHeightFraction: Float = 0.06f,
        /** Reject if any single ink blob is taller than this fraction of the region (a big stroke). */
        val maxDominantStrokeFraction: Float = 0.6f,
        /** Grow each detected bubble by this fraction so the outline is included. */
        val paddingFraction: Float = 0.03f,
    )

    override fun detect(image: GrayImage): List<Region> {
        val w = image.width
        val h = image.height
        if (w <= 2 || h <= 2) return emptyList()
        val n = w * h
        val pixels = image.pixels

        val ink = BooleanArray(n)
        for (i in 0 until n) ink[i] = pixels[i] < config.inkThreshold

        val barrier = dilate(ink, w, h, config.closeRadius)
        val outside = floodFromBorder(barrier, w, h)
        val enclosed = BooleanArray(n)
        for (i in 0 until n) enclosed[i] = !barrier[i] && !outside[i]

        val regions = labelBubbles(enclosed, ink, pixels, w, h)
        val filtered = regions.filter { it.area in config.minAreaFraction..config.maxAreaFraction }
        return filtered.ifEmpty { emptyList() }
    }

    private fun dilate(ink: BooleanArray, w: Int, h: Int, radius: Int): BooleanArray {
        if (radius <= 0) return ink.copyOf()
        val out = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (!ink[y * w + x]) continue
                val x0 = maxOf(0, x - radius)
                val x1 = minOf(w - 1, x + radius)
                val y0 = maxOf(0, y - radius)
                val y1 = minOf(h - 1, y + radius)
                for (yy in y0..y1) for (xx in x0..x1) out[yy * w + xx] = true
            }
        }
        return out
    }

    private fun floodFromBorder(barrier: BooleanArray, w: Int, h: Int): BooleanArray {
        val n = w * h
        val outside = BooleanArray(n)
        val stack = IntArray(n)
        var sp = 0
        fun push(p: Int) {
            if (!barrier[p] && !outside[p]) {
                outside[p] = true
                stack[sp++] = p
            }
        }
        for (x in 0 until w) {
            push(x)
            push((h - 1) * w + x)
        }
        for (y in 0 until h) {
            push(y * w)
            push(y * w + (w - 1))
        }
        while (sp > 0) {
            val p = stack[--sp]
            val px = p % w
            val py = p / w
            if (px > 0) push(p - 1)
            if (px < w - 1) push(p + 1)
            if (py > 0) push(p - w)
            if (py < h - 1) push(p + w)
        }
        return outside
    }

    private fun labelBubbles(
        enclosed: BooleanArray,
        ink: BooleanArray,
        pixels: IntArray,
        w: Int,
        h: Int,
    ): List<Region> {
        val n = w * h
        val visited = BooleanArray(n)
        val stack = IntArray(n)
        val regions = ArrayList<Region>()

        for (seed in 0 until n) {
            if (!enclosed[seed] || visited[seed]) continue
            var sp = 0
            stack[sp++] = seed
            visited[seed] = true
            var minX = w
            var minY = h
            var maxX = -1
            var maxY = -1
            var blobArea = 0
            while (sp > 0) {
                val p = stack[--sp]
                val px = p % w
                val py = p / w
                blobArea++
                if (px < minX) minX = px
                if (px > maxX) maxX = px
                if (py < minY) minY = py
                if (py > maxY) maxY = py
                if (px > 0 && enclosed[p - 1] && !visited[p - 1]) {
                    visited[p - 1] = true
                    stack[sp++] = p - 1
                }
                if (px < w - 1 && enclosed[p + 1] && !visited[p + 1]) {
                    visited[p + 1] = true
                    stack[sp++] = p + 1
                }
                if (py > 0 && enclosed[p - w] && !visited[p - w]) {
                    visited[p - w] = true
                    stack[sp++] = p - w
                }
                if (py < h - 1 && enclosed[p + w] && !visited[p + w]) {
                    visited[p + w] = true
                    stack[sp++] = p + w
                }
            }

            val bw = maxX - minX + 1
            val bh = maxY - minY + 1
            if (!passesShape(bw, bh, blobArea, w, h)) continue

            var inkCount = 0
            var pureWhiteCount = 0
            var total = 0
            for (yy in minY..maxY) {
                val rb = yy * w
                for (xx in minX..maxX) {
                    total++
                    if (ink[rb + xx]) inkCount++
                    if (pixels[rb + xx] > config.pureWhiteThreshold) pureWhiteCount++
                }
            }
            val textDensity = inkCount.toFloat() / total
            val pureWhiteRatio = pureWhiteCount.toFloat() / total
            if (pureWhiteRatio < config.minPureWhiteRatio) continue
            if (textDensity < config.minTextDensity || textDensity > config.maxTextDensity) continue
            if (!looksLikeText(ink, w, minX, minY, maxX, maxY, h)) continue

            regions.add(pad(minX, minY, maxX + 1, maxY + 1, w, h))
        }
        return regions
    }

    /**
     * Whether the ink inside the bounding box looks like text: several ink blobs, most of
     * them text-sized, and no single blob tall enough to be a stroke of artwork. This is what
     * separates a dialogue bubble from an enclosed white face, body or patch of scenery.
     */
    private fun looksLikeText(
        ink: BooleanArray,
        w: Int,
        minX: Int,
        minY: Int,
        maxX: Int,
        maxY: Int,
        pageHeight: Int,
    ): Boolean {
        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        val maxLetterHeight = maxOf(2, (config.maxLetterHeightFraction * pageHeight).toInt())
        val dominantLimit = config.maxDominantStrokeFraction * bh

        val visited = BooleanArray(bw * bh)
        val stack = IntArray(bw * bh)
        var components = 0
        var small = 0

        for (ly in 0 until bh) {
            for (lx in 0 until bw) {
                if (!ink[(minY + ly) * w + (minX + lx)]) continue
                val seed = ly * bw + lx
                if (visited[seed]) continue
                var sp = 0
                stack[sp++] = seed
                visited[seed] = true
                var top = ly
                var bottom = ly
                while (sp > 0) {
                    val p = stack[--sp]
                    val px = p % bw
                    val py = p / bw
                    if (py < top) top = py
                    if (py > bottom) bottom = py
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            val nx = px + dx
                            val ny = py + dy
                            if (nx < 0 || nx >= bw || ny < 0 || ny >= bh) continue
                            val np = ny * bw + nx
                            if (visited[np]) continue
                            if (ink[(minY + ny) * w + (minX + nx)]) {
                                visited[np] = true
                                stack[sp++] = np
                            }
                        }
                    }
                }
                val compHeight = bottom - top + 1
                if (compHeight > dominantLimit) return false // a big stroke => artwork, not text
                components++
                if (compHeight <= maxLetterHeight) small++
            }
        }

        if (components < config.minInkComponents) return false
        return small.toFloat() / components >= config.minSmallComponentRatio
    }

    private fun passesShape(bw: Int, bh: Int, blobArea: Int, w: Int, h: Int): Boolean {
        val widthFraction = bw / w.toFloat()
        val heightFraction = bh / h.toFloat()
        val areaFraction = (bw.toLong() * bh) / (w.toLong() * h).toFloat()
        if (widthFraction < config.minWidthFraction || widthFraction > config.maxWidthFraction) return false
        if (heightFraction < config.minHeightFraction || heightFraction > config.maxHeightFraction) return false
        if (areaFraction < config.minAreaFraction || areaFraction > config.maxAreaFraction) return false
        val aspect = maxOf(bw.toFloat() / bh, bh.toFloat() / bw)
        if (aspect > config.maxAspect) return false
        val solidity = blobArea.toFloat() / (bw * bh)
        return solidity >= config.minSolidity
    }

    private fun pad(x0: Int, y0: Int, x1: Int, y1: Int, w: Int, h: Int): Region {
        val padX = (x1 - x0) * config.paddingFraction
        val padY = (y1 - y0) * config.paddingFraction
        val left = (x0 - padX).coerceAtLeast(0f)
        val top = (y0 - padY).coerceAtLeast(0f)
        val right = (x1 + padX).coerceAtMost(w.toFloat())
        val bottom = (y1 + padY).coerceAtMost(h.toFloat())
        return Region(left / w, top / h, right / w, bottom / h)
    }
}
