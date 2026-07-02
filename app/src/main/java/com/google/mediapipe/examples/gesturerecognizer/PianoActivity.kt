package com.google.mediapipe.examples.gesturerecognizer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mediapipe.examples.gesturerecognizer.model.NoteEvent
import com.google.mediapipe.examples.gesturerecognizer.model.Recording
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PianoActivity : AppCompatActivity(), HandLandmarkerHelper.HandLandmarkerListener {

    companion object {
        private const val TAG = "PianoActivity"
        private const val PREFS_NAME = "AirPianoPrefs"
        private const val KEY_RECORDINGS = "recordings"
        private const val KEY_OCTAVE = "octave"
    }

    private lateinit var handLandmarkerHelper: HandLandmarkerHelper
    private lateinit var pianoSoundPlayer: PianoSoundPlayer
    private lateinit var overlay: OverlayView
    private lateinit var tvInfo: TextView
    private lateinit var btnRecord: Button

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT

    private lateinit var backgroundExecutor: ExecutorService
    private var lastActiveNotes: List<String> = emptyList()
    private var currentOctave = 3
    private var isRecording = false

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-screen immersive mode
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        setContentView(R.layout.activity_piano)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentOctave = prefs.getInt(KEY_OCTAVE, 3)

        overlay = findViewById(R.id.overlay)
        tvInfo = findViewById(R.id.tv_info)
        btnRecord = findViewById(R.id.btn_record)

        // Back button
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // Settings button
        findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            showSettingsDialog()
        }

        // Octave controls
        findViewById<Button>(R.id.btn_octave_down).setOnClickListener {
            currentOctave = (currentOctave - 1).coerceAtLeast(2)
            prefs.edit().putInt(KEY_OCTAVE, currentOctave).apply()
            updateInfoText()
        }

        findViewById<Button>(R.id.btn_octave_up).setOnClickListener {
            currentOctave = (currentOctave + 1).coerceAtMost(6)
            prefs.edit().putInt(KEY_OCTAVE, currentOctave).apply()
            updateInfoText()
        }

        // Record button
        btnRecord.setOnClickListener {
            toggleRecording()
        }

        // Instrument spinner
        val instruments = arrayOf("钢琴", "吉他", "合成器")
        val spinner = findViewById<Spinner>(R.id.spinner_instrument)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, instruments)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        backgroundExecutor = Executors.newSingleThreadExecutor()

        pianoSoundPlayer = PianoSoundPlayer(this)
        pianoSoundPlayer.initialize()

        backgroundExecutor.execute {
            handLandmarkerHelper = HandLandmarkerHelper(
                context = this,
                runningMode = RunningMode.LIVE_STREAM,
                handLandmarkerListener = this
            )
        }

        findViewById<androidx.camera.view.PreviewView>(R.id.view_finder).post {
            setUpCamera()
        }

        updateInfoText()
    }

    private fun toggleRecording() {
        if (isRecording) {
            // Stop recording
            val events = pianoSoundPlayer.stopRecording()
            isRecording = false
            btnRecord.text = "录制"
            btnRecord.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))

            // Save recording
            if (events.isNotEmpty()) {
                val duration = if (events.isNotEmpty()) events.last().timestampMs else 0L
                val recording = Recording(
                    name = "录音 ${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}",
                    duration = duration,
                    noteEvents = events
                )
                saveRecording(recording)
                Toast.makeText(this, "录音已保存 (${events.size}个音符)", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Start recording
            pianoSoundPlayer.startRecording()
            isRecording = true
            btnRecord.text = "停止"
            btnRecord.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            Toast.makeText(this, "开始录制...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveRecording(recording: Recording) {
        val json = prefs.getString(KEY_RECORDINGS, "[]")
        val type = object : TypeToken<MutableList<Recording>>() {}.type
        val recordings: MutableList<Recording> = Gson().fromJson(json, type) ?: mutableListOf()
        recordings.add(0, recording)
        // Keep only last 50 recordings
        if (recordings.size > 50) {
            recordings.subList(50, recordings.size).clear()
        }
        prefs.edit().putString(KEY_RECORDINGS, Gson().toJson(recordings)).apply()
    }

    private fun updateInfoText() {
        tvInfo.text = "八度: $currentOctave | 推理: ${handLandmarkerHelper::class.java.simpleName}"
    }

    private fun showSettingsDialog() {
        val dialog = SettingsDialogFragment()
        dialog.show(supportFragmentManager, SettingsDialogFragment.TAG)
    }

    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        val cameraSelector = CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(overlay.display.rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(overlay.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(backgroundExecutor) { imageProxy ->
                    recognizeHand(imageProxy)
                }
            }

        cameraProvider.unbindAll()
        try {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            preview?.setSurfaceProvider(findViewById<androidx.camera.view.PreviewView>(R.id.view_finder).surfaceProvider)
        } catch (e: Exception) {
            e(TAG, "Camera binding failed", e)
        }
    }

    private fun recognizeHand(imageProxy: ImageProxy) {
        try {
            handLandmarkerHelper.recognizeLiveStream(imageProxy)
        } finally {
            imageProxy.close()
        }
    }

    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
        runOnUiThread {
            val result = resultBundle.results
            overlay.setInferenceTime(resultBundle.inferenceTime)
            overlay.setResults(
                result,
                resultBundle.inputImageHeight,
                resultBundle.inputImageWidth,
                RunningMode.LIVE_STREAM,
                frontCamera = (cameraFacing == CameraSelector.LENS_FACING_FRONT)
            )
            overlay.invalidate()
            processNotes()
        }
    }

    private fun processNotes() {
        val currentNotes = overlay.getPressedNotes()
        val currentNoteStr = if (currentNotes.isNotEmpty()) currentNotes.first() else "--"

        // Stop notes that are no longer pressed
        for (note in lastActiveNotes) {
            if (note !in currentNotes) {
                pianoSoundPlayer.stopNote(note)
            }
        }

        // Only play NEWLY pressed notes
        for (note in currentNotes) {
            if (note !in lastActiveNotes) {
                pianoSoundPlayer.playNote(note)
            }
        }

        lastActiveNotes = currentNotes
    }

    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        if (handLandmarkerHelper::class.java != null) {
            backgroundExecutor.execute { handLandmarkerHelper.clearHandLandmarker() }
        }
        pianoSoundPlayer.stopAllNotes()
        if (isRecording) {
            toggleRecording()  // Stop recording if leaving
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pianoSoundPlayer.release()
        backgroundExecutor.shutdown()
    }
}
