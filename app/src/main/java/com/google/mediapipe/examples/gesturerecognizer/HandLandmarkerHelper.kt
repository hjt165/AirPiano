package com.google.mediapipe.examples.gesturerecognizer

import android.content.Context
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.TaskResultCallback
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

            if (currentDelegate == DELEGATE_GPU) {
                BaseOptions.Delegate.GPU
            } else {
                BaseOptions.Delegate.CPU
            }.let { delegate ->
                baseOptionsBuilder.setDelegate(delegate)
            }

            val optionsBuilder = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setMinHandDetectionConfidence(minHandDetectionConfidence)
                .setMinHandTrackingConfidence(minHandTrackingConfidence)
                .setMinHandPresenceConfidence(minHandPresenceConfidence)
                .setNumHands(1)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener { result, inputImage ->
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

    fun recognizeLiveStream(imageProxy: android.media.Image) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call HandLandmarker while in the wrong running mode: $runningMode"
            )
        }

        val frameTimeMs = System.currentTimeMillis()

        val bitmap = android.graphics.Bitmap.createBitmap(
            imageProxy.width, imageProxy.height,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(imageProxy.planes[0].buffer)

        val mpImage = BitmapImageBuilder(bitmap).build()
        handLandmarker?.detectAsync(mpImage, frameTimeMs)
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
