package eu.kanade.tachiyomi.ui.reader.bubblezoom

import kotlin.math.max
import kotlin.math.min

/**
 * Orders detected text/bubble [Region]s by panel: panels are visited in reading order (rows
 * top-to-bottom, right-to-left within a row for manga), and within each panel its regions are
 * ordered the same way. Regions that fall outside every panel are appended at the end in plain
 * reading order.
 *
 * Falls back to [RegionOrdering] when no panels are available.
 */
object PanelAwareOrdering {

    fun order(regions: List<Region>, panels: List<Region>, rtl: Boolean): List<Region> {
        if (panels.isEmpty()) return RegionOrdering.order(regions, rtl)

        val orderedPanels = RegionOrdering.order(panels, rtl)
        val buckets = Array(orderedPanels.size) { mutableListOf<Region>() }
        val unassigned = mutableListOf<Region>()

        for (region in regions) {
            val panelIndex = bestPanel(region, orderedPanels)
            if (panelIndex >= 0) buckets[panelIndex].add(region) else unassigned.add(region)
        }

        val result = ArrayList<Region>(regions.size)
        for (bucket in buckets) {
            result.addAll(RegionOrdering.order(bucket, rtl))
        }
        result.addAll(RegionOrdering.order(unassigned, rtl))
        return result
    }

    /** Index of the panel that covers the most of [region], or -1 if none covers at least half. */
    private fun bestPanel(region: Region, panels: List<Region>): Int {
        var bestIndex = -1
        var bestCoverage = MIN_COVERAGE
        panels.forEachIndexed { index, panel ->
            val coverage = coverage(region, panel)
            if (coverage > bestCoverage) {
                bestCoverage = coverage
                bestIndex = index
            }
        }
        return bestIndex
    }

    /** Fraction of [region]'s area that lies inside [panel]. */
    private fun coverage(region: Region, panel: Region): Float {
        val ix0 = max(region.left, panel.left)
        val iy0 = max(region.top, panel.top)
        val ix1 = min(region.right, panel.right)
        val iy1 = min(region.bottom, panel.bottom)
        val iw = max(0f, ix1 - ix0)
        val ih = max(0f, iy1 - iy0)
        val area = region.area
        return if (area <= 0f) 0f else (iw * ih) / area
    }

    private const val MIN_COVERAGE = 0.5f
}
