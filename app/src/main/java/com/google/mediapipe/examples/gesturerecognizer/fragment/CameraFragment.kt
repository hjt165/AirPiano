package com.google.mediapipe.examples.gesturerecognizer.fragment

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.google.mediapipe.examples.gesturerecognizer.FingerPressDetector
import com.google.mediapipe.examples.gesturerecognizer.HandLandmarkerHelper
import com.google.mediapipe.examples.gesturerecognizer.PianoKeyMapper
import com.google.mediapipe.examples.gesturerecognizer.PianoSoundPlayer
import com.google.mediapipe.examples.gesturerecognizer.R
import com.google.mediapipe.examples.gesturerecognizer.SettingsDialogFragment
import com.google.mediapipe.examples.gesturerecognizer.databinding.FragmentCameraBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraFragment : Fragment(),
    HandLandmarkerHelper.HandLandmarkerListener,
    SettingsDialogFragment.SettingsListener {

    companion object {
        private const val TAG = "AirPiano"
    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var handLandmarkerHelper: HandLandmarkerHelper
    private lateinit var fingerPressDetector: FingerPressDetector
    private lateinit var pianoKeyMapper: PianoKeyMapper
    private lateinit var pianoSoundPlayer: PianoSoundPlayer

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT

    private lateinit var backgroundExecutor: ExecutorService
    private var lastActiveKeys: Set<Int> = emptySet()

    private var currentDetectionThreshold = 0.50f
    private var currentTrackingThreshold = 0.50f
    private var currentPressThreshold = FingerPressDetector.DEFAULT_PRESS_THRESHOLD
    private var currentDelegate = 0

    override fun onResume() {
        super.onResume()
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(), R.id.fragment_container
            ).navigate(R.id.action_camera_to_permissions)
        }

        backgroundExecutor.execute {
            if (handLandmarkerHelper.isClosed()) {
                handLandmarkerHelper.setupHandLandmarker()
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
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
        if (this::pianoSoundPlayer.isInitialized) {
            pianoSoundPlayer.release()
        }
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding =
            FragmentCameraBinding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        backgroundExecutor = Executors.newSingleThreadExecutor()

        fingerPressDetector = FingerPressDetector()
        pianoKeyMapper = PianoKeyMapper()
        pianoSoundPlayer = PianoSoundPlayer(requireContext())

        pianoSoundPlayer.initialize()

        fragmentCameraBinding.viewFinder.post {
            setUpCamera()
        }

        fragmentCameraBinding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        backgroundExecutor.execute {
            handLandmarkerHelper = HandLandmarkerHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                handLandmarkerListener = this
            )
        }
    }

    private fun showSettingsDialog() {
        val dialog = SettingsDialogFragment()
        dialog.setCurrentSettings(
            currentDetectionThreshold,
            currentTrackingThreshold,
            currentPressThreshold,
            currentDelegate
        )
        dialog.setSettingsListener(this)
        dialog.show(childFragmentManager, SettingsDialogFragment.TAG)
    }

    override fun onSettingsApplied(
        detectionThreshold: Float,
        trackingThreshold: Float,
        pressThreshold: Float,
        delegate: Int
    ) {
        currentDetectionThreshold = detectionThreshold
        currentTrackingThreshold = trackingThreshold
        currentPressThreshold = pressThreshold
        currentDelegate = delegate

        fingerPressDetector.setThreshold(pressThreshold)

        backgroundExecutor.execute {
            if (this::handLandmarkerHelper.isInitialized) {
                handLandmarkerHelper.minHandDetectionConfidence = detectionThreshold
                handLandmarkerHelper.minHandTrackingConfidence = trackingThreshold
                handLandmarkerHelper.currentDelegate = delegate
                handLandmarkerHelper.clearHandLandmarker()
                handLandmarkerHelper.setupHandLandmarker()
            }
        }
    }

    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(requireContext())
        )
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        imageAnalyzer =
            ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(backgroundExecutor) { image ->
                        recognizeHand(image)
                    }
                }

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun recognizeHand(imageProxy: ImageProxy) {
        val image = imageProxy.image
        if (image != null) {
            handLandmarkerHelper.recognizeLiveStream(image)
        }
        imageProxy.close()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
    }

    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
                val result = resultBundle.results

                fragmentCameraBinding.tvInferenceTime.text =
                    "推理时间: ${resultBundle.inferenceTime}ms"

                processHandResults(result)

                fragmentCameraBinding.overlay.setResults(
                    result,
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )
                fragmentCameraBinding.overlay.invalidate()
            }
        }
    }

    private fun processHandResults(result: HandLandmarkerResult) {
        val pressedFingers = fingerPressDetector.detectPress(result)
        val fingertipPositions = fingerPressDetector.getFingertipPositions(result)

        val activeKeys = mutableSetOf<String>()
        val activeKeyIndices = mutableSetOf<Int>()

        for (i in fingertipPositions.indices) {
            if (i in pressedFingers) {
                val (keyIndex, noteName) = pianoKeyMapper.mapFingerToKey(
                    fingertipPositions[i].first,
                    fingertipPositions[i].second,
                    pressedFingers
                )
                if (keyIndex >= 0 && noteName.isNotEmpty()) {
                    activeKeys.add(noteName)
                    activeKeyIndices.add(keyIndex)
                }
            }
        }

        val currentNote = if (activeKeys.isNotEmpty()) activeKeys.first() else "--"
        fragmentCameraBinding.tvCurrentNote.text = "当前音符: $currentNote"

        fragmentCameraBinding.pianoView.setPressedKeys(activeKeyIndices)
        fragmentCameraBinding.pianoView.setCurrentNote(currentNote)

        for (note in activeKeys) {
            pianoSoundPlayer.playNote(note)
        }

        for (note in lastActiveKeys) {
            if (note !in activeKeys) {
                pianoSoundPlayer.stopNote(note)
            }
        }

        lastActiveKeys = activeKeys
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }
}
