# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**"Air Piano -- Gesture-based Performance using MediaPipe"** (空气钢琴--基于MediaPipe的手势演奏). An Android app that uses camera-based hand tracking to let users play piano notes by making finger-tapping gestures in the air.

The project is based on Google's MediaPipe Gesture Recognizer sample app, extended with custom piano-playing logic.

## Repository Structure

- `课程设计.md` — Course design outline and grading rubric
- `MediaPipe空气钢琴案例分析.md` — Technical case analysis with gesture detection logic and Kotlin code snippets
- `需求文档.md` — Requirements document (8 functional requirements, non-functional requirements, user scenarios)
- `UI设计.md` — UI design specification (layout, color scheme, typography, interaction)
- `PPT大纲.md` — 10-slide presentation outline
- `android/` — The Android application (MediaPipe Gesture Recognizer demo + Air Piano extension)

## Build Commands

The Android project uses Gradle. All commands run from the `android/` directory:

```bash
cd android

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run unit tests
./gradlew test

# Clean build
./gradlew clean
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

The gesture recognizer model (`gesture_recognizer.task`) is downloaded automatically during build via `app/download_tasks.gradle`.

## Architecture

**Package:** `com.google.mediapipe.examples.gesturerecognizer`

### Core Components

- **`GestureRecognizerHelper`** — Wraps MediaPipe's `GestureRecognizer` API. Supports three running modes: `IMAGE`, `VIDEO`, `LIVE_STREAM`. Handles image rotation, bitmap conversion, and delegates results via `GestureRecognizerListener` interface. Confidence thresholds and CPU/GPU delegate are configurable.

- **`MainViewModel`** — Persists recognition settings (delegate, confidence thresholds) across configuration changes.

- **`OverlayView`** — Custom `View` that draws hand landmarks and connections on a canvas overlay, scaling coordinates to match the preview.

### Fragments (Navigation Component)

- **`PermissionsFragment`** — Checks camera permission, navigates to camera on grant.
- **`CameraFragment`** — CameraX integration. Binds preview + image analysis use cases. Feeds frames to `GestureRecognizerHelper.recognizeLiveStream()` on a background executor. Displays results in a RecyclerView and updates the overlay.
- **`GalleryFragment`** — Handles image/video selection from gallery for inference.

### Key Dependencies

- **MediaPipe Tasks Vision** (`com.google.mediapipe:tasks-vision:0.10.26`) — Gesture recognition
- **CameraX** (`1.2.0-alpha02`) — Camera preview and frame analysis
- **Navigation Component** (`2.5.3`) — Fragment navigation
- **View Binding** — Layout inflation

### Data Flow (Live Stream Mode)

```
CameraX ImageAnalysis → ImageProxy → GestureRecognizerHelper.recognizeLiveStream()
  → Bitmap rotation/flip → MPImage → GestureRecognizer.recognizeAsync()
  → returnLivestreamResult() → GestureRecognizerListener.onResults()
  → UI thread: update RecyclerView + OverlayView
```

## Air Piano Implementation

The air piano feature is fully implemented. Key files:

- **`AirPianoManager.kt`** — Core piano logic. State machine (UNKNOWN→TAP_DOWN→HOLD→RELEASE) detects finger taps by comparing fingertip Y-offset relative to wrist. Uses 5-frame weighted moving average smoothing. MediaPlayer plays 5 notes (d/re/mi/fa/sol). Lower threshold for thumb (`THUMB_TAP_THRESHOLD=0.015`) vs other fingers (`TAP_THRESHOLD=0.025`).

- **`PianoKeyDrawer.kt`** — Draws 5 colored piano keys at the bottom of the OverlayView. Keys positioned with `bottomOffset` to avoid overlap with the bottom sheet. Colors: Do=red, Re=orange, Mi=gold, Fa=green, Sol=blue.

- **`OverlayView.kt`** — Modified to include `PianoKeyDrawer` and `setPianoKeyStates()` method.

- **`CameraFragment.kt`** — Modified to initialize `AirPianoManager`, call `processFrame()` in `onResults()`, and update piano key states via `PianoListener` callbacks.

- **`res/raw/`** — Contains 5 WAV files (d.wav, re.wav, mi.wav, fa.wav, sol.wav) generated as sine waves (261–392 Hz, 44100Hz, 16-bit, 1s).

Data flow extension:

```
onResults() → airPianoManager.processFrame(result)
  → processAirpianoLogic(landmarks) — per-finger state machine
  → playSound() on TAP_DOWN + PianoListener.onFingerPressed()
  → OverlayView.setPianoKeyStates() → PianoKeyDrawer.draw()
```

The case analysis document (`MediaPipe空气钢琴案例分析.md`) contains the original design rationale for the gesture detection logic.

## Build Environment

- Android Gradle Plugin: 7.3.0
- Kotlin: 1.7.10
- compileSdk/targetSdk: 32
- minSdk: 24 (Android 7.0)
- JVM target: 1.8
