package com.google.mediapipe.examples.gesturerecognizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
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
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    init {
        initPaints()
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
        linePaint.color =
            ContextCompat.getColor(context!!, R.color.mp_color_primary)
        linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.WHITE
        pointPaint.strokeWidth = 6f
        pointPaint.style = Paint.Style.FILL

        fingertipPaint.color = Color.parseColor("#00BFA5")
        fingertipPaint.strokeWidth = 12f
        fingertipPaint.style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        results?.let { handLandmarkerResult ->
            for (landmark in handLandmarkerResult.landmarks()) {
                for (normalizedLandmark in landmark) {
                    canvas.drawPoint(
                        normalizedLandmark.x() * imageWidth * scaleFactor,
                        normalizedLandmark.y() * imageHeight * scaleFactor,
                        pointPaint
                    )
                }

                HandLandmarker.HAND_CONNECTIONS?.forEach {
                    canvas.drawLine(
                        landmark[it!!.start()].x() * imageWidth * scaleFactor,
                        landmark[it.start()].y() * imageHeight * scaleFactor,
                        landmark[it.end()].x() * imageWidth * scaleFactor,
                        landmark[it.end()].y() * imageHeight * scaleFactor,
                        linePaint
                    )
                }

                val fingertipIndices = listOf(4, 8, 12, 16, 20)
                for (idx in fingertipIndices) {
                    if (idx < landmark.size) {
                        canvas.drawCircle(
                            landmark[idx].x() * imageWidth * scaleFactor,
                            landmark[idx].y() * imageHeight * scaleFactor,
                            10f,
                            fingertipPaint
                        )
                    }
                }
            }
        }
    }

    fun setResults(
        handLandmarkerResult: HandLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        results = handLandmarkerResult

        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        scaleFactor = when (runningMode) {
            RunningMode.IMAGE,
            RunningMode.VIDEO -> {
                min(width * 1f / imageWidth, height * 1f / imageHeight)
            }
            RunningMode.LIVE_STREAM -> {
                max(width * 1f / imageWidth, height * 1f / imageHeight)
            }
        }
        invalidate()
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 8F
    }
}
