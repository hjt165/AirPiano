package com.google.mediapipe.examples.gesturerecognizer.teach

import android.content.Context
import com.google.mediapipe.examples.gesturerecognizer.model.Song
import com.google.mediapipe.examples.gesturerecognizer.model.SongNote
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SongLibrary(private val context: Context) {

    private val songFiles = listOf(
        "songs/xiaoxingxing.json",
        "songs/liangzhiLaohu.json",
        "songs/xiaomifeng.json",
        "songs/zhaopengyou.json",
        "songs/xinnianhao.json"
    )

    private var cachedSongs: List<Song>? = null

    fun loadAllSongs(): List<Song> {
        cachedSongs?.let { return it }

        val gson = Gson()
        val songs = mutableListOf<Song>()

        for (fileName in songFiles) {
            try {
                val json = context.assets.open(fileName).bufferedReader().use { it.readText() }
                val song = parseSong(json, gson)
                if (song != null) {
                    songs.add(song)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        cachedSongs = songs
        return songs
    }

    fun getSong(index: Int): Song? {
        val songs = loadAllSongs()
        return songs.getOrNull(index)
    }

    fun getSongCount(): Int = loadAllSongs().size

    private fun parseSong(json: String, gson: Gson): Song? {
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val map: Map<String, Any> = gson.fromJson(json, type)

            val name = map["name"] as? String ?: return null
            val artist = map["artist"] as? String ?: ""
            val difficulty = (map["difficulty"] as? Double)?.toInt() ?: 1

            @Suppress("UNCHECKED_CAST")
            val notesList = map["notes"] as? List<Map<String, Any>> ?: emptyList()
            val notes = notesList.map { noteMap ->
                SongNote(
                    note = noteMap["note"] as? String ?: "",
                    startMs = (noteMap["startMs"] as? Double)?.toLong() ?: 0L,
                    durationMs = (noteMap["durationMs"] as? Double)?.toLong() ?: 400L
                )
            }

            Song(name = name, artist = artist, difficulty = difficulty, notes = notes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
