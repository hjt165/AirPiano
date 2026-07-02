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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

    private var recordings: MutableList<Recording> = mutableListOf()

    // Playback state
    private var playingPosition = -1
    private var isPaused = false
    private var currentEventIndex = 0
    private var pauseOffsetMs = 0L
    private var playbackStartTimeMs = 0L
    private var currentRecording: Recording? = null

    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            if (playingPosition < 0 || currentRecording == null) return
            val elapsed = System.currentTimeMillis() - playbackStartTimeMs + pauseOffsetMs
            val duration = currentRecording!!.duration.toInt().coerceAtLeast(1)
            adapter.updateProgress(playingPosition, elapsed.toInt().coerceAtMost(duration))
            handler.postDelayed(this, 100)
        }
    }

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
        initSoundPool()
    }

    private fun loadRecordings() {
        val json = prefs.getString(KEY_RECORDINGS, "[]")
        val type = object : TypeToken<MutableList<Recording>>() {}.type
        recordings = Gson().fromJson(json, type) ?: mutableListOf()

        if (recordings.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rvRecordings.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rvRecordings.visibility = View.VISIBLE

            adapter = RecordingAdapter(
                recordings,
                onPlayPauseClick = { recording, position -> togglePlayPause(recording, position) },
                onStopClick = { _, position -> stopPlayback(position) },
                onDeleteClick = { recording, position -> deleteRecording(recording, position) },
                onRename = { recording, position, newName -> renameRecording(recording, position, newName) },
                onSeekTo = { recording, position, seekMs -> seekTo(recording, position, seekMs) }
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
            .setMaxStreams(8)
            .setAudioAttributes(audioAttributes)
            .build()

        val instrumentDir = File(requireContext().cacheDir, "piano_notes_v2/钢琴")
        for ((noteName, _) in noteFrequencies) {
            val wavFile = File(instrumentDir, "note_${noteName}.wav")
            if (wavFile.exists()) {
                val soundId = soundPool?.load(wavFile.absolutePath, 1)
                if (soundId != null) {
                    noteSoundIds[noteName] = soundId
                }
            }
        }
    }

    private fun togglePlayPause(recording: Recording, position: Int) {
        if (playingPosition == position) {
            if (isPaused) {
                resumePlayback()
            } else {
                pausePlayback()
            }
        } else {
            startPlayback(recording, position)
        }
    }

    private fun startPlayback(recording: Recording, position: Int) {
        if (recording.noteEvents.isEmpty()) {
            Toast.makeText(requireContext(), "录音为空", Toast.LENGTH_SHORT).show()
            return
        }

        stopAllPlayback()

        playingPosition = position
        currentRecording = recording
        isPaused = false
        currentEventIndex = 0
        pauseOffsetMs = 0L
        playbackStartTimeMs = System.currentTimeMillis()

        adapter.setPlayingState(position, false)
        handler.post(progressUpdateRunnable)
        scheduleNextEvent()
    }

    private fun scheduleNextEvent() {
        val recording = currentRecording ?: return
        val events = recording.noteEvents

        while (currentEventIndex < events.size) {
            val event = events[currentEventIndex]
            val delayMs = event.timestampMs - pauseOffsetMs - (System.currentTimeMillis() - playbackStartTimeMs)

            if (delayMs <= 0) {
                playEvent(event)
                currentEventIndex++
            } else {
                handler.postDelayed({
                    if (playingPosition >= 0 && !isPaused) {
                        playEvent(event)
                        currentEventIndex++
                        scheduleNextEvent()
                    }
                }, delayMs)
                return
            }
        }

        handler.postDelayed({
            stopAllPlayback()
            Toast.makeText(requireContext(), "播放完成", Toast.LENGTH_SHORT).show()
        }, 300)
    }

    private fun playEvent(event: NoteEvent) {
        if (event.action == "play") {
            val soundId = noteSoundIds[event.noteName]
            if (soundId != null) {
                soundPool?.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
            }
        }
    }

    private fun pausePlayback() {
        isPaused = true
        pauseOffsetMs = System.currentTimeMillis() - playbackStartTimeMs + pauseOffsetMs
        handler.removeCallbacksAndMessages(null)
        adapter.setPlayingState(playingPosition, true)
    }

    private fun resumePlayback() {
        isPaused = false
        playbackStartTimeMs = System.currentTimeMillis()
        adapter.setPlayingState(playingPosition, false)
        handler.post(progressUpdateRunnable)
        scheduleNextEvent()
    }

    private fun stopPlayback(position: Int) {
        if (playingPosition == position) {
            stopAllPlayback()
        }
    }

    private fun stopAllPlayback() {
        handler.removeCallbacksAndMessages(null)
        val oldPosition = playingPosition
        playingPosition = -1
        isPaused = false
        currentEventIndex = 0
        pauseOffsetMs = 0L
        currentRecording = null
        if (oldPosition >= 0 && ::adapter.isInitialized) {
            adapter.stopPlayback()
        }
    }

    private fun seekTo(recording: Recording, position: Int, seekMs: Int) {
        if (playingPosition != position) return

        handler.removeCallbacksAndMessages(null)

        pauseOffsetMs = seekMs.toLong()
        playbackStartTimeMs = System.currentTimeMillis()
        currentEventIndex = 0

        val events = recording.noteEvents
        while (currentEventIndex < events.size && events[currentEventIndex].timestampMs <= seekMs) {
            currentEventIndex++
        }

        if (!isPaused) {
            handler.post(progressUpdateRunnable)
            scheduleNextEvent()
        }
    }

    private fun renameRecording(recording: Recording, position: Int, newName: String) {
        recordings[position] = recording.copy(name = newName)
        saveRecordings()
        adapter.notifyItemChanged(position)
    }

    private fun saveRecordings() {
        prefs.edit().putString(KEY_RECORDINGS, Gson().toJson(recordings)).apply()
    }

    private fun deleteRecording(recording: Recording, position: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("确认删除")
            .setMessage("确定要删除「${recording.name}」吗？")
            .setPositiveButton("删除") { _, _ ->
                performDelete(recording, position)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun performDelete(recording: Recording, position: Int) {
        if (playingPosition == position) {
            stopAllPlayback()
        }

        val json = prefs.getString(KEY_RECORDINGS, "[]")
        val type = object : TypeToken<MutableList<Recording>>() {}.type
        val allRecordings: MutableList<Recording> = Gson().fromJson(json, type) ?: mutableListOf()

        allRecordings.removeAll { it.id == recording.id }
        prefs.edit().putString(KEY_RECORDINGS, Gson().toJson(allRecordings)).apply()

        recordings.removeAt(position)
        adapter.notifyItemRemoved(position)
        adapter.notifyItemRangeChanged(position, recordings.size)

        if (recordings.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rvRecordings.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        if (this::rvRecordings.isInitialized && playingPosition < 0) {
            loadRecordings()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        soundPool?.release()
        soundPool = null
    }
}
