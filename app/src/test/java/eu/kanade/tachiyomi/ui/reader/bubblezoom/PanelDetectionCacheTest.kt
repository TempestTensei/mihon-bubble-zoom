package eu.kanade.tachiyomi.ui.reader.bubblezoom

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PanelDetectionCacheTest {

    private val image = GrayImage(1, 1, intArrayOf(255))

    private class CountingDetector(private val regions: List<Region>) : RegionDetector {
        var calls = 0
        override fun detect(image: GrayImage): List<Region> {
            calls++
            return regions
        }
    }

    @Test
    fun `detectAndCache runs the detector once per key and caches the result`() {
        val detector = CountingDetector(listOf(Region(0f, 0f, 0.4f, 1f)))
        val cache = PanelDetectionCache(detector)

        val first = cache.detectAndCache("k", image, rtl = false)
        val second = cache.detectAndCache("k", image, rtl = false)

        detector.calls shouldBe 1
        second shouldBe first
        cache.getOrNull("k") shouldBe first
    }

    @Test
    fun `detectAndCache caches an empty result without re-running the detector`() {
        val detector = CountingDetector(emptyList())
        val cache = PanelDetectionCache(detector)

        cache.detectAndCache("k", image, rtl = false)
        cache.detectAndCache("k", image, rtl = false)

        detector.calls shouldBe 1
        cache.getOrNull("k") shouldBe emptyList()
    }

    @Test
    fun `getOrNull returns null for an unknown key`() {
        val cache = PanelDetectionCache(CountingDetector(emptyList()))

        cache.getOrNull("missing").shouldBeNull()
    }

    @Test
    fun `results are ordered by reading direction`() {
        val left = Region(0.05f, 0.1f, 0.45f, 0.9f)
        val right = Region(0.55f, 0.1f, 0.95f, 0.9f)
        // Detector returns them right-to-left; the cache must apply reading order.
        val cache = PanelDetectionCache(CountingDetector(listOf(right, left)))

        cache.detectAndCache("k", image, rtl = false) shouldBe listOf(left, right)
    }

    @Test
    fun `least recently used entry is evicted past capacity`() {
        val cache = PanelDetectionCache(CountingDetector(listOf(Region(0f, 0f, 0.4f, 1f))), maxEntries = 2)

        cache.detectAndCache("k1", image, rtl = false)
        cache.detectAndCache("k2", image, rtl = false)
        cache.detectAndCache("k3", image, rtl = false)

        cache.getOrNull("k1").shouldBeNull()
        (cache.getOrNull("k2") != null) shouldBe true
        (cache.getOrNull("k3") != null) shouldBe true
    }
}
