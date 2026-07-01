package com.google.mediapipe.examples.gesturerecognizer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.SoundPool
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

class PianoSoundPlayer(private val context: Context) {

    private var soundPool: SoundPool? = null
    private val noteSoundIds = mutableMapOf<String, Int>()
    private val activeNotes = mutableSetOf<String>()
    private var isLoaded = false

    private val noteFrequencies = mapOf(
        "C4" to 261.63, "D4" to 293.66, "E4" to 329.63,
        "F4" to 349.23, "G4" to 392.00, "A4" to 440.00, "B4" to 493.88,
        "C5" to 523.25, "D5" to 587.33, "E5" to 659.25,
        "F5" to 698.46, "G5" to 783.99, "A5" to 880.00, "B5" to 987.77,
        "C#4" to 277.18, "D#4" to 311.13, "F#4" to 369.99,
        "G#4" to 415.30, "A#4" to 466.16,
        "C#5" to 554.37, "D#5" to 622.25, "F#5" to 739.99,
        "G#5" to 830.61, "A#5" to 932.33
    )

    fun initialize() {
        val cacheDir = File(context.cacheDir, "piano_notes")
        cacheDir.mkdirs()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(audioAttributes)
            .build()

        var loadCount = 0
        val totalNotes = noteFrequencies.size

        soundPool?.setOnLoadCompleteListener { _, _, status ->
            loadCount++
            if (loadCount >= totalNotes) {
                isLoaded = true
                Log.d(TAG, "All $totalNotes piano notes loaded")
            }
        }

        for ((noteName, freq) in noteFrequencies) {
            val wavFile = File(cacheDir, "note_${noteName}.wav")
            if (!wavFile.exists()) {
                generatePianoWav(wavFile, freq)
            }
            val soundId = soundPool?.load(wavFile.absolutePath, 1)
            if (soundId != null) {
                noteSoundIds[noteName] = soundId
            }
        }
    }

    private fun generatePianoWav(file: File, frequency: Double) {
        val sampleRate = 22050
        val duration = 1.5
        val numSamples = (sampleRate * duration).toInt()
        val amplitude = 12000.0

        val dataSize = numSamples * 2
        val fileSize = 36 + dataSize

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(fileSize)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(1)
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray())
        header.putInt(dataSize)

        val data = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate

            val envelope = when {
                t < 0.005 -> t / 0.005
                t < 0.1 -> 1.0 - 0.5 * ((t - 0.005) / 0.095)
                t < duration * 0.7 -> 0.5
                t < duration -> 0.5 * (1.0 - (t - duration * 0.7) / (duration * 0.3))
                else -> 0.0
            }

            val wave = sin(2.0 * Math.PI * frequency * t) * 1.0 +
                    sin(2.0 * Math.PI * frequency * 2 * t) * 0.3 +
                    sin(2.0 * Math.PI * frequency * 3 * t) * 0.15 +
                    sin(2.0 * Math.PI * frequency * 4 * t) * 0.08

            val value = (wave * amplitude * envelope / 1.53).toInt()
            data.putShort(value.coerceIn(-32768, 32767).toShort())
        }

        FileOutputStream(file).use { fos ->
            fos.write(header.array())
            fos.write(data.array())
        }
    }

    fun playNote(noteName: String) {
        if (!isLoaded) return
        if (activeNotes.contains(noteName)) return

        val soundId = noteSoundIds[noteName] ?: return
        soundPool?.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
        activeNotes.add(noteName)
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
    }
}
