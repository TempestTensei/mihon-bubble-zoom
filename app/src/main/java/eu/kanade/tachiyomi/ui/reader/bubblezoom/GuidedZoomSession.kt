package eu.kanade.tachiyomi.ui.reader.bubblezoom

/**
 * The outcome of a single guided-zoom navigation step.
 */
sealed interface StepResult {
    /** Zoom the page to [region], which is the region at [index] in reading order. */
    data class Zoom(val region: Region, val index: Int) : StepResult

    /** No region remains in this direction; the caller should turn the page. */
    data object CrossPage : StepResult

    /** The page has no usable regions; show the normal full page without zooming. */
    data object FullPage : StepResult
}

/**
 * Drives bubble-zoom navigation across the ordered [regions] of a single page.
 *
 * The cursor starts before the first region. Entering a page from the start or
 * end positions the cursor on the first or last region; [forward]/[backward]
 * then step within the page and report [StepResult.CrossPage] once no region
 * remains, at which point the caller turns the page.
 */
class GuidedZoomSession(private val regions: List<Region>) {

    var index: Int = -1
        private set

    val size: Int get() = regions.size

    fun currentRegion(): Region? = regions.getOrNull(index)

    fun enterFromStart(): StepResult {
        if (regions.isEmpty()) return StepResult.FullPage
        index = 0
        return StepResult.Zoom(regions[0], 0)
    }

    fun enterFromEnd(): StepResult {
        if (regions.isEmpty()) return StepResult.FullPage
        index = regions.lastIndex
        return StepResult.Zoom(regions[index], index)
    }

    fun forward(): StepResult {
        if (regions.isEmpty()) return StepResult.CrossPage
        if (index < regions.lastIndex) {
            index++
            return StepResult.Zoom(regions[index], index)
        }
        return StepResult.CrossPage
    }

    fun backward(): StepResult {
        if (regions.isEmpty()) return StepResult.CrossPage
        if (index > 0) {
            index--
            return StepResult.Zoom(regions[index], index)
        }
        return StepResult.CrossPage
    }
}
