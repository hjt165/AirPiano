package com.google.mediapipe.examples.gesturerecognizer

import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class FingerPressDetector(
    private var pressThreshold: Float = DEFAULT_PRESS_THRESHOLD
) {

    private val fingertipIndices = intArrayOf(
        THUMB_TIP,
        INDEX_FINGER_TIP,
        MIDDLE_FINGER_TIP,
        RING_FINGER_TIP,
        LITTLE_FINGER_TIP
    )

    private val fingerMcpIndices = intArrayOf(
        THUMB_MCP,
        INDEX_FINGER_MCP,
        MIDDLE_FINGER_MCP,
        RING_FINGER_MCP,
        LITTLE_FINGER_MCP
    )

    fun detectPress(result: HandLandmarkerResult): Set<Int> {
        val pressedFingers = mutableSetOf<Int>()

        if (result.landmarks().isEmpty()) return pressedFingers

        val landmarks = result.landmarks()[0]

        for (i in fingertipIndices.indices) {
            val tipIdx = fingertipIndices[i]
            val mcpIdx = fingerMcpIndices[i]

            if (tipIdx >= landmarks.size || mcpIdx >= landmarks.size) continue

            val tip = landmarks[tipIdx]
            val mcp = landmarks[mcpIdx]

            val tipY = tip.y()
            val mcpY = mcp.y()

            val tipX = tip.x()
            val mcpX = mcp.x()

            val distance = Math.sqrt(
                ((tipX - mcpX) * (tipX - mcpX) + (tipY - mcpY) * (tipY - mcpY)).toDouble()
            ).toFloat()

            if (distance < pressThreshold) {
                pressedFingers.add(i)
            }
        }

        return pressedFingers
    }

    fun getFingertipPositions(result: HandLandmarkerResult): List<Pair<Float, Float>> {
        val positions = mutableListOf<Pair<Float, Float>>()
        if (result.landmarks().isEmpty()) return positions

        val landmarks = result.landmarks()[0]
        for (idx in fingertipIndices) {
            if (idx < landmarks.size) {
                val lm = landmarks[idx]
                positions.add(Pair(lm.x(), lm.y()))
            }
        }
        return positions
    }

    fun setThreshold(threshold: Float) {
        pressThreshold = threshold
    }

    fun getThreshold(): Float = pressThreshold

    companion object {
        const val DEFAULT_PRESS_THRESHOLD = 0.07f

        const val THUMB_TIP = 4
        const val INDEX_FINGER_TIP = 8
        const val MIDDLE_FINGER_TIP = 12
        const val RING_FINGER_TIP = 16
        const val LITTLE_FINGER_TIP = 20

        const val THUMB_MCP = 2
        const val INDEX_FINGER_MCP = 5
        const val MIDDLE_FINGER_MCP = 9
        const val RING_FINGER_MCP = 13
        const val LITTLE_FINGER_MCP = 17
    }
}
