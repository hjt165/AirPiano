package com.google.mediapipe.examples.gesturerecognizer

class PianoKeyMapper(
    private val numWhiteKeys: Int = 14,
    private val startOctave: Int = 4
) {

    private val whiteKeyNotes = listOf("C", "D", "E", "F", "G", "A", "B")

    fun mapFingerToKey(
        fingerX: Float,
        fingerY: Float,
        pressedFingers: Set<Int>,
        isLeftHand: Boolean = false
    ): Pair<Int, String> {
        if (fingerX < 0f || fingerX > 1f) return Pair(-1, "")

        val keyIndex = (fingerX * numWhiteKeys).toInt().coerceIn(0, numWhiteKeys - 1)
        val noteName = getNoteName(keyIndex)

        return Pair(keyIndex, noteName)
    }

    fun mapAllFingers(
        fingertipPositions: List<Pair<Float, Float>>,
        pressedFingers: Set<Int>
    ): List<Pair<Int, String>> {
        val results = mutableListOf<Pair<Int, String>>()

        for (i in fingertipPositions.indices) {
            if (i in pressedFingers) {
                val (keyIndex, noteName) = mapFingerToKey(
                    fingertipPositions[i].first,
                    fingertipPositions[i].second,
                    pressedFingers
                )
                results.add(Pair(keyIndex, noteName))
            } else {
                results.add(Pair(-1, ""))
            }
        }

        return results
    }

    private fun getNoteName(keyIndex: Int): String {
        val noteIndex = keyIndex % 7
        val octave = startOctave + keyIndex / 7
        return "${whiteKeyNotes[noteIndex]}$octave"
    }

    fun getNoteWithOctave(keyIndex: Int): String = getNoteName(keyIndex)

    fun getKeyIndexForNote(noteName: String): Int {
        for (i in 0 until numWhiteKeys) {
            if (getNoteName(i) == noteName) return i
        }
        return -1
    }
}
