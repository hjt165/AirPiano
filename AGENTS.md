# AGENTS.md

Centralized instructions for AI agents working in the Air Piano repository.

## Project Identity

**Air Piano (空气钢琴)** — Android app using MediaPipe hand tracking to play piano notes via finger-tapping gestures in the air. Course design project for "AI Image Application" course.

**Package:** `com.google.mediapipe.examples.gesturerecognizer`

## Build Commands

All commands run from `android/` directory. On Windows use `gradlew.bat`.

```bash
cd android

# Build
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease        # Release APK (no signing config)
./gradlew clean                  # Clean build

# Tests (instrumented only, no unit tests exist)
./gradlew connectedAndroidTest   # Requires connected device/emulator
./gradlew test                   # No unit tests — will pass vacuously
```

**Important:** The gesture model (`gesture_recognizer.task`) auto-downloads during build via `app/download_tasks.gradle`. Requires internet on first build.

## Build Environment

| Setting | Value |
|---------|-------|
| Gradle | 8.9 |
| Android Gradle Plugin | 7.3.0 |
| Kotlin | 1.7.10 |
| compileSdk / targetSdk | 32 |
| minSdk | 24 (Android 7.0) |
| JVM target | 1.8 |

## Architecture

Single-module Gradle project. Fragment-based Navigation Component with one Activity.

```
MainActivity (Navigation Component host)
  ├── PermissionsFragment → requests camera permission
  ├── CameraFragment → CameraX + MediaPipe + Air Piano
  └── GalleryFragment → image/video selection for offline inference
```

### Key Files (read before modifying)

| File | Role |
|------|------|
| `AirPianoManager.kt` | Core piano logic: state machine (UNKNOWN→TAP_DOWN→HOLD→RELEASE), 5-frame weighted moving average smoothing, MediaPlayer for 5 notes. **Custom code — primary focus for air piano changes.** |
| `PianoKeyDrawer.kt` | Draws 5 colored piano keys (Do=red, Re=orange, Mi=gold, Fa=green, Sol=blue) at bottom of OverlayView. |
| `GestureRecognizerHelper.kt` | Wraps MediaPipe GestureRecognizer API. Handles IMAGE/VIDEO/LIVE_STREAM modes, bitmap rotation, front-camera flip. |
| `OverlayView.kt` | Custom View drawing hand landmarks (yellow dots, cyan lines) and delegating to PianoKeyDrawer. |
| `CameraFragment.kt` | CameraX integration, initializes AirPianoManager, feeds frames to recognizer, updates piano key states via PianoListener. |
| `MainViewModel.kt` | Persists recognition settings + tap/hold thresholds across config changes. |

### Air Piano Data Flow

```
CameraX ImageAnalysis → GestureRecognizerHelper.recognizeLiveStream()
  → onResults() → airPianoManager.processFrame(result)
    → processAirpianoLogic(landmarks) — per-finger state machine
    → playSound() on TAP_DOWN + PianoListener.onFingerPressed()
    → OverlayView.setPianoKeyStates() → PianoKeyDrawer.draw()
```

### Air Piano Thresholds (in AirPianoManager.kt)

| Constant | Value | Purpose |
|----------|-------|---------|
| `TAP_THRESHOLD` | 0.025 | Fingertip Y-offset for tap detection (index–pinky) |
| `THUMB_TAP_THRESHOLD` | 0.015 | Lower threshold for thumb (more sensitive) |
| `HOLD_THRESHOLD` | 0.015 | Threshold to detect sustained hold |
| `SMOOTHING_FACTOR` | 0.8 | Weighted moving average coefficient (5 frames) |

## Dependencies (Critical)

| Dependency | Version | Notes |
|------------|---------|-------|
| MediaPipe Tasks Vision | 0.10.26 | Gesture recognition model |
| CameraX | 1.2.0-alpha02 | Camera preview + frame analysis |
| Navigation Component | 2.5.3 | Fragment navigation |
| Material Design | 1.7.0 | UI components |

**Do not upgrade CameraX past alpha02 without testing — alpha APIs change frequently.**

## Testing

- **Instrumented tests only** (`androidTest/`). No unit tests exist.
- `GestureRecognizerTest.kt` has 3 tests: LIVE_STREAM, VIDEO, IMAGE modes with thumb-up image.
- Test assets: `hand_thumb_up.jpg`, `test_video.mp4`.
- Tests expect ~0.81 confidence for "Thumb_Up" gesture.

## Known Constraints

- **Front camera only** — `LENS_FACING_FRONT` with horizontal flip in `recognizeLiveStream()`.
- **WAV files excluded from git** (`.gitignore` has `*.wav`). Audio must be regenerated or provided.
- **No CI/CD** — no GitHub Actions, no automated builds.
- **Not a git repository** at root level — `.git` exists only in `android/` subdirectory.
- **ProGuard disabled** — minification is off for release builds.

## Modification Guidelines

1. **Adding new gestures:** Modify `AirPianoManager.kt` state machine. Add new states if needed beyond UNKNOWN→TAP_DOWN→HOLD→RELEASE.
2. **Changing piano keys:** Edit `PianoKeyDrawer.kt` (colors, positions, count). Update `res/raw/` for new audio files.
3. **Adjusting sensitivity:** Tune thresholds in `AirPianoManager.kt` or expose via `MainViewModel.kt` for runtime adjustment.
4. **Adding new fragments:** Update `nav_graph.xml` and `menu_bottom_nav.xml`. Follow existing fragment pattern in `fragment/` directory.
5. **Audio changes:** WAV files in `res/raw/` — sine waves at 261–392 Hz, 44100Hz, 16-bit, 1s duration.
