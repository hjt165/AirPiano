package com.google.mediapipe.examples.gesturerecognizer

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult

class AirPianoManager(private val context: Context) {

    enum class FingerState { UNKNOWN, TAP_DOWN, HOLD, RELEASE }

    data class FingerTracking(
        var state: FingerState = FingerState.UNKNOWN,
        var tapOffsetDiff: Float = 0f,
        var previousOffsetDiff: Float = 0f
    )

    companion object {
        private const val TAG = "AirPianoManager"
        private const val TAP_THRESHOLD = 0.025f
        private const val THUMB_TAP_THRESHOLD = 0.015f  // lower threshold for thumb
        private const val HOLD_THRESHOLD = 0.015f
        private const val SMOOTHING_FACTOR = 0.8f
        private const val HISTORY_SIZE = 5
        private val FINGER_TIP_INDICES = listOf(4, 8, 12, 16, 20)
        private val NOTE_NAMES = listOf("Do", "Re", "Mi", "Fa", "Sol")
        private val SOUND_RESOURCES = listOf(R.raw.d, R.raw.re, R.raw.mi, R.raw.fa, R.raw.sol)
    }

    var tapThreshold = TAP_THRESHOLD
    var holdThreshold = HOLD_THRESHOLD
    var smoothingFactor = SMOOTHING_FACTOR

    private val fingerStates = Array(5) { FingerTracking() }
    private val fingerSmoothHistory = Array(5) { mutableListOf<Float>() }
    private val palmBaseHistory = mutableListOf<Float>()
    private val mediaPlayers = mutableListOf<MediaPlayer>()
    private var isSoundReady = false

    interface PianoListener {
        fun onFingerPressed(fingerIndex: Int, noteName: String)
        fun onFingerReleased(fingerIndex: Int)
    }

    var listener: PianoListener? = null

    fun setup() {
        setupSoundPool()
    }

    private fun setupSoundPool() {
        try {
            for (resId in SOUND_RESOURCES) {
                val mp = MediaPlayer.create(context, resId)
                if (mp != null) {
                    mediaPlayers.add(mp)
                } else {
                    Log.e(TAG, "Failed to create MediaPlayer for resource $resId")
                    isSoundReady = false
                    return
                }
            }
            isSoundReady = true
        } catch (e: Exception) {
            Log.e(TAG, "Sound setup failed: ${e.message}")
            isSoundReady = false
        }
    }

    @Synchronized
    fun processFrame(result: GestureRecognizerResult) {
        val handLandmarks = result.landmarks()
        if (handLandmarks.isEmpty() || handLandmarks[0].size < 21) {
            resetAllFingers()
            return
        }
        processAirpianoLogic(handLandmarks[0])
    }

    private fun resetAllFingers() {
        for (i in fingerStates.indices) {
            if (fingerStates[i].state != FingerState.UNKNOWN) {
                fingerStates[i].state = FingerState.RELEASE
                listener?.onFingerReleased(i)
            }
            fingerStates[i].state = FingerState.UNKNOWN
            fingerSmoothHistory[i].clear()
        }
        palmBaseHistory.clear()
    }

    private fun processAirpianoLogic(landmarks: List<NormalizedLandmark>) {
        if (landmarks.size < 21) return

        val wristY = landmarks[0].y()

        palmBaseHistory.add(wristY)
        if (palmBaseHistory.size > HISTORY_SIZE) palmBaseHistory.removeAt(0)
        val avgPalmY = palmBaseHistory.average().toFloat()

        for (i in FINGER_TIP_INDICES.indices) {
            val tipIndex = FINGER_TIP_INDICES[i]
            val rawTipY = landmarks[tipIndex].y()

            val smoothedTipY = smoothFingerPosition(i, rawTipY)

            val historyAvg = fingerSmoothHistory[i].let { history ->
                if (history.size > 1) history.subList(0, history.size - 1).average().toFloat()
                else smoothedTipY
            }
            val tipOffset = smoothedTipY - historyAvg
            val palmOffset = wristY - avgPalmY
            val offsetDiff = tipOffset - palmOffset

            // Use lower threshold for thumb (index 0)
            val effectiveTapThreshold = if (i == 0) THUMB_TAP_THRESHOLD else tapThreshold

            val tracking = fingerStates[i]
            tracking.previousOffsetDiff = tracking.tapOffsetDiff

            when (tracking.state) {
                FingerState.UNKNOWN, FingerState.RELEASE -> {
                    if (offsetDiff > effectiveTapThreshold) {
                        tracking.state = FingerState.TAP_DOWN
                        tracking.tapOffsetDiff = offsetDiff
                        playSound(i)
                        listener?.onFingerPressed(i, NOTE_NAMES[i])
                    }
                }
                FingerState.TAP_DOWN -> {
                    if (Math.abs(offsetDiff - tracking.tapOffsetDiff) < holdThreshold) {
                        tracking.state = FingerState.HOLD
                    } else if (offsetDiff < tracking.tapOffsetDiff - effectiveTapThreshold) {
                        tracking.state = FingerState.RELEASE
                        listener?.onFingerReleased(i)
                    }
                }
                FingerState.HOLD -> {
                    if (offsetDiff < tracking.tapOffsetDiff - effectiveTapThreshold) {
                        tracking.state = FingerState.RELEASE
                        listener?.onFingerReleased(i)
                    }
                }
            }
        }
    }

    private fun smoothFingerPosition(fingerIndex: Int, currentY: Float): Float {
        val history = fingerSmoothHistory[fingerIndex]
        history.add(currentY)
        if (history.size > HISTORY_SIZE) history.removeAt(0)

        if (history.size <= 1) return currentY

        var weightedSum = 0f
        var totalWeight = 0f
        for (j in history.indices) {
            val weight = Math.pow(smoothingFactor.toDouble(), (history.size - 1 - j).toDouble()).toFloat()
            weightedSum += history[j] * weight
            totalWeight += weight
        }
        return weightedSum / totalWeight
    }

    private fun playSound(fingerIndex: Int) {
        if (!isSoundReady || fingerIndex >= mediaPlayers.size) return
        try {
            val mp = mediaPlayers[fingerIndex]
            mp.seekTo(0)
            mp.start()
        } catch (e: Exception) {
            Log.e(TAG, "playSound failed: ${e.message}")
        }
    }

    @Synchronized
    fun getFingerStates(): Array<FingerTracking> = fingerStates

    fun release() {
        for (mp in mediaPlayers) {
            try { mp.release() } catch (_: Exception) {}
        }
        mediaPlayers.clear()
        isSoundReady = false
    }
}
