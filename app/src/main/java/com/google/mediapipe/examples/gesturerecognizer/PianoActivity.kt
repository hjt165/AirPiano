package com.google.mediapipe.examples.gesturerecognizer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mediapipe.examples.gesturerecognizer.model.Recording
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PianoActivity : AppCompatActivity(), HandLandmarkerHelper.HandLandmarkerListener,
    SettingsDialogFragment.SettingsListener {

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

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                setUpCamera()
            } else {
                Toast.makeText(this, "需要相机权限才能使用钢琴功能", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        setContentView(R.layout.activity_piano)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentOctave = prefs.getInt(KEY_OCTAVE, 4)

        overlay = findViewById(R.id.overlay)
        tvInfo = findViewById(R.id.tv_info)
        btnRecord = findViewById(R.id.btn_record)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            showSettingsDialog()
        }

        findViewById<Button>(R.id.btn_octave_down).setOnClickListener {
            currentOctave = (currentOctave - 1).coerceAtLeast(2)
            prefs.edit().putInt(KEY_OCTAVE, currentOctave).apply()
            overlay.setOctave(currentOctave)
            pianoSoundPlayer.setOctave(currentOctave)
            updateInfoText()
        }

        findViewById<Button>(R.id.btn_octave_up).setOnClickListener {
            currentOctave = (currentOctave + 1).coerceAtMost(6)
            prefs.edit().putInt(KEY_OCTAVE, currentOctave).apply()
            overlay.setOctave(currentOctave)
            pianoSoundPlayer.setOctave(currentOctave)
            updateInfoText()
        }

        btnRecord.setOnClickListener {
            toggleRecording()
        }

        val instruments = arrayOf("钢琴", "吉他", "合成器")
        val spinner = findViewById<Spinner>(R.id.spinner_instrument)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, instruments)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        backgroundExecutor = Executors.newSingleThreadExecutor()

        pianoSoundPlayer = PianoSoundPlayer(this)
        pianoSoundPlayer.initialize(currentOctave)

        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                pianoSoundPlayer.setInstrument(instruments[position])
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        overlay.setOctave(currentOctave)

        handLandmarkerHelper = HandLandmarkerHelper(
            context = this,
            runningMode = RunningMode.LIVE_STREAM,
            handLandmarkerListener = this
        )

        updateInfoText()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            findViewById<androidx.camera.view.PreviewView>(R.id.view_finder).post {
                setUpCamera()
            }
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun toggleRecording() {
        if (isRecording) {
            val events = pianoSoundPlayer.stopRecording()
            isRecording = false
            btnRecord.text = "录制"
            btnRecord.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))

            if (events.isNotEmpty()) {
                val duration = events.last().timestampMs
                val recording = Recording(
                    name = "录音 ${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}",
                    duration = duration,
                    noteEvents = events
                )
                saveRecording(recording)
                Toast.makeText(this, "录音已保存 (${events.size}个音符)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "录制为空，未保存", Toast.LENGTH_SHORT).show()
            }
        } else {
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
        if (recordings.size > 50) {
            recordings.subList(50, recordings.size).clear()
        }
        prefs.edit().putString(KEY_RECORDINGS, Gson().toJson(recordings)).apply()
    }

    private fun updateInfoText() {
        tvInfo.text = "八度: $currentOctave"
    }

    private fun showSettingsDialog() {
        val dialog = SettingsDialogFragment()
        dialog.setCurrentSettings(
            handLandmarkerHelper.minHandDetectionConfidence,
            handLandmarkerHelper.minHandTrackingConfidence,
            0.07f,
            handLandmarkerHelper.currentDelegate,
            handLandmarkerHelper.numHands
        )
        dialog.setSettingsListener(this)
        dialog.show(supportFragmentManager, SettingsDialogFragment.TAG)
    }

    override fun onSettingsApplied(
        detectionThreshold: Float,
        trackingThreshold: Float,
        pressThreshold: Float,
        delegate: Int,
        numHands: Int
    ) {
        handLandmarkerHelper.clearHandLandmarker()
        handLandmarkerHelper.minHandDetectionConfidence = detectionThreshold
        handLandmarkerHelper.minHandTrackingConfidence = trackingThreshold
        handLandmarkerHelper.currentDelegate = delegate
        handLandmarkerHelper.numHands = numHands
        handLandmarkerHelper.setupHandLandmarker()
        Toast.makeText(this, "设置已应用", Toast.LENGTH_SHORT).show()
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
            Log.e(TAG, "Camera binding failed", e)
        }
    }

    private fun recognizeHand(imageProxy: ImageProxy) {
        try {
            handLandmarkerHelper.recognizeLiveStream(imageProxy)
        } catch (e: Exception) {
            Log.e(TAG, "Error in recognizeHand", e)
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

        for (note in lastActiveNotes) {
            if (note !in currentNotes) {
                pianoSoundPlayer.stopNote(note)
            }
        }

        for (note in currentNotes) {
            if (note !in lastActiveNotes) {
                pianoSoundPlayer.playNote(note)
            }
        }

        lastActiveNotes = currentNotes
        overlay.setCurrentNote(if (currentNotes.isNotEmpty()) currentNotes.joinToString(", ") else "--")
    }

    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!this::handLandmarkerHelper.isInitialized) {
            handLandmarkerHelper = HandLandmarkerHelper(
                context = this,
                runningMode = RunningMode.LIVE_STREAM,
                handLandmarkerListener = this
            )
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            findViewById<androidx.camera.view.PreviewView>(R.id.view_finder).post {
                setUpCamera()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (this::handLandmarkerHelper.isInitialized) {
            backgroundExecutor.execute { handLandmarkerHelper.clearHandLandmarker() }
        }
        if (this::pianoSoundPlayer.isInitialized) {
            pianoSoundPlayer.stopAllNotes()
        }
        cameraProvider?.unbindAll()
        if (isRecording) {
            toggleRecording()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::pianoSoundPlayer.isInitialized) {
            pianoSoundPlayer.release()
        }
        if (this::backgroundExecutor.isInitialized) {
            backgroundExecutor.shutdown()
        }
    }
}
