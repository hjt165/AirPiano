package com.google.mediapipe.examples.gesturerecognizer.adapter

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.gesturerecognizer.R
import com.google.mediapipe.examples.gesturerecognizer.model.Recording

class RecordingAdapter(
    private val recordings: MutableList<Recording>,
    private val onPlayPauseClick: (Recording, Int) -> Unit,
    private val onStopClick: (Recording, Int) -> Unit,
    private val onDeleteClick: (Recording, Int) -> Unit,
    private val onRename: (Recording, Int, String) -> Unit,
    private val onSeekTo: (Recording, Int, Int) -> Unit
) : RecyclerView.Adapter<RecordingAdapter.ViewHolder>() {

    private var playingPosition = -1
    private var isPaused = false
    private var isUserDragging = false
    private var currentProgressMs = 0

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_recording_name)
        val tvInfo: TextView = view.findViewById(R.id.tv_recording_info)
        val seekBar: SeekBar = view.findViewById(R.id.seek_bar_progress)
        val btnPlayPause: Button = view.findViewById(R.id.btn_play_pause)
        val btnStop: Button = view.findViewById(R.id.btn_stop)
        val btnDelete: Button = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recording, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recording = recordings[position]
        holder.tvName.text = recording.name

        val durationSec = recording.duration / 1000.0
        val noteCount = recording.noteEvents.size
        holder.tvInfo.text = "${noteCount}个音符 | ${String.format("%.1f", durationSec)}秒"

        val isThisPlaying = position == playingPosition
        holder.seekBar.max = recording.duration.toInt().coerceAtLeast(1)

        if (isThisPlaying) {
            holder.btnPlayPause.text = if (isPaused) "继续" else "暂停"
            holder.btnStop.isEnabled = true
            holder.seekBar.progress = currentProgressMs
        } else {
            holder.btnPlayPause.text = "播放"
            holder.btnStop.isEnabled = false
            holder.seekBar.progress = 0
        }

        holder.btnPlayPause.setOnClickListener {
            onPlayPauseClick(recording, holder.adapterPosition)
        }

        holder.btnStop.setOnClickListener {
            onStopClick(recording, holder.adapterPosition)
        }

        holder.btnDelete.setOnClickListener {
            if (holder.adapterPosition == playingPosition) {
                onStopClick(recording, holder.adapterPosition)
            }
            onDeleteClick(recording, holder.adapterPosition)
        }

        holder.tvName.setOnClickListener {
            showRenameDialog(holder.tvName, recording, holder.adapterPosition)
        }

        holder.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && holder.adapterPosition == playingPosition) {
                    onSeekTo(recording, holder.adapterPosition, progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserDragging = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserDragging = false
            }
        })
    }

    private fun showRenameDialog(nameTextView: TextView, recording: Recording, position: Int) {
        val context = nameTextView.context
        val editText = EditText(context).apply {
            setText(recording.name)
            setSelection(recording.name.length)
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(context)
            .setTitle("重命名")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    onRename(recording, position, newName)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    fun setPlayingState(position: Int, paused: Boolean) {
        val oldPosition = playingPosition
        playingPosition = position
        isPaused = paused
        if (!paused) currentProgressMs = 0

        if (oldPosition >= 0 && oldPosition != position) {
            notifyItemChanged(oldPosition)
        }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }

    fun updateProgress(position: Int, progressMs: Int) {
        if (position == playingPosition && position >= 0 && position < itemCount && !isUserDragging) {
            currentProgressMs = progressMs
            notifyItemChanged(position, progressMs)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads[0] is Int) {
            holder.seekBar.progress = payloads[0] as Int
            return
        }
        onBindViewHolder(holder, position)
    }

    fun stopPlayback() {
        val oldPosition = playingPosition
        playingPosition = -1
        isPaused = false
        currentProgressMs = 0
        if (oldPosition >= 0) {
            notifyItemChanged(oldPosition)
        }
    }

    override fun getItemCount(): Int = recordings.size

    fun removeAt(position: Int) {
        recordings.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, recordings.size)
    }

    fun getRecordingName(position: Int): String {
        return recordings.getOrNull(position)?.name ?: ""
    }
}
