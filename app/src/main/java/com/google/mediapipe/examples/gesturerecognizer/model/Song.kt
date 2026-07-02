package com.google.mediapipe.examples.gesturerecognizer.model

data class Song(
    val name: String,
    val artist: String,
    val difficulty: Int,
    val notes: List<SongNote>
) {
    val durationMs: Long
        get() = if (notes.isEmpty()) 0L else notes.maxOf { it.startMs + it.durationMs }
}
