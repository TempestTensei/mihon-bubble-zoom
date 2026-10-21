package eu.kanade.tachiyomi.ui.reader.bubblezoom

import java.util.IdentityHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Orders detected text/bubble [Region]s by panel.
 *
 * Each detected panel that holds at least one region becomes a cell (its box = the panel box);
 * each region that falls outside every detected panel becomes its own cell (its box = the region
 * box). Cells are visited in reading order (rows top-to-bottom, right-to-left within a row for
 * manga), and a panel cell's regions are ordered the same way. Making un-panelled regions their
 * own cells means that when the panel detector only finds *some* panels, the remaining regions
 * are still placed by their position, not dumped at the end.
 *
 * Falls back to [RegionOrdering] when no panels are available.
 */
object PanelAwareOrdering {

    private class Cell(val box: Region, val regions: List<Region>)

    fun order(regions: List<Region>, panels: List<Region>, rtl: Boolean): List<Region> {
        if (regions.isEmpty()) return regions
        if (panels.isEmpty()) return RegionOrdering.order(regions, rtl)

        val buckets = HashMap<Int, MutableList<Region>>()
        val cells = ArrayList<Cell>()
        for (region in regions) {
            val panelIndex = bestPanel(region, panels)
            if (panelIndex >= 0) {
                buckets.getOrPut(panelIndex) { mutableListOf() }.add(region)
            } else {
                cells.add(Cell(region, listOf(region)))
            }
        }
        buckets.forEach { (panelIndex, bucket) ->
            cells.add(Cell(panels[panelIndex], RegionOrdering.order(bucket, rtl)))
        }

        val cellByBox = IdentityHashMap<Region, Cell>()
        cells.forEach { cellByBox[it.box] = it }
        val orderedBoxes = RegionOrdering.order(cells.map { it.box }, rtl)

        val result = ArrayList<Region>(regions.size)
        for (box in orderedBoxes) {
            cellByBox[box]?.let { result.addAll(it.regions) }
        }
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
