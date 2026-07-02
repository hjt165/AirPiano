package com.google.mediapipe.examples.gesturerecognizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.max
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    private var results: HandLandmarkerResult? = null
    private var linePaint = Paint()
    private var pointPaint = Paint()
    private var fingertipPaint = Paint()

    private var scaleFactor: Float = 1f
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var isFrontCamera: Boolean = true
    private var currentRunningMode: RunningMode = RunningMode.LIVE_STREAM
    private var pendingResult: HandLandmarkerResult? = null

    // Piano overlay
    private var whiteKeyRects: List<RectF> = emptyList()
    private var blackKeyRects: List<RectF> = emptyList()
    private val whiteKeyNotes = listOf(
        "C4", "D4", "E4", "F4", "G4", "A4", "B4",
        "C5", "D5", "E5", "F5", "G5", "A5", "B5"
    )
    private val blackKeyNotes = listOf(
        "C#4", "D#4", "F#4", "G#4", "A#4",
        "C#5", "D#5", "F#5", "G#5", "A#5"
    )
    private val hasBlackAfter = listOf(true, true, false, true, true, true, false)

    private var pressedWhiteKeys: Set<Int> = emptySet()
    private var pressedBlackKeys: Set<Int> = emptySet()
    private var pressedNotes: List<String> = emptyList()

    // Paints for piano
    private val whiteKeyNormalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 110
        style = Paint.Style.FILL
    }
    private val whiteKeyPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val blackKeyNormalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        alpha = 130
        style = Paint.Style.FILL
    }
    private val blackKeyPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val keyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }
    private val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    // Info text
    private var inferenceTime: Long = 0
    private var currentNote: String = "--"

    init {
        initPaints()
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            pendingResult?.let { recalculateAndInvalidate() }
            calculatePianoLayout()
        }
    }

    fun clear() {
        results = null
        linePaint.reset()
        pointPaint.reset()
        fingertipPaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        linePaint.color = ContextCompat.getColor(context!!, R.color.mp_color_primary)
        linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.WHITE
        pointPaint.strokeWidth = 6f
        pointPaint.style = Paint.Style.FILL

        fingertipPaint.color = Color.parseColor("#00BFA5")
        fingertipPaint.strokeWidth = 12f
        fingertipPaint.style = Paint.Style.FILL
    }

    private fun calculatePianoLayout() {
        if (width == 0 || height == 0) return

        val pianoWidth = width * 0.95f
        val pianoHeight = height * 0.35f
        val pianoLeft = (width - pianoWidth) / 2f
        val pianoTop = height * 0.10f  // Place piano at 10% from top
        val numWhiteKeys = 14
        val keyWidth = pianoWidth / numWhiteKeys

        val whiteList = mutableListOf<RectF>()
        val blackList = mutableListOf<RectF>()

        for (i in 0 until numWhiteKeys) {
            val left = pianoLeft + i * keyWidth
            whiteList.add(RectF(left, pianoTop, left + keyWidth, pianoTop + pianoHeight))
        }

        var blackIndex = 0
        for (i in 0 until numWhiteKeys - 1) {
            val scaleIndex = i % 7
            if (hasBlackAfter[scaleIndex]) {
                val left = pianoLeft + (i + 1) * keyWidth - keyWidth * 0.35f
                val blackWidth = keyWidth * 0.70f
                val blackHeight = pianoHeight * 0.65f
                blackList.add(RectF(left, pianoTop, left + blackWidth, pianoTop + blackHeight))
                blackIndex++
            }
        }

        whiteKeyRects = whiteList
        blackKeyRects = blackList
    }

    private fun landmarkToViewX(normalizedX: Float): Float {
        val mirroredX = if (isFrontCamera) 1f - normalizedX else normalizedX
        return mirroredX * imageWidth * scaleFactor + offsetX
    }

    private fun landmarkToViewY(normalizedY: Float): Float {
        return normalizedY * imageHeight * scaleFactor + offsetY
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        // Draw hand landmarks
        drawHandLandmarks(canvas)

        // Draw piano overlay
        drawPiano(canvas)

        // Draw info text
        drawInfo(canvas)
    }

    private fun drawHandLandmarks(canvas: Canvas) {
        results?.let { handLandmarkerResult ->
            for (landmark in handLandmarkerResult.landmarks()) {
                // Draw connections
                HandLandmarker.HAND_CONNECTIONS?.forEach {
                    canvas.drawLine(
                        landmarkToViewX(landmark[it!!.start()].x()),
                        landmarkToViewY(landmark[it.start()].y()),
                        landmarkToViewX(landmark[it.end()].x()),
                        landmarkToViewY(landmark[it.end()].y()),
                        linePaint
                    )
                }

                // Draw all points
                for (normalizedLandmark in landmark) {
                    canvas.drawPoint(
                        landmarkToViewX(normalizedLandmark.x()),
                        landmarkToViewY(normalizedLandmark.y()),
                        pointPaint
                    )
                }

                // Draw fingertip highlights
                val fingertipIndices = listOf(4, 8, 12, 16, 20)
                for (idx in fingertipIndices) {
                    if (idx < landmark.size) {
                        canvas.drawCircle(
                            landmarkToViewX(landmark[idx].x()),
                            landmarkToViewY(landmark[idx].y()),
                            10f,
                            fingertipPaint
                        )
                    }
                }
            }
        }
    }

    private fun drawPiano(canvas: Canvas) {
        if (whiteKeyRects.isEmpty()) return

        // Draw white keys
        for (i in whiteKeyRects.indices) {
            val key = whiteKeyRects[i]
            val isPressed = pressedWhiteKeys.contains(i)

            if (isPressed) {
                whiteKeyPressedPaint.shader = LinearGradient(
                    key.left, key.top, key.left, key.bottom,
                    Color.parseColor("#00BFA5"),
                    Color.parseColor("#00897B"),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(key, whiteKeyPressedPaint)
            } else {
                canvas.drawRect(key, whiteKeyNormalPaint)
            }
            canvas.drawRect(key, keyBorderPaint)
        }

        // Draw black keys
        for (i in blackKeyRects.indices) {
            val key = blackKeyRects[i]
            val isPressed = pressedBlackKeys.contains(i)

            if (isPressed) {
                blackKeyPressedPaint.color = Color.parseColor("#00BFA5")
                canvas.drawRect(key, blackKeyPressedPaint)
            } else {
                canvas.drawRect(key, blackKeyNormalPaint)
            }
            canvas.drawRect(key, keyBorderPaint)
        }
    }

    private fun drawInfo(canvas: Canvas) {
        infoPaint.textSize = 32f
        val pianoBottom = if (whiteKeyRects.isNotEmpty()) whiteKeyRects[0].bottom + 40f else 120f
        canvas.drawText("推理时间: ${inferenceTime}ms", 20f, pianoBottom, infoPaint)
        canvas.drawText("当前音符: $currentNote", 20f, pianoBottom + 40f, infoPaint)
    }

    fun detectPressedKeys(result: HandLandmarkerResult) {
        if (whiteKeyRects.isEmpty() || blackKeyRects.isEmpty()) {
            calculatePianoLayout()
        }

        val newPressedWhite = mutableSetOf<Int>()
        val newPressedBlack = mutableSetOf<Int>()
        val newPressedNotes = mutableListOf<String>()

        if (result.landmarks().isEmpty()) {
            pressedWhiteKeys = emptySet()
            pressedBlackKeys = emptySet()
            pressedNotes = emptyList()
            return
        }

        val landmarks = result.landmarks()[0]
        val fingertipIndices = intArrayOf(8, 12, 16, 20) // index, middle, ring, little

        for (tipIdx in fingertipIndices) {
            if (tipIdx >= landmarks.size) continue
            val tip = landmarks[tipIdx]
            val tipX = landmarkToViewX(tip.x())
            val tipY = landmarkToViewY(tip.y())

            Log.d(TAG, "Finger tip $tipIdx: normalized(${tip.x()}, ${tip.y()}) -> view($tipX, $tipY), pianoTop=${if (whiteKeyRects.isNotEmpty()) whiteKeyRects[0].top else 0}, pianoBottom=${if (whiteKeyRects.isNotEmpty()) whiteKeyRects[0].bottom else 0}")

            // Check black keys first (they're on top)
            var hitBlack = false
            for (i in blackKeyRects.indices) {
                if (blackKeyRects[i].contains(tipX, tipY)) {
                    newPressedBlack.add(i)
                    if (i < blackKeyNotes.size) {
                        newPressedNotes.add(blackKeyNotes[i])
                    }
                    hitBlack = true
                    break
                }
            }

            if (!hitBlack) {
                for (i in whiteKeyRects.indices) {
                    if (whiteKeyRects[i].contains(tipX, tipY)) {
                        newPressedWhite.add(i)
                        if (i < whiteKeyNotes.size) {
                            newPressedNotes.add(whiteKeyNotes[i])
                        }
                        break
                    }
                }
            }
        }

        pressedWhiteKeys = newPressedWhite
        pressedBlackKeys = newPressedBlack
        pressedNotes = newPressedNotes
    }

    fun setResults(
        handLandmarkerResult: HandLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE,
        frontCamera: Boolean = true
    ) {
        results = handLandmarkerResult
        this.imageHeight = imageHeight
        this.imageWidth = imageWidth
        this.isFrontCamera = frontCamera
        this.currentRunningMode = runningMode

        // Detect pressed keys
        detectPressedKeys(handLandmarkerResult)

        if (width == 0 || height == 0) {
            pendingResult = handLandmarkerResult
            return
        }

        recalculateAndInvalidate()
    }

    fun setInferenceTime(time: Long) {
        inferenceTime = time
    }

    fun setCurrentNote(note: String) {
        currentNote = note
    }

    fun getPressedNotes(): List<String> = pressedNotes

    private fun recalculateAndInvalidate() {
        pendingResult = null
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        scaleFactor = when (currentRunningMode) {
            RunningMode.IMAGE,
            RunningMode.VIDEO -> {
                min(viewWidth / imageWidth, viewHeight / imageHeight)
            }
            RunningMode.LIVE_STREAM -> {
                max(viewWidth / imageWidth, viewHeight / imageHeight)
            }
        }

        offsetX = (viewWidth - imageWidth * scaleFactor) / 2f
        offsetY = (viewHeight - imageHeight * scaleFactor) / 2f

        calculatePianoLayout()
        invalidate()
    }

    companion object {
        private const val TAG = "OverlayView"
        private const val LANDMARK_STROKE_WIDTH = 8F
    }
}
