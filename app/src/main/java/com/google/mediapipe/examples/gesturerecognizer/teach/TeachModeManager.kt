package com.google.mediapipe.examples.gesturerecognizer.teach

import android.util.Log
import com.google.mediapipe.examples.gesturerecognizer.model.Song

class TeachModeManager {

    companion object {
        private const val TAG = "TeachModeManager"
    }

    interface Callback {
        fun onShowHint(noteName: String, index: Int, total: Int)
        fun onHighlightNote(noteName: String)
        fun onNoteCompleted(index: Int, note: String, score: Double)
        fun onSongCompleted(score: Float, rating: String, perfect: Int, skip: Int)
        fun onWrongNote()
    }

    var callback: Callback? = null

    private var currentSong: Song? = null
    private var currentNoteIndex = 0
    private var isPlaying = false
    private var isPaused = false

    // Scoring
    private var totalScore = 0.0
    private var perfectCount = 0
    private var skipCount = 0

    fun setSong(song: Song) {
        currentSong = song
        reset()
    }

    fun start() {
        val song = currentSong ?: return
        if (song.notes.isEmpty()) return

        reset()
        isPlaying = true
        isPaused = false
        showCurrentHint()
        Log.d(TAG, "Song started: ${song.name}, ${song.notes.size} notes")
    }

    fun pause() {
        if (!isPlaying || isPaused) return
        isPaused = true
        Log.d(TAG, "Paused at note $currentNoteIndex")
    }

    fun resume() {
        if (!isPlaying || !isPaused) return
        isPaused = false
        showCurrentHint()
        Log.d(TAG, "Resumed from note $currentNoteIndex")
    }

    fun stop() {
        isPlaying = false
        isPaused = false
    }

    fun reset() {
        stop()
        currentNoteIndex = 0
        totalScore = 0.0
        perfectCount = 0
        skipCount = 0
    }

    fun isPlaying(): Boolean = isPlaying
    fun isPaused(): Boolean = isPaused
    fun getCurrentSong(): Song? = currentSong
    fun getCurrentNoteIndex(): Int = currentNoteIndex

    fun getCurrentHighlightNote(): String? {
        val song = currentSong ?: return null
        if (currentNoteIndex >= song.notes.size) return null
        return song.notes[currentNoteIndex].note
    }

    fun getHighlightedKeys(): List<String> {
        val song = currentSong ?: return emptyList()
        if (!isPlaying || isPaused) return emptyList()
        if (currentNoteIndex >= song.notes.size) return emptyList()
        return listOf(song.notes[currentNoteIndex].note)
    }

    fun onNotePressed(noteName: String) {
        if (!isPlaying || isPaused) return
        val song = currentSong ?: return
        if (currentNoteIndex >= song.notes.size) return

        val expectedNote = song.notes[currentNoteIndex].note
        if (noteName == expectedNote) {
            // Correct!
            scoreNote(currentNoteIndex, 1.0)
            advanceToNext()
        } else {
            // Wrong note
            callback?.onWrongNote()
        }
    }

    fun onNoteReleased(noteName: String) {
        // No-op in press-to-advance mode
    }

    fun skipCurrentNote() {
        if (!isPlaying || isPaused) return
        val song = currentSong ?: return
        if (currentNoteIndex >= song.notes.size) return

        scoreNote(currentNoteIndex, 0.0)
        skipCount++
        advanceToNext()
    }

    private fun advanceToNext() {
        val song = currentSong ?: return
        currentNoteIndex++

        if (currentNoteIndex >= song.notes.size) {
            finishSong()
        } else {
            showCurrentHint()
        }
    }

    private fun showCurrentHint() {
        val song = currentSong ?: return
        if (currentNoteIndex >= song.notes.size) return

        val note = song.notes[currentNoteIndex]
        callback?.onShowHint(note.note, currentNoteIndex, song.notes.size)
        callback?.onHighlightNote(note.note)
    }

    private fun scoreNote(index: Int, score: Double) {
        val song = currentSong ?: return
        if (index < 0 || index >= song.notes.size) return

        val note = song.notes[index]
        totalScore += score
        if (score >= 1.0) {
            perfectCount++
        }
        callback?.onNoteCompleted(index, note.note, score)
    }

    private fun finishSong() {
        isPlaying = false

        val song = currentSong ?: return
        val totalNotes = song.notes.size
        val percentage = if (totalNotes > 0) (totalScore / totalNotes * 100).toInt().coerceIn(0, 100) else 0

        val rating = when {
            percentage >= 90 -> "优秀"
            percentage >= 75 -> "良好"
            percentage >= 60 -> "及格"
            else -> "不及格"
        }

        callback?.onSongCompleted(percentage.toFloat(), rating, perfectCount, skipCount)
        Log.d(TAG, "Song completed: $percentage% ($rating), perfect=$perfectCount skip=$skipCount")
    }
}
