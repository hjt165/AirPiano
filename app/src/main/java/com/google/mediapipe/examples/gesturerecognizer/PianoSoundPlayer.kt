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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.min
import kotlin.math.max
import kotlin.random.Random

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
        val cacheDir = File(context.cacheDir, "piano_notes_v2")
        cacheDir.mkdirs()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(12)
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
        val sampleRate = 44100
        // Duration varies by frequency: lower notes sustain longer
        val duration = 3.0 - (frequency - 261.0) / 200.0  // ~3s for C4, ~1.5s for B5
        val durationCapped = duration.coerceIn(1.5, 3.0)
        val numSamples = (sampleRate * durationCapped).toInt()
        val amplitude = 14000.0

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

        // Frequency-dependent brightness: lower notes = fewer high harmonics
        val brightness = (frequency / 1000.0).coerceIn(0.5, 1.0)

        val data = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate

            // === Multi-stage piano envelope ===
            val envelope = when {
                t < 0.002 -> t / 0.002  // Hammer attack: 2ms
                t < 0.01 -> 1.0  // Brief peak hold
                t < 0.08 -> 1.0 - 0.25 * ((t - 0.01) / 0.07)  // Quick initial decay
                t < 0.4 -> 0.75 - 0.25 * ((t - 0.08) / 0.32)  // Mid decay
                t < durationCapped -> 0.5 * exp(-(t - 0.4) * (1.5 + frequency / 300.0))  // Exponential tail
                else -> 0.0
            }

            // === Time-varying harmonics (high harmonics decay faster) ===
            val h1Decay = exp(-t * 1.2)       // Fundamental: slowest decay
            val h2Decay = exp(-t * 1.8)       // 2nd harmonic
            val h3Decay = exp(-t * 2.8)       // 3rd harmonic
            val h4Decay = exp(-t * 4.0)       // 4th harmonic
            val h5Decay = exp(-t * 5.5)       // 5th harmonic
            val h6Decay = exp(-t * 7.5)       // 6th harmonic

            // Inharmonicity: slight sharpness on upper partials (real piano strings)
            val inharmonicShift = 1.003  // 0.3% sharp

            val wave = sin(2.0 * Math.PI * frequency * t) * 1.0 * h1Decay +
                    sin(2.0 * Math.PI * frequency * 2 * inharmonicShift * t) * 0.4 * h2Decay * brightness +
                    sin(2.0 * Math.PI * frequency * 3 * inharmonicShift * t) * 0.2 * h3Decay * brightness +
                    sin(2.0 * Math.PI * frequency * 4 * inharmonicShift * t) * 0.1 * h4Decay * brightness * brightness +
                    sin(2.0 * Math.PI * frequency * 5 * inharmonicShift * t) * 0.05 * h5Decay * brightness * brightness +
                    sin(2.0 * Math.PI * frequency * 6 * inharmonicShift * t) * 0.025 * h6Decay * brightness * brightness

            // === Hammer strike noise (5ms burst at attack) ===
            val hammerNoise = if (t < 0.005) {
                (Random.nextFloat() * 2.0 - 1.0) * 0.3 * (1.0 - t / 0.005)
            } else 0.0

            val value = ((wave + hammerNoise) * amplitude * envelope / 1.775).toInt()
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
