package eu.kanade.tachiyomi.ui.reader.bubblezoom

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.nio.FloatBuffer

/**
 * Manga panel detector backed by an end-to-end YOLO ONNX model run on-device with ONNX Runtime.
 *
 * Returns panel rectangles (class 0) as [Region]s, used only to order bubbles/text panel by
 * panel. Returns an empty list when the model is missing or inference fails, in which case the
 * ordering falls back to plain reading order.
 */
class MlPanelDetector(
    private val context: Context,
    private val assetName: String = "panel_detector.onnx",
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
            val modelBytes = context.assets.open(assetName).use { it.readBytes() }
            val env = OrtEnvironment.getEnvironment()
            session = env.createSession(modelBytes, OrtSession.SessionOptions())
            logcat { "Panel ML model loaded from $assetName" }
            true
        } catch (t: Throwable) {
            logcat(LogPriority.INFO, t) { "Panel ML model unavailable; ordering without panels" }
            false
        }
    }

    override fun detect(image: GrayImage): List<Region> {
        ensureInit()
        val activeSession = session
        if (!available || activeSession == null) return emptyList()
        return try {
            runModel(activeSession, image)
        } catch (t: Throwable) {
            logcat(LogPriority.ERROR, t) { "Panel ML inference failed; ordering without panels" }
            emptyList()
        }
    }

    private fun runModel(session: OrtSession, image: GrayImage): List<Region> {
        val env = OrtEnvironment.getEnvironment()
        val letterbox = PanelPostprocessor.computeLetterbox(image.width, image.height, INPUT_SIZE)
        val input = buildInput(image, letterbox)
        val tensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(input),
            longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong()),
        )
        tensor.use {
            session.run(mapOf(INPUT_IMAGES to tensor)).use { result ->
                val outTensor = result[OUTPUT].get() as OnnxTensor
                val buffer = outTensor.floatBuffer.also { it.rewind() }
                val flat = FloatArray(buffer.remaining())
                buffer.get(flat)
                return PanelPostprocessor.toPanels(
                    output = flat,
                    count = flat.size / 6,
                    letterbox = letterbox,
                    origWidth = image.width,
                    origHeight = image.height,
                    scoreThreshold = confThreshold,
                )
            }
        }
    }

    /** Letterboxes (aspect-preserving, gray pad) the grayscale page into a 3-channel CHW tensor. */
    private fun buildInput(image: GrayImage, letterbox: PanelPostprocessor.Letterbox): FloatArray {
        val size = INPUT_SIZE
        val plane = size * size
        val input = FloatArray(3 * plane) { PAD_VALUE }
        val scale = letterbox.scale
        val scaledWidth = (image.width * scale).toInt()
        val scaledHeight = (image.height * scale).toInt()
        val offsetX = letterbox.padX.toInt()
        val offsetY = letterbox.padY.toInt()
        for (y in 0 until scaledHeight) {
            val destY = offsetY + y
            if (destY < 0 || destY >= size) continue
            val srcY = (y / scale).toInt().coerceIn(0, image.height - 1)
            val srcRow = srcY * image.width
            val destRow = destY * size
            for (x in 0 until scaledWidth) {
                val destX = offsetX + x
                if (destX < 0 || destX >= size) continue
                val srcX = (x / scale).toInt().coerceIn(0, image.width - 1)
                val value = image.pixels[srcRow + srcX] / 255f
                val idx = destRow + destX
                input[idx] = value
                input[plane + idx] = value
                input[2 * plane + idx] = value
            }
        }
        return input
    }

    companion object {
        private const val INPUT_SIZE = 1024
        private const val INPUT_IMAGES = "images"
        private const val OUTPUT = "output0"
        private const val PAD_VALUE = 114f / 255f
    }
}
