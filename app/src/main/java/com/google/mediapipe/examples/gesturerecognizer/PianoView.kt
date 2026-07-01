package com.google.mediapipe.examples.gesturerecognizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class PianoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val whiteKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blackKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var whiteKeys: List<RectF> = emptyList()
    private var blackKeys: List<RectF> = emptyList()

    private val whiteKeyNotes = listOf("C", "D", "E", "F", "G", "A", "B")
    private val hasBlackAfter = listOf(true, true, false, true, true, true, false)

    private var pressedKeys: Set<Int> = emptySet()
    private var currentNote: String = ""

    private var numWhiteKeys = 14
    private var keyWidth = 0f
    private var keyHeight = 0f

    private val KEY_COLORS = intArrayOf(
        Color.parseColor("#F5F5F5"),
        Color.parseColor("#E8E8E8"),
        Color.parseColor("#F5F5F5"),
        Color.parseColor("#E8E8E8"),
        Color.parseColor("#F5F5F5"),
        Color.parseColor("#E8E8E8"),
        Color.parseColor("#F5F5F5")
    )

    init {
        blackKeyPaint.color = Color.parseColor("#1A1A1A")
        pressedPaint.color = Color.parseColor("#00BFA5")
        borderPaint.color = Color.parseColor("#333333")
        borderPaint.strokeWidth = 2f
        borderPaint.style = Paint.Style.STROKE

        textPaint.color = Color.parseColor("#888888")
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 24f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateKeyLayout(w, h)
    }

    private fun calculateKeyLayout(width: Int, height: Int) {
        keyWidth = width.toFloat() / numWhiteKeys
        keyHeight = height.toFloat()

        val whiteKeyList = mutableListOf<RectF>()
        val blackKeyList = mutableListOf<RectF>()

        for (i in 0 until numWhiteKeys) {
            val left = i * keyWidth
            whiteKeyList.add(RectF(left, 0f, left + keyWidth, keyHeight))

            val scaleIndex = i % 7
            if (hasBlackAfter[scaleIndex]) {
                val blackLeft = left + keyWidth * 0.65f
                val blackRight = left + keyWidth * 0.95f
                val blackBottom = keyHeight * 0.6f
                blackKeyList.add(RectF(blackLeft, 0f, blackRight, blackBottom))
            }
        }

        whiteKeys = whiteKeyList
        blackKeys = blackKeyList
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (i in whiteKeys.indices) {
            val key = whiteKeys[i]
            val isPressed = pressedKeys.contains(i)

            if (isPressed) {
                val gradient = LinearGradient(
                    key.left, key.top, key.left, key.bottom,
                    Color.parseColor("#00BFA5"),
                    Color.parseColor("#00897B"),
                    Shader.TileMode.CLAMP
                )
                pressedPaint.shader = gradient
                canvas.drawRect(key, pressedPaint)
            } else {
                whiteKeyPaint.color = KEY_COLORS[i % 7]
                canvas.drawRect(key, whiteKeyPaint)
            }

            canvas.drawRect(key, borderPaint)

            val noteIndex = i % 7
            val octave = 4 + i / 7
            val noteName = "${whiteKeyNotes[noteIndex]}$octave"

            textPaint.textSize = keyWidth * 0.15f
            canvas.drawText(
                noteName,
                key.centerX(),
                key.bottom - keyWidth * 0.1f,
                textPaint
            )
        }

        for (blackKey in blackKeys) {
            canvas.drawRect(blackKey, blackKeyPaint)
        }
    }

    fun setPressedKeys(keys: Set<Int>) {
        if (pressedKeys != keys) {
            pressedKeys = keys
            invalidate()
        }
    }

    fun setCurrentNote(note: String) {
        if (currentNote != note) {
            currentNote = note
            invalidate()
        }
    }

    fun getWhiteKeyCount(): Int = numWhiteKeys

    fun getWhiteKeyWidth(): Float = keyWidth

    fun getWhiteKeyRect(index: Int): RectF? = whiteKeys.getOrNull(index)

    fun getBlackKeyRects(): List<RectF> = blackKeys
}
