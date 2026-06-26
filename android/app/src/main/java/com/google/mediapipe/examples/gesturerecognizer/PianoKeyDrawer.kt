package com.google.mediapipe.examples.gesturerecognizer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

class PianoKeyDrawer {

    companion object {
        private const val KEY_COUNT = 5
        private val KEY_LABELS = arrayOf("Do", "Re", "Mi", "Fa", "Sol")
        private val KEY_COLORS_PRESSED = intArrayOf(
            Color.parseColor("#FF6B6B"),  // Red - thumb/Do
            Color.parseColor("#FFA07A"),  // Orange - index/Re
            Color.parseColor("#FFD700"),  // Gold - middle/Mi
            Color.parseColor("#98FB98"),  // Green - ring/Fa
            Color.parseColor("#87CEEB"),  // Blue - pinky/Sol
        )
    }

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.DKGRAY
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textAlign = Paint.Align.CENTER
        textSize = 36f
        isFakeBoldText = true
    }
    private val feedbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 48f
        isFakeBoldText = true
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }
    private val pressedStates = BooleanArray(KEY_COUNT)
    var density: Float = 2f

    fun setKeyPressed(index: Int, pressed: Boolean) {
        if (index in 0 until KEY_COUNT) pressedStates[index] = pressed
    }

    fun setAllReleased() {
        pressedStates.fill(false)
    }

    fun draw(canvas: Canvas, viewWidth: Int, viewHeight: Int, bottomInset: Int = 0) {
        val keyWidth = viewWidth.toFloat() / KEY_COUNT
        val keyHeight = viewHeight * 0.18f
        val bottomOffset = viewHeight * 0.12f + bottomInset
        val topY = viewHeight - keyHeight - bottomOffset

        textPaint.textSize = 36f * density
        feedbackPaint.textSize = 24f * density

        for (i in 0 until KEY_COUNT) {
            val left = i * keyWidth
            val rect = RectF(left, topY, left + keyWidth, viewHeight.toFloat())

            // Fill
            keyPaint.color = if (pressedStates[i]) KEY_COLORS_PRESSED[i] else Color.WHITE
            keyPaint.style = Paint.Style.FILL
            canvas.drawRect(rect, keyPaint)

            // Semi-transparent overlay for depth
            if (pressedStates[i]) {
                keyPaint.color = Color.argb(40, 0, 0, 0)
                canvas.drawRect(rect, keyPaint)
            }

            // Border
            canvas.drawRect(rect, borderPaint)

            // Label
            val centerX = left + keyWidth / 2
            val centerY = topY + keyHeight / 2
            textPaint.color = if (pressedStates[i]) Color.WHITE else Color.DKGRAY
            canvas.drawText(KEY_LABELS[i], centerX, centerY + 12f, textPaint)
        }

        // Draw note feedback text above pressed keys
        for (i in 0 until KEY_COUNT) {
            if (pressedStates[i]) {
                val centerX = i * keyWidth + keyWidth / 2
                val feedbackY = topY - 16f * density
                feedbackPaint.color = KEY_COLORS_PRESSED[i]
                canvas.drawText(KEY_LABELS[i], centerX, feedbackY, feedbackPaint)
            }
        }
    }
}
