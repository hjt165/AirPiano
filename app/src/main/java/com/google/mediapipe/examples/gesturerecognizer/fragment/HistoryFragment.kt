package com.google.mediapipe.examples.gesturerecognizer.fragment

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.gesturerecognizer.R
import com.google.mediapipe.examples.gesturerecognizer.adapter.RecordingAdapter
import com.google.mediapipe.examples.gesturerecognizer.model.NoteEvent
import com.google.mediapipe.examples.gesturerecognizer.model.Recording
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import kotlin.math.exp
import kotlin.random.Random

class HistoryFragment : Fragment() {

    companion object {
        private const val PREFS_NAME = "AirPianoPrefs"
        private const val KEY_RECORDINGS = "recordings"
    }

    private lateinit var rvRecordings: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: RecordingAdapter
    private lateinit var prefs: SharedPreferences

    private var soundPool: SoundPool? = null
    private val noteSoundIds = mutableMapOf<String, Int>()
    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        rvRecordings = view.findViewById(R.id.rv_recordings)
        tvEmpty = view.findViewById(R.id.tv_empty)

        rvRecordings.layoutManager = LinearLayoutManager(requireContext())

        loadRecordings()

        // Initialize SoundPool for playback
        initSoundPool()
    }

    private fun loadRecordings() {
        val json = prefs.getString(KEY_RECORDINGS, "[]")
        val type = object : TypeToken<MutableList<Recording>>() {}.type
        val recordings: MutableList<Recording> = Gson().fromJson(json, type) ?: mutableListOf()

        if (recordings.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rvRecordings.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rvRecordings.visibility = View.VISIBLE

            adapter = RecordingAdapter(
                recordings,
                onPlayClick = { recording -> playRecording(recording) },
                onDeleteClick = { recording, position -> deleteRecording(recording, position) }
            )
            rvRecordings.adapter = adapter
        }
    }

    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(audioAttributes)
            .build()

        // Load sounds
        for ((noteName, freq) in noteFrequencies) {
            val wavFile = File(requireContext().cacheDir, "piano_notes_v2/note_${noteName}.wav")
            if (wavFile.exists()) {
                val soundId = soundPool?.load(wavFile.absolutePath, 1)
                if (soundId != null) {
                    noteSoundIds[noteName] = soundId
                }
            }
        }
    }

    private fun playRecording(recording: Recording) {
        if (isPlaying) {
            Toast.makeText(requireContext(), "正在播放中...", Toast.LENGTH_SHORT).show()
            return
        }

        if (recording.noteEvents.isEmpty()) {
            Toast.makeText(requireContext(), "录音为空", Toast.LENGTH_SHORT).show()
            return
        }

        isPlaying = true
        Toast.makeText(requireContext(), "开始播放: ${recording.name}", Toast.LENGTH_SHORT).show()

        // Play notes with original timing
        for (event in recording.noteEvents) {
            handler.postDelayed({
                if (event.action == "play") {
                    val soundId = noteSoundIds[event.noteName]
                    if (soundId != null) {
                        soundPool?.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
                    }
                }
            }, event.timestampMs)
        }

        // Reset playing state after recording duration
        val duration = if (recording.noteEvents.isNotEmpty()) recording.noteEvents.last().timestampMs + 500 else 0L
        handler.postDelayed({
            isPlaying = false
            Toast.makeText(requireContext(), "播放完成", Toast.LENGTH_SHORT).show()
        }, duration)
    }

    private fun deleteRecording(recording: Recording, position: Int) {
        val json = prefs.getString(KEY_RECORDINGS, "[]")
        val type = object : TypeToken<MutableList<Recording>>() {}.type
        val recordings: MutableList<Recording> = Gson().fromJson(json, type) ?: mutableListOf()

        recordings.removeAll { it.id == recording.id }
        prefs.edit().putString(KEY_RECORDINGS, Gson().toJson(recordings)).apply()

        adapter.removeAt(position)

        if (recordings.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rvRecordings.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        soundPool?.release()
        soundPool = null
    }
}
