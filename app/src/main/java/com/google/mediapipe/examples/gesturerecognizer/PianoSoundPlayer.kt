package com.google.mediapipe.examples.gesturerecognizer

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.google.mediapipe.examples.gesturerecognizer.model.NoteEvent
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import kotlin.math.exp
import kotlin.math.abs
import kotlin.random.Random

class PianoSoundPlayer(private val context: Context) {

    private var soundPool: SoundPool? = null
    private val noteSoundIds = mutableMapOf<String, Int>()
    private val activeNotes = mutableSetOf<String>()
    @Volatile
    private var isLoaded = false

    // Recording state
    private var isRecording = false
    private val recordedEvents = mutableListOf<NoteEvent>()
    private var recordingStartTime = 0L

    // Instrument
    private var currentInstrument = "钢琴"

    private val baseFrequencies = mapOf(
        "C" to 261.63, "D" to 293.66, "E" to 329.63,
        "F" to 349.23, "G" to 392.00, "A" to 440.00, "B" to 493.88,
        "C#" to 277.18, "D#" to 311.13, "F#" to 369.99,
        "G#" to 415.30, "A#" to 466.16
    )

    private var noteFrequencies = mutableMapOf<String, Double>()
    private var currentOctave = 4
    private var cacheDir: File? = null

    fun initialize(octave: Int = 4) {
        currentOctave = octave
        cacheDir = File(context.cacheDir, "piano_notes_v2")
        cacheDir!!.mkdirs()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(audioAttributes)
            .build()

        loadOctave(currentOctave)
    }

    private fun loadOctave(octave: Int) {
        val whiteNoteNames = listOf("C", "D", "E", "F", "G", "A", "B")
        val blackNoteNames = listOf("C#", "D#", "F#", "G#", "A#")
        val notes = mutableListOf<Pair<String, Double>>()

        for (oct in 0..1) {
            val octNum = octave + oct
            for (name in whiteNoteNames + blackNoteNames) {
                val baseFreq = baseFrequencies[name] ?: continue
                val noteName = "$name$octNum"
                val freq = baseFreq * Math.pow(2.0, (octNum - 4).toDouble())
                notes.add(noteName to freq)
                noteFrequencies[noteName] = freq
            }
        }

        var loadCount = 0
        soundPool?.setOnLoadCompleteListener { _, _, _ ->
            loadCount++
            if (loadCount >= notes.size) {
                isLoaded = true
                Log.d(TAG, "Octave $octave+${octave + 1} loaded (${notes.size} notes)")
            }
        }

        val subDir = File(cacheDir, currentInstrument)
        subDir.mkdirs()

        for ((noteName, freq) in notes) {
            val wavFile = File(subDir, "note_${noteName}.wav")
            if (!wavFile.exists()) {
                generateWav(wavFile, freq, currentInstrument)
            }
            val soundId = soundPool?.load(wavFile.absolutePath, 1)
            if (soundId != null) {
                noteSoundIds[noteName] = soundId
            }
        }
    }

    fun setOctave(octave: Int) {
        if (octave == currentOctave) return
        currentOctave = octave
        noteSoundIds.clear()
        isLoaded = false
        loadOctave(currentOctave)
        Log.d(TAG, "Switched octave to: $octave")
    }

    fun setInstrument(name: String) {
        if (name == currentInstrument) return
        currentInstrument = name
        // Reload current octave with new instrument
        noteSoundIds.clear()
        isLoaded = false
        loadOctave(currentOctave)
        Log.d(TAG, "Switched instrument to: $name")
    }

    fun getInstrument(): String = currentInstrument

    // ==================== WAV Generation ====================

    private fun generateWav(file: File, frequency: Double, instrument: String) {
        when (instrument) {
            "吉他" -> generateGuitarWav(file, frequency)
            "合成器" -> generateSynthWav(file, frequency)
            else -> generatePianoWav(file, frequency)
        }
    }

