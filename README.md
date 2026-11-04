# HALYN Motion

**GPU-powered, minimalist live wallpapers for Android.**

A separate wallpaper studio in the same visual family as CYN and HALO. This is not a notification overlay or a PC companion.

## Preview v0.1

- Six procedural, visually distinct live wallpaper scenes: **Ion Veil**, **Liquid Chrome**, **Nebula Drift**, **Electric Silk**, **Solar Bloom**, and **Prism Tide**.
- Five accent palettes, two motion controls (intensity and speed) and configurable 30/45/60 FPS.
- Full-screen, native Android `WallpaperService` with a hardware-accelerated AGSL `RuntimeShader`; no video loops or remote assets.
- A dark wallpaper studio with animated preview, touch interaction and an **Apply live wallpaper** button that opens Android's system wallpaper picker.
- Optional home-screen parallax and touch response. When the wallpaper is hidden or its surface is removed, the render loop stops.
- No network, microphone, audio recording, accessibility, notification-listener or overlay permission.

**Device floor:** Android 13+ (API 33) for AGSL, targeting modern Samsung One UI and Galaxy A56.

## Install

Use the `HALYN-Motion-Android` artifact from a successful GitHub Actions build, or install the APK attached to a preview release. Open the app and select **Apply live wallpaper**.

> Development preview. The code compiles in CI before a release is published; actual performance on a Galaxy A56 and Samsung's wallpaper picker must still be verified on-device. Motion is procedural, not currently synchronized with music or wallpaper colors.

## Build

- Java 17
- Gradle 8.11.1
- Android Gradle Plugin 8.9.2 / Kotlin 2.1.20
- Android SDK 35

Run: `gradle :app:assembleDebug`.

GitHub Actions handles the Android SDK setup and produces an installable APK. **No Unity editor or Unity account required.**

## Architecture

- `HalynWallpaperService.kt`: Android live wallpaper service, render scheduling, parallax/touch, visibility lifecycle.
- `HalynRenderer.kt`: six AGSL shaders with palette controls, settings persistence and GPU-only frame rendering.
- `MainActivity.kt`: native responsive wallpaper studio, live preview and system wallpaper picker.

## Privacy

All settings are kept in local `SharedPreferences`. The app declares no dangerous permissions. No telemetry, ads, analytics or network access.

## License

Copyright (c) the repository owner. No license has been granted for redistribution beyond applicable law.
