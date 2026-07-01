package com.google.mediapipe.examples.gesturerecognizer

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandLandmarkerHelper(
    var currentDelegate: Int = DELEGATE_CPU,
    var currentModel: String = MODEL_TASK,
    var runningMode: RunningMode = RunningMode.LIVE_STREAM,
    var minHandDetectionConfidence: Float = DEFAULT_HAND_DETECTION_CONFIDENCE,
    var minHandTrackingConfidence: Float = DEFAULT_HAND_TRACKING_CONFIDENCE,
    var minHandPresenceConfidence: Float = DEFAULT_HAND_PRESENCE_CONFIDENCE,
    val context: Context,
    val handLandmarkerListener: HandLandmarkerListener? = null
) {

    private var handLandmarker: HandLandmarker? = null

    init {
        setupHandLandmarker()
    }

    fun setupHandLandmarker() {
        try {
            val baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath(currentModel)

            val delegate = if (currentDelegate == DELEGATE_GPU) {
                Delegate.GPU
            } else {
                Delegate.CPU
            }
            baseOptionsBuilder.setDelegate(delegate)

            val optionsBuilder = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setMinHandDetectionConfidence(minHandDetectionConfidence)
                .setMinTrackingConfidence(minHandTrackingConfidence)
                .setMinHandPresenceConfidence(minHandPresenceConfidence)
                .setNumHands(1)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder.setRunningMode(RunningMode.LIVE_STREAM)
                optionsBuilder.setResultListener { result: HandLandmarkerResult, inputImage: MPImage ->
                    val finishTimeMs = System.currentTimeMillis()
                    val latencyMs = finishTimeMs - result.timestampMs()
                    handLandmarkerListener?.onResults(
                        ResultBundle(
                            result,
                            inputImage.height,
                            inputImage.width,
                            latencyMs
                        )
                    )
                }
            }

            handLandmarker = HandLandmarker.createFromOptions(context, optionsBuilder.build())
        } catch (e: IllegalStateException) {
            handLandmarkerListener?.onError(
                "Hand Landmarker failed to initialize. See error logs for details", GPU_ERROR
            )
            Log.e(TAG, "Hand Landmarker failed to initialize", e)
        } catch (e: RuntimeException) {
            handLandmarkerListener?.onError(
                "Hand Landmarker task GPU delegate not available. Falling back to CPU", GPU_ERROR
            )
            Log.e(TAG, "Hand Landmarker task GPU delegate not available", e)
        }
    }

    fun recognizeLiveStream(imageProxy: ImageProxy) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call HandLandmarker while in the wrong running mode: $runningMode"
            )
        }

        val frameTimeMs = System.currentTimeMillis()
        val bitmap = imageProxyToBitmap(imageProxy)
        val mpImage = BitmapImageBuilder(bitmap).build()
        handLandmarker?.detectAsync(mpImage, frameTimeMs)
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val image = imageProxy.image!!
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride

        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(image.width * image.height)
        buffer.rewind()

        for (row in 0 until image.height) {
            buffer.position(row * rowStride)
            for (col in 0 until image.width) {
                val byteIndex = col * pixelStride
                val r = buffer.get(byteIndex).toInt() and 0xFF
                val g = buffer.get(byteIndex + 1).toInt() and 0xFF
                val b = buffer.get(byteIndex + 2).toInt() and 0xFF
                val a = buffer.get(byteIndex + 3).toInt() and 0xFF
                pixels[row * image.width + col] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        bitmap.setPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        return bitmap
    }

    fun isClosed(): Boolean = handLandmarker == null

    fun clearHandLandmarker() {
        handLandmarker?.close()
        handLandmarker = null
    }

    data class ResultBundle(
        val results: HandLandmarkerResult,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
        val inferenceTime: Long
    )

    interface HandLandmarkerListener {
        fun onResults(resultBundle: ResultBundle)
        fun onError(error: String, errorCode: Int)
    }

    companion object {
        private const val TAG = "HandLandmarkerHelper"
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val MODEL_TASK = "hand_landmarker.task"
        const val GPU_ERROR = 2
        const val DEFAULT_HAND_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_HAND_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_HAND_PRESENCE_CONFIDENCE = 0.5F
    }
}
