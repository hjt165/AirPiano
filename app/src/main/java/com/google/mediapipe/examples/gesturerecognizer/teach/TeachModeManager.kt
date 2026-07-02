package com.google.mediapipe.examples.gesturerecognizer.teach

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.mediapipe.examples.gesturerecognizer.model.Song
import com.google.mediapipe.examples.gesturerecognizer.model.SongNote

class TeachModeManager {

    companion object {
        private const val TAG = "TeachModeManager"
        private const val TOLERANCE_MS = 200L
        private const val EARLY_PENALTY_MS = 300L
    }

    interface Callback {
        fun onProgressChanged(currentNoteIndex: Int, totalNotes: Int, currentMs: Long)
        fun onHighlightNote(note: String, startMs: Long, durationMs: Long)
        fun onNoteCompleted(index: Int, note: String, score: Double)
        fun onSongCompleted(score: Float, rating: String, perfect: Int, good: Int, miss: Int)
        fun onMetronomeTick(expectedNoteIndex: Int)
    }

    var callback: Callback? = null

    private val handler = Handler(Looper.getMainLooper())
    private var currentSong: Song? = null
    private var currentNoteIndex = 0
    private var isPlaying = false
    private var isPaused = false
    private var speedMultiplier = 1.0f
    private var songStartTimeMs = 0L
    private var pauseOffsetMs = 0L

    // Scoring
    private var totalScore = 0.0
    private var perfectCount = 0
    private var goodCount = 0
    private var missCount = 0
    private var noteHitStatus = BooleanArray(0)

    // User press tracking
    private val pressedNotes = mutableSetOf<String>()
    private val scoredNoteIndices = mutableSetOf<Int>()

