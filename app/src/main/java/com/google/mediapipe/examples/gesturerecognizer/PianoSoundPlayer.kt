package com.google.mediapipe.examples.gesturerecognizer

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

class PianoSoundPlayer(private val context: Context) {

    private var soundPool: SoundPool? = null
    private val noteSoundIds = mutableMapOf<String, Int>()
    private val activeNotes = mutableSetOf<String>()

    private var isLoaded = false

    fun initialize() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(audioAttributes)
            .build()

        loadAllNotes()
    }

    private fun loadAllNotes() {
        val whiteKeyNotes = listOf("C", "D", "E", "F", "G", "A", "B")
        var loadCount = 0
        val totalToLoad = 88

        soundPool?.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) {
                loadCount++
                if (loadCount >= totalToLoad) {
                    isLoaded = true
                    Log.d(TAG, "All $totalToLoad note sounds loaded")
                }
            }
        }

        for (octave in 0..7) {
            for (note in whiteKeyNotes) {
                val noteName = "$note$octave"
                val resId = context.resources.getIdentifier(
                    "note_${note.lowercase()}${octave}",
                    "raw",
                    context.packageName
                )
                if (resId != 0) {
                    val soundId = soundPool?.load(context, resId, 1)
                    if (soundId != null) {
                        noteSoundIds[noteName] = soundId
                    }
                } else {
                    Log.w(TAG, "Sound resource not found: $noteName")
                }
            }
        }
    }

    fun playNote(noteName: String) {
        if (!isLoaded) return
        if (activeNotes.contains(noteName)) return

        val soundId = noteSoundIds[noteName]
        if (soundId != null) {
            soundPool?.play(
                soundId,
                1.0f,
                1.0f,
                1,
                0,
                1.0f
            )
            activeNotes.add(noteName)
            Log.d(TAG, "Playing note: $noteName")
        }
    }

    fun stopNote(noteName: String) {
        activeNotes.remove(noteName)
    }

    fun stopAllNotes() {
        activeNotes.clear()
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        noteSoundIds.clear()
        activeNotes.clear()
        isLoaded = false
    }

    fun isReady(): Boolean = isLoaded

    companion object {
        private const val TAG = "PianoSoundPlayer"
        private const val MAX_STREAMS = 5
    }
}
