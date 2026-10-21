package eu.kanade.tachiyomi.ui.reader.bubblezoom

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Speech-bubble and text detector backed by the RT-DETR comic text/bubble ONNX model, run
 * on-device with ONNX Runtime.
 *
 * The model ([assetName]) detects speech bubbles, text inside bubbles and free-floating text.
 * [RtDetrPostprocessor] keeps the bubbles and free text while collapsing text-inside-bubble
 * into its bubble. If the model is missing or inference fails, detection transparently falls
 * back to [fallback] (the classical detector), so the feature keeps working without the model.
 */
class MlBubbleDetector(
    private val context: Context,
    private val fallback: RegionDetector,
    private val assetName: String = "bubble_detector.onnx",
    private val confThreshold: Float = 0.4f,
) : RegionDetector {

    @Volatile private var session: OrtSession? = null

    @Volatile private var initialized = false

    @Volatile private var available = false

    @Synchronized
    private fun ensureInit() {
        if (initialized) return
        initialized = true
        available = try {
            // Load straight from the bundled asset bytes so a model swap in the APK always takes
            // effect (loading from a cached file could pick up a stale copy from an older build).
            val modelBytes = context.assets.open(assetName).use { it.readBytes() }
            val env = OrtEnvironment.getEnvironment()
            session = env.createSession(modelBytes, OrtSession.SessionOptions())
            logcat { "Bubble ML model loaded from $assetName" }
            true
        } catch (t: Throwable) {
            logcat(LogPriority.INFO, t) { "Bubble ML model unavailable; using classical detector" }
            false
        }
    }

    override fun detect(image: GrayImage): List<Region> {
        ensureInit()
        val activeSession = session
        if (!available || activeSession == null) return fallback.detect(image)
        return try {
            runModel(activeSession, image)
        } catch (t: Throwable) {
            logcat(LogPriority.ERROR, t) { "Bubble ML inference failed; using classical detector" }
            fallback.detect(image)
        }
    }

    private fun runModel(session: OrtSession, image: GrayImage): List<Region> {
        val env = OrtEnvironment.getEnvironment()
        val imageData = buildImageInput(image)
        val imageTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(imageData),
            longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong()),
        )
        // This export scales boxes x by the first value and y by the second, i.e. (width, height).
        val sizeTensor = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(longArrayOf(image.width.toLong(), image.height.toLong())),
            longArrayOf(1, 2),
        )

        imageTensor.use {
            sizeTensor.use {
                session.run(mapOf(INPUT_IMAGES to imageTensor, INPUT_SIZES to sizeTensor)).use { result ->
                    val boxesTensor = result[OUTPUT_BOXES].get() as OnnxTensor
                    val scoresTensor = result[OUTPUT_SCORES].get() as OnnxTensor
                    val boxesBuffer = boxesTensor.floatBuffer.also { it.rewind() }
                    val boxes = FloatArray(boxesBuffer.remaining())
                    boxesBuffer.get(boxes)
                    val scoresBuffer = scoresTensor.floatBuffer.also { it.rewind() }
                    val scores = FloatArray(scoresBuffer.remaining())
                    scoresBuffer.get(scores)

                    return RtDetrPostprocessor.toRegions(
                        boxes = boxes,
                        scores = scores,
                        count = scores.size,
                        origWidth = image.width,
                        origHeight = image.height,
                        scoreThreshold = confThreshold,
                    )
                }
            }
        }
    }

    /** Resizes the grayscale page to [INPUT_SIZE]² and fills a 3-channel CHW float tensor in [0,1]. */
    private fun buildImageInput(image: GrayImage): FloatArray {
        val size = INPUT_SIZE
        val plane = size * size
        val input = FloatArray(3 * plane)
        val srcW = image.width
        val srcH = image.height
        for (y in 0 until size) {
            val srcY = (y.toLong() * srcH / size).toInt().coerceIn(0, srcH - 1)
            val srcRow = srcY * srcW
            val dstRow = y * size
            for (x in 0 until size) {
                val srcX = (x.toLong() * srcW / size).toInt().coerceIn(0, srcW - 1)
                val value = image.pixels[srcRow + srcX] / 255f
                val idx = dstRow + x
                input[idx] = value
                input[plane + idx] = value
                input[2 * plane + idx] = value
            }
        }
        return input
    }

    companion object {
        private const val INPUT_SIZE = 640
        private const val INPUT_IMAGES = "images"
        private const val INPUT_SIZES = "orig_target_sizes"
        private const val OUTPUT_BOXES = "boxes"
        private const val OUTPUT_SCORES = "scores"
    }
}