    private fun generatePianoWav(file: File, frequency: Double) {
        val sampleRate = 44100
        val duration = (3.0 - (frequency - 261.0) / 200.0).coerceIn(1.5, 3.0)
        val numSamples = (sampleRate * duration).toInt()
        val amplitude = 14000.0
        val brightness = (frequency / 1000.0).coerceIn(0.5, 1.0)

        val (header, data) = createWavBuffers(sampleRate, numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate

            val envelope = when {
                t < 0.002 -> t / 0.002
                t < 0.01 -> 1.0
                t < 0.08 -> 1.0 - 0.25 * ((t - 0.01) / 0.07)
                t < 0.4 -> 0.75 - 0.25 * ((t - 0.08) / 0.32)
                t < duration -> 0.5 * exp(-(t - 0.4) * (1.5 + frequency / 300.0))
                else -> 0.0
            }

            val h1 = exp(-t * 1.2)
            val h2 = exp(-t * 1.8)
            val h3 = exp(-t * 2.8)
            val h4 = exp(-t * 4.0)
            val h5 = exp(-t * 5.5)
            val h6 = exp(-t * 7.5)
            val inh = 1.003

            val wave = sin(2.0 * Math.PI * frequency * t) * 1.0 * h1 +
                    sin(2.0 * Math.PI * frequency * 2 * inh * t) * 0.4 * h2 * brightness +
                    sin(2.0 * Math.PI * frequency * 3 * inh * t) * 0.2 * h3 * brightness +
                    sin(2.0 * Math.PI * frequency * 4 * inh * t) * 0.1 * h4 * brightness * brightness +
                    sin(2.0 * Math.PI * frequency * 5 * inh * t) * 0.05 * h5 * brightness * brightness +
                    sin(2.0 * Math.PI * frequency * 6 * inh * t) * 0.025 * h6 * brightness * brightness

            val hammerNoise = if (t < 0.005) {
                (Random.nextFloat() * 2.0 - 1.0) * 0.3 * (1.0 - t / 0.005)
            } else 0.0

            val value = ((wave + hammerNoise) * amplitude * envelope / 1.775).toInt()
            data.putShort(value.coerceIn(-32768, 32767).toShort())
        }

        writeWavFile(file, header, data)
    }

    private fun generateGuitarWav(file: File, frequency: Double) {
        val sampleRate = 44100
        val duration = (4.0 - (frequency - 261.0) / 300.0).coerceIn(2.0, 4.0)
        val numSamples = (sampleRate * duration).toInt()
        val amplitude = 13000.0
        val brightness = (frequency / 800.0).coerceIn(0.6, 1.0)

        val (header, data) = createWavBuffers(sampleRate, numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate

            // Guitar: sharper attack, longer sustain, pluck noise
            val envelope = when {
                t < 0.001 -> t / 0.001  // Very fast pluck
                t < 0.005 -> 1.0
                t < 0.1 -> 1.0 - 0.15 * ((t - 0.005) / 0.095)
                t < 1.0 -> 0.85 - 0.20 * ((t - 0.1) / 0.9)
                t < duration -> 0.65 * exp(-(t - 1.0) * (0.8 + frequency / 500.0))
                else -> 0.0
            }

            // Guitar has stronger 2nd and 3rd harmonics
            val h1 = exp(-t * 0.8)
            val h2 = exp(-t * 1.2)
            val h3 = exp(-t * 1.6)
            val h4 = exp(-t * 2.5)
            val h5 = exp(-t * 3.5)

            val wave = sin(2.0 * Math.PI * frequency * t) * 1.0 * h1 +
                    sin(2.0 * Math.PI * frequency * 2 * t) * 0.55 * h2 * brightness +
                    sin(2.0 * Math.PI * frequency * 3 * t) * 0.35 * h3 * brightness +
                    sin(2.0 * Math.PI * frequency * 4 * t) * 0.12 * h4 * brightness * brightness +
                    sin(2.0 * Math.PI * frequency * 5 * t) * 0.04 * h5 * brightness * brightness

            // Pluck noise (1ms burst, sharper than piano hammer)
            val pluckNoise = if (t < 0.001) {
                (Random.nextFloat() * 2.0 - 1.0) * 0.5 * (1.0 - t / 0.001)
            } else 0.0

            val value = ((wave + pluckNoise) * amplitude * envelope / 2.0).toInt()
            data.putShort(value.coerceIn(-32768, 32767).toShort())
        }

        writeWavFile(file, header, data)
    }

