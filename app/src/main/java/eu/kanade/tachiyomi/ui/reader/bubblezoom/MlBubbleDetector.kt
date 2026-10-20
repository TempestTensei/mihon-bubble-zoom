package eu.kanade.tachiyomi.ui.reader.bubblezoom

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.nio.FloatBuffer

/**
 * Speech-bubble detector backed by a YOLOv8 ONNX model run on-device with ONNX Runtime.
 *
 * The model file is expected in assets as [assetName]. If it is missing or fails to load,
 * detection transparently falls back to [fallback] (the classical detector), so the feature
 * keeps working without the model.
 */
class MlBubbleDetector(
    private val context: Context,
    private val fallback: RegionDetector,
    private val assetName: String = "bubble_detector.onnx",
    private val inputSize: Int = 1024,
    private val confThreshold: Float = 0.35f,
    private val iouThreshold: Float = 0.5f,
) : RegionDetector {

    @Volatile private var session: OrtSession? = null

    @Volatile private var initialized = false

    @Volatile private var available = false

    @Synchronized
    private fun ensureInit() {
        if (initialized) return
        initialized = true
        available = try {
            val modelFile = File(context.filesDir, assetName)
            if (!modelFile.exists() || modelFile.length() == 0L) {
                context.assets.open(assetName).use { input ->
                    modelFile.outputStream().use { input.copyTo(it) }
                }
            }
            val env = OrtEnvironment.getEnvironment()
            session = env.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
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
        val letterbox = YoloBubblePostprocessor.computeLetterbox(image.width, image.height, inputSize)
        val input = buildInput(image, letterbox)
        val env = OrtEnvironment.getEnvironment()
        val shape = longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())

        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { tensor ->
            val inputName = session.inputNames.iterator().next()
            session.run(mapOf(inputName to tensor)).use { result ->
                val output = result[0] as OnnxTensor
                val dims = (output.info as TensorInfo).shape
                // Expected [1, channels, anchors] (Ultralytics YOLOv8 export).
                val channels = dims[1].toInt()
                val anchors = dims[2].toInt()
                val channelsFirst = channels < anchors
                val flat = FloatArray(channels * anchors)
                output.floatBuffer.get(flat)

                val boxes = YoloBubblePostprocessor.decode(flat, channels, anchors, channelsFirst, confThreshold)
                val kept = YoloBubblePostprocessor.nms(boxes, iouThreshold)
                return YoloBubblePostprocessor.toRegions(kept, letterbox, image.width, image.height)
            }
        }
    }

    /** Letterboxes the grayscale page into a 3-channel CHW float tensor normalized to [0,1]. */
    private fun buildInput(image: GrayImage, letterbox: YoloBubblePostprocessor.Letterbox): FloatArray {
        val size = inputSize
        val plane = size * size
        val out = FloatArray(3 * plane) { PAD_VALUE }
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
                out[idx] = value
                out[plane + idx] = value
                out[2 * plane + idx] = value
            }
        }
        return out
    }

    companion object {
        private const val PAD_VALUE = 114f / 255f
    }
}
