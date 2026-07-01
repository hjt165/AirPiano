package com.google.mediapipe.examples.gesturerecognizer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SettingsDialogFragment : BottomSheetDialogFragment() {

    private var currentDetectionThreshold = 0.50f
    private var currentTrackingThreshold = 0.50f
    private var currentPressThreshold = 0.07f
    private var currentDelegate = 0

    private var listener: SettingsListener? = null

    interface SettingsListener {
        fun onSettingsApplied(
            detectionThreshold: Float,
            trackingThreshold: Float,
            pressThreshold: Float,
            delegate: Int
        )
    }

    fun setSettingsListener(listener: SettingsListener) {
        this.listener = listener
    }

    fun setCurrentSettings(
        detectionThreshold: Float,
        trackingThreshold: Float,
        pressThreshold: Float,
        delegate: Int
    ) {
        currentDetectionThreshold = detectionThreshold
        currentTrackingThreshold = trackingThreshold
        currentPressThreshold = pressThreshold
        currentDelegate = delegate
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.settings_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvDetectionValue = view.findViewById<TextView>(R.id.tv_detection_value)
        val tvTrackingValue = view.findViewById<TextView>(R.id.tv_tracking_value)
        val tvPressValue = view.findViewById<TextView>(R.id.tv_press_value)
        val rgDelegate = view.findViewById<RadioGroup>(R.id.rg_delegate)

        tvDetectionValue.text = String.format("%.2f", currentDetectionThreshold)
        tvTrackingValue.text = String.format("%.2f", currentTrackingThreshold)
        tvPressValue.text = String.format("%.2f", currentPressThreshold)

        if (currentDelegate == 0) {
            rgDelegate.check(R.id.rb_cpu)
        } else {
            rgDelegate.check(R.id.rb_gpu)
        }

        setupThresholdControl(
            view.findViewById(R.id.btn_detection_minus),
            view.findViewById(R.id.btn_detection_plus),
            tvDetectionValue,
            { currentDetectionThreshold },
            { currentDetectionThreshold = it }
        )

        setupThresholdControl(
            view.findViewById(R.id.btn_tracking_minus),
            view.findViewById(R.id.btn_tracking_plus),
            tvTrackingValue,
            { currentTrackingThreshold },
            { currentTrackingThreshold = it }
        )

        setupThresholdControl(
            view.findViewById(R.id.btn_press_minus),
            view.findViewById(R.id.btn_press_plus),
            tvPressValue,
            { currentPressThreshold },
            { currentPressThreshold = it },
            step = 0.01f,
            min = 0.01f,
            max = 0.20f
        )

        rgDelegate.setOnCheckedChangeListener { _, checkedId ->
            currentDelegate = if (checkedId == R.id.rb_cpu) 0 else 1
        }

        view.findViewById<Button>(R.id.btn_apply).setOnClickListener {
            listener?.onSettingsApplied(
                currentDetectionThreshold,
                currentTrackingThreshold,
                currentPressThreshold,
                currentDelegate
            )
            dismiss()
        }
    }

    private fun setupThresholdControl(
        minusBtn: ImageButton,
        plusBtn: ImageButton,
        valueTv: TextView,
        getter: () -> Float,
        setter: (Float) -> Unit,
        step: Float = 0.10f,
        min: Float = 0.10f,
        max: Float = 0.90f
    ) {
        minusBtn.setOnClickListener {
            val newValue = (getter() - step).coerceAtLeast(min)
            setter(newValue)
            valueTv.text = String.format("%.2f", newValue)
        }

        plusBtn.setOnClickListener {
            val newValue = (getter() + step).coerceAtMost(max)
            setter(newValue)
            valueTv.text = String.format("%.2f", newValue)
        }
    }

    companion object {
        const val TAG = "SettingsDialog"
    }
}