    // Playback state
    private var playbackElapsedMs = 0L
    private val progressRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying || isPaused) return
            playbackElapsedMs = ((System.currentTimeMillis() - songStartTimeMs) * speedMultiplier).toLong() + pauseOffsetMs.toLong()
            updateNoteIndex()
            callback?.onProgressChanged(currentNoteIndex, getCurrentSong()?.notes?.size ?: 0, playbackElapsedMs)
            handler.postDelayed(this, 50)
        }
    }

    fun setSong(song: Song) {
        currentSong = song
        reset()
    }

    fun setSpeed(speed: Float) {
        speedMultiplier = speed.coerceIn(0.5f, 2.0f)
    }

    fun getSpeed(): Float = speedMultiplier

    fun start() {
        val song = currentSong ?: return
        if (song.notes.isEmpty()) return

        if (isPaused) {
            resume()
            return
        }

        reset()
        isPlaying = true
        isPaused = false
        songStartTimeMs = System.currentTimeMillis()
        playbackElapsedMs = 0
        pauseOffsetMs = 0

        handler.post(progressRunnable)
        scheduleNextHighlight()
        Log.d(TAG, "Song started: ${song.name}, ${song.notes.size} notes")
    }

    fun pause() {
        if (!isPlaying || isPaused) return
        isPaused = true
        pauseOffsetMs = playbackElapsedMs
        handler.removeCallbacks(progressRunnable)
        Log.d(TAG, "Paused at ${playbackElapsedMs}ms")
    }

    fun resume() {
        if (!isPlaying || !isPaused) return
        isPaused = false
        songStartTimeMs = System.currentTimeMillis()
        handler.post(progressRunnable)
        scheduleNextHighlight()
        Log.d(TAG, "Resumed from ${pauseOffsetMs}ms")
    }

    fun stop() {
        isPlaying = false
        isPaused = false
        handler.removeCallbacks(progressRunnable)
        handler.removeCallbacksAndMessages(null)
    }

    fun reset() {
        stop()
        currentNoteIndex = 0
        totalScore = 0.0
        perfectCount = 0
        goodCount = 0
        missCount = 0
        pressedNotes.clear()
        scoredNoteIndices.clear()
        playbackElapsedMs = 0
        pauseOffsetMs = 0
    }

    fun isPlaying(): Boolean = isPlaying
    fun isPaused(): Boolean = isPaused
    fun getCurrentSong(): Song? = currentSong

    fun getCurrentNoteIndex(): Int = currentNoteIndex
    fun getPlaybackMs(): Long = playbackElapsedMs

    fun onNotePressed(noteName: String) {
        if (!isPlaying || isPaused) return
        pressedNotes.add(noteName)
        checkUserInput()
    }

    fun onNoteReleased(noteName: String) {
        pressedNotes.remove(noteName)
    }

    fun getCurrentHighlightNote(): String? {
        val song = currentSong ?: return null
        if (currentNoteIndex >= song.notes.size) return null
        return song.notes[currentNoteIndex].note
    }

    fun getHighlightedKeys(): List<String> {
        val song = currentSong ?: return emptyList()
        if (!isPlaying || isPaused) return emptyList()

        val result = mutableListOf<String>()
        for (i in currentNoteIndex until minOf(currentNoteIndex + 2, song.notes.size)) {
            if (i !in scoredNoteIndices) {
                result.add(song.notes[i].note)
            }
        }
        return result
    }

    fun isNoteHighlighted(noteName: String): Boolean {
        val song = currentSong ?: return false
        if (!isPlaying || isPaused) return false
        for (i in currentNoteIndex until minOf(currentNoteIndex + 2, song.notes.size)) {
            if (i !in scoredNoteIndices && song.notes[i].note == noteName) {
                return true
            }
        }
        return false
    }

    private fun updateNoteIndex() {
        val song = currentSong ?: return
        while (currentNoteIndex < song.notes.size) {
            val note = song.notes[currentNoteIndex]
            val noteEndMs = note.startMs + note.durationMs
            if (playbackElapsedMs >= noteEndMs) {
                if (currentNoteIndex !in scoredNoteIndices) {
                    scoreNote(currentNoteIndex, null)
                }
                currentNoteIndex++
            } else {
                break
            }
        }

        if (currentNoteIndex >= song.notes.size) {
            finishSong()
        }
    }

    private fun checkUserInput() {
        val song = currentSong ?: return
        if (currentNoteIndex >= song.notes.size) return

        val note = song.notes[currentNoteIndex]
        if (currentNoteIndex in scoredNoteIndices) return

        if (pressedNotes.contains(note.note)) {
            val timeDiff = kotlin.math.abs(playbackElapsedMs - note.startMs)
            when {
                timeDiff <= TOLERANCE_MS -> scoreNote(currentNoteIndex, 1.0)
                playbackElapsedMs > note.startMs + TOLERANCE_MS -> scoreNote(currentNoteIndex, 0.5)
                else -> scoreNote(currentNoteIndex, 0.5)
            }
        }
    }

    private fun scoreNote(index: Int, userScore: Double?) {
        val song = currentSong ?: return
        if (index < 0 || index >= song.notes.size) return
        if (index in scoredNoteIndices) return

        scoredNoteIndices.add(index)
        val note = song.notes[index]

        val score: Double
        if (userScore != null) {
            score = userScore
            if (score >= 1.0) {
                perfectCount++
            } else {
                goodCount++
            }
        } else {
            score = 0.0
            missCount++
        }

        totalScore += score
        callback?.onNoteCompleted(index, note.note, score)
    }

    private fun finishSong() {
        isPlaying = false
        handler.removeCallbacks(progressRunnable)

        val song = currentSong ?: return
        val totalNotes = song.notes.size
        val maxScore = totalNotes.toDouble()
        val percentage = if (maxScore > 0) (totalScore / maxScore * 100).toInt().coerceIn(0, 100) else 0

        val rating = when {
            percentage >= 90 -> "优秀"
            percentage >= 75 -> "良好"
            percentage >= 60 -> "及格"
            else -> "不及格"
        }

        callback?.onSongCompleted(percentage.toFloat(), rating, perfectCount, goodCount, missCount)
        Log.d(TAG, "Song completed: $percentage% ($rating), P=$perfectCount G=$goodCount M=$missCount")
    }

    private fun scheduleNextHighlight() {
        val song = currentSong ?: return
        if (currentNoteIndex >= song.notes.size) return

        val note = song.notes[currentNoteIndex]
        val noteStartMs = note.startMs
        val delayMs = ((noteStartMs - playbackElapsedMs) / speedMultiplier).toLong().coerceAtLeast(0)

        handler.postDelayed({
            if (isPlaying && !isPaused) {
                callback?.onHighlightNote(note.note, note.startMs, note.durationMs)
                callback?.onMetronomeTick(currentNoteIndex)
            }
        }, delayMs)
    }
}
