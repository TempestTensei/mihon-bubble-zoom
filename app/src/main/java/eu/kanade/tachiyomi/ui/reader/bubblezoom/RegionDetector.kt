package eu.kanade.tachiyomi.ui.reader.bubblezoom

/**
 * Detects candidate panel [Region]s on a downscaled grayscale page.
 *
 * Implementations run off the main thread and must be side-effect free so a
 * result can be cached per page. Returning an empty list means "no usable
 * regions" and signals the caller to show the normal full page.
 */
fun interface RegionDetector {
    fun detect(image: GrayImage): List<Region>
}
