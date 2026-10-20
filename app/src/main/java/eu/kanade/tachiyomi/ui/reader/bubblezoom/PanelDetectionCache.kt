package eu.kanade.tachiyomi.ui.reader.bubblezoom

/**
 * Caches panel-detection results per page so revisiting a page never recomputes.
 *
 * Detection runs off the main thread via [detectAndCache]; the main thread reads
 * cached results with [getOrNull]. Both are synchronized. An empty result is
 * cached too, so a page with no usable regions is not re-detected on revisit.
 *
 * Eviction is least-recently-used, bounded by [maxEntries].
 */
class PanelDetectionCache(
    private val detector: RegionDetector,
    private val maxEntries: Int = 64,
) {
    private val cache = object : LinkedHashMap<String, List<Region>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Region>>): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    fun getOrNull(key: String): List<Region>? = cache[key]

    @Synchronized
    fun detectAndCache(key: String, image: GrayImage, rtl: Boolean): List<Region> {
        cache[key]?.let { return it }
        val ordered = RegionOrdering.order(detector.detect(image), rtl)
        cache[key] = ordered
        return ordered
    }
}
