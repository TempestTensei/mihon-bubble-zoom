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
    private val panelDetector: RegionDetector? = null,
    private val maxEntries: Int = 64,
) {
    private val lock = Any()

    private val cache = object : LinkedHashMap<String, List<Region>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Region>>): Boolean {
            return size > maxEntries
        }
    }

    fun getOrNull(key: String): List<Region>? = synchronized(lock) { cache[key] }

    fun detectAndCache(key: String, image: GrayImage, rtl: Boolean): List<Region> {
        synchronized(lock) { cache[key] }?.let { return it }
        // Run detection outside the lock so main-thread reads never block on it.
        val regions = detector.detect(image)
        val panels = panelDetector?.detect(image).orEmpty()
        val ordered = PanelAwareOrdering.order(regions, panels, rtl)
        return synchronized(lock) { cache.getOrPut(key) { ordered } }
    }
}
