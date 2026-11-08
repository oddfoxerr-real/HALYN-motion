# HALYN Motion — 30 Smooth Originals

## v0.15.0 Preview

The HALYN collection now includes 30 distinct live procedural wallpaper scenes. Each uses an individually authored geometry/motion field and its own material colours, with continuously interpolated textures rather than mismatched pixel effects or recoloured flat shading. A native-resolution AGSL material compositor reconstructs surface normals, soft lighting, reflection highlights, atmosphere, and subtle filmic tonemapping.

**Glass & minerals:** Frosted Glass Ridges, Spun Aluminum, Cathedral Quartz, Basalt Cross-Section, Acrylic Lens Stack, Pearlescent Shell.

**Nature & water:** Submerged Silk, Auroral Curtains, Wind-Carved Dunes, Glacial Snowbanks, Mist-Cut Canyon, Rainy Window.

**Tactile materials:** Satin Pleats, Titanium Brushwork, Silken Carbon Weave, Porcelain Sculpture, Velvet Cascade, Slate Relief.

**Space & atmosphere:** Cosmic Filament, Solar Corona, Observatory Night, Planet Atmosphere, Gravitational Halo, Meteor Smoke.

**Architecture & sculpture:** Illuminated Colonnade, Marble Archways, Sculpted Wall Panels, Suspended Basalt, Silent Atrium, Twisting Ribbon Canopy.

### Preserved features

Four minimalist mountain wallpapers and Star Window remain in their own collection. The licensed 4K photo gallery, local photo imports, five clock designs, optional moveable clock, custom fonts, and gyro tracking remain available. Previously saved flat-colour wallpaper IDs migrate to Frosted Glass Ridges, while existing photo choices keep their ID.

### Architecture

- SmoothWallpaperRenderer.kt: thirty independent shader definitions, runtime-compiled AGSL, six-program LRU, continuous smooth noise and GPU-failure fallback.
- HalynRenderer.kt: compact wallpaper dispatcher for smooth materials, photographs, mountain scenes and Star Window. Previous flat-colour shader library has been physically deleted.
- MainActivity.kt: five groups of six new scenes, with separate landscape/star rows and all previous studio controls.
- HalynWallpaperService.kt: adaptive FPS, surface lifecycle and clock composition.

Android 13+ required (API 33); same package name as older HALYN versions. The APK is debug-signed. Android CI tests compilation and archive integrity but cannot confirm AGSL compilation, animation quality, or thermals on a Galaxy A56. Verify on the phone after installing.

### Build

JDK 17, Android SDK 35, Gradle 8.11.1: run `gradle --no-daemon :app:assembleDebug`. APK output: `app/build/outputs/apk/debug/app-debug.apk`.
