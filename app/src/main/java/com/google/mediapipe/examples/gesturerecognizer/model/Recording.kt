package com.google.mediapipe.examples.gesturerecognizer.model

data class Recording(
    val id: String = System.currentTimeMillis().toString(),
    val name: String = "录音 ${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}",
    val timestamp: Long = System.currentTimeMillis(),
    val duration: Long = 0L,
    val noteEvents: List<NoteEvent> = emptyList()
)

data class NoteEvent(
    val noteName: String,
    val timestampMs: Long,
    val action: String = "play"  // "play" or "stop"
)