    private fun generateSynthWav(file: File, frequency: Double) {
        val sampleRate = 44100
        val duration = 2.5
        val numSamples = (sampleRate * duration).toInt()
        val amplitude = 12000.0

        val (header, data) = createWavBuffers(sampleRate, numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate

            // Synth: pad-like envelope with slow attack
            val envelope = when {
                t < 0.05 -> t / 0.05  // 50ms attack
                t < 0.1 -> 1.0
                t < duration - 0.3 -> 0.9
                t < duration -> 0.9 * (1.0 - (t - (duration - 0.3)) / 0.3)
                else -> 0.0
            }

            // Sawtooth-like: all harmonics with linear decay
            val wave = sin(2.0 * Math.PI * frequency * t) * 0.5 +
                    sin(2.0 * Math.PI * frequency * 2 * t) * 0.3 +
                    sin(2.0 * Math.PI * frequency * 3 * t) * 0.2 +
                    sin(2.0 * Math.PI * frequency * 4 * t) * 0.15 +
                    sin(2.0 * Math.PI * frequency * 5 * t) * 0.1 +
                    sin(2.0 * Math.PI * frequency * 6 * t) * 0.06 +
                    sin(2.0 * Math.PI * frequency * 7 * t) * 0.03 +
                    sin(2.0 * Math.PI * frequency * 8 * t) * 0.015

            // Slight vibrato
            val vibrato = 1.0 + 0.003 * sin(2.0 * Math.PI * 5.0 * t)

            val value = (wave * amplitude * envelope * vibrato / 1.2).toInt()
            data.putShort(value.coerceIn(-32768, 32767).toShort())
        }

        writeWavFile(file, header, data)
    }

    private fun createWavBuffers(sampleRate: Int, numSamples: Int): Pair<ByteBuffer, ByteBuffer> {
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
        return header to data
    }

    private fun writeWavFile(file: File, header: ByteBuffer, data: ByteBuffer) {
        FileOutputStream(file).use { fos ->
            fos.write(header.array())
            fos.write(data.array())
        }
    }

    // ==================== Playback ====================

    fun playNote(noteName: String) {
        if (!isLoaded) return
        if (activeNotes.contains(noteName)) return

        val soundId = noteSoundIds[noteName] ?: return
        soundPool?.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
        activeNotes.add(noteName)
        recordNoteEvent(noteName, "play")
    }

    fun stopNote(noteName: String) {
        if (activeNotes.contains(noteName)) {
            activeNotes.remove(noteName)
            recordNoteEvent(noteName, "stop")
        }
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

    // ==================== Recording ====================

    fun startRecording() {
        recordedEvents.clear()
        recordingStartTime = System.currentTimeMillis()
        isRecording = true
        Log.d(TAG, "Recording started")
    }

    fun stopRecording(): List<NoteEvent> {
        isRecording = false
        Log.d(TAG, "Recording stopped, ${recordedEvents.size} events")
        return recordedEvents.toList()
    }

    fun isCurrentlyRecording(): Boolean = isRecording

    fun getRecordedEvents(): List<NoteEvent> = recordedEvents.toList()

    private fun recordNoteEvent(noteName: String, action: String) {
        if (!isRecording) return
        val event = NoteEvent(
            noteName = noteName,
            timestampMs = System.currentTimeMillis() - recordingStartTime,
            action = action
        )
        recordedEvents.add(event)
    }

    companion object {
        private const val TAG = "PianoSoundPlayer"
    }
}
