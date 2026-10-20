package eu.kanade.tachiyomi.ui.reader.bubblezoom

/**
 * Orders detected [Region]s into reading order: rows from top to bottom, and
 * within each row left-to-right for left-to-right comics, or right-to-left for
 * right-to-left manga.
 *
 * Regions are grouped into a row while their vertical center falls within the
 * running vertical span of that row, so panels sitting side by side with a
 * small vertical offset are still treated as one row.
 */
object RegionOrdering {

    fun order(regions: List<Region>, rtl: Boolean): List<Region> {
        if (regions.size <= 1) return regions

        return groupIntoRows(regions).flatMap { row ->
            row.sortedBy { if (rtl) -it.centerX else it.centerX }
        }
    }

    private fun groupIntoRows(regions: List<Region>): List<List<Region>> {
        val sorted = regions.sortedBy { it.top }
        val rows = mutableListOf<MutableList<Region>>()
        var rowBottom = Float.NEGATIVE_INFINITY

        for (region in sorted) {
            val currentRow = rows.lastOrNull()
            if (currentRow == null || region.centerY > rowBottom) {
                rows.add(mutableListOf(region))
                rowBottom = region.bottom
            } else {
                currentRow.add(region)
                rowBottom = maxOf(rowBottom, region.bottom)
            }
        }
        return rows
    }
}
