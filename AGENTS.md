# AGENTS.md

## Project Configuration

**Working Directory:** `E:\AIR`
**Git Repository:** `https://github.com/hjt165/AirPiano.git`
**Branch:** `main`
**Package:** `com.google.mediapipe.examples.gesturerecognizer`

## Roles

- **AI Agent (opencode):** Write code only. Do NOT run gradlew or build commands.
- **User (hjt165):** Handle all compilation, building, and APK installation.

## Project Structure

```
E:\AIR/
├── app/src/main/java/com/google/mediapipe/examples/gesturerecognizer/
│   ├── MainActivity.kt              - Main entry (portrait, bottom nav)
│   ├── PianoActivity.kt             - Piano playing (landscape, full-screen)
│   ├── OverlayView.kt               - Hand landmarks + piano overlay
│   ├── HandLandmarkerHelper.kt      - MediaPipe hand detection
│   ├── PianoSoundPlayer.kt          - Sound generation & playback
│   ├── SettingsDialogFragment.kt    - Settings bottom sheet
│   ├── model/
│   │   └── Recording.kt             - Recording data class
│   ├── adapter/
│   │   └── RecordingAdapter.kt      - History list adapter
│   └── fragment/
│       ├── CameraFragment.kt        - Camera preview (legacy)
│       ├── PermissionsFragment.kt   - Permission request
│       └── HistoryFragment.kt       - Recording history list
├── app/src/main/res/
│   ├── layout/
│   │   ├── activity_main.xml        - Portrait main layout
│   │   ├── activity_piano.xml       - Landscape piano layout
│   │   ├── fragment_camera.xml      - Camera fragment
│   │   ├── fragment_history.xml     - History list
│   │   ├── item_recording.xml       - Recording list item
│   │   └── settings_dialog.xml      - Settings dialog
│   ├── menu/
│   │   └── bottom_nav_menu.xml      - Bottom navigation menu
│   └── navigation/
│       └── nav_graph.xml            - Navigation graph
├── docs/
│   ├── 功能需求文档.md
│   ├── UI设计说明.md
│   └── 演讲PPT.md
└── AGENTS.md                        - This file
```

## Architecture

- **MainActivity** (portrait): Bottom navigation with Piano and History tabs
- **PianoActivity** (landscape): Full-screen camera + piano overlay + recording
- MediaPipe Hand Landmarker for hand detection
- PianoSoundPlayer generates WAV files dynamically with realistic piano timbre

## Key Features

1. Real-time hand tracking via front camera
2. 14-key piano overlay (C4-B5) with transparent design
3. Touch-free playing by fingertip position detection
4. Recording and playback of performances
5. Adjustable thresholds and octave control
6. Multiple instrument support (piano, guitar, synth)
