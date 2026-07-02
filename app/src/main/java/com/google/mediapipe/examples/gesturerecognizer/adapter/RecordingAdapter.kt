package com.google.mediapipe.examples.gesturerecognizer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.gesturerecognizer.R
import com.google.mediapipe.examples.gesturerecognizer.model.Recording

class RecordingAdapter(
    private val recordings: MutableList<Recording>,
    private val onPlayClick: (Recording) -> Unit,
    private val onDeleteClick: (Recording, Int) -> Unit
) : RecyclerView.Adapter<RecordingAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_recording_name)
        val tvInfo: TextView = view.findViewById(R.id.tv_recording_info)
        val btnPlay: Button = view.findViewById(R.id.btn_play)
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

        holder.btnPlay.setOnClickListener {
            onPlayClick(recording)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClick(recording, holder.adapterPosition)
        }
    }

    override fun getItemCount(): Int = recordings.size

    fun removeAt(position: Int) {
        recordings.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, recordings.size)
    }
}
