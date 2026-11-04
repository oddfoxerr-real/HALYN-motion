# HALYN Motion — Dynamic Wallpaper Research and Technical Roadmap

**Research date:** 2026-11-04  
**Scope:** Native Android live wallpapers, visual quality, interactive effects, Samsung Galaxy A56 / One UI behavior, performance, architecture and licensing.  
**Hard constraint:** Preserve all six original HALYN effects exactly: Ion Veil, Liquid Chrome, Nebula Drift, Electric Silk, Solar Bloom and Prism Tide. New systems are opt-in/additive; this document does not change implementation.

## Executive conclusion

A wallpaper is not truly dynamic merely because it loops, shifts hues or animates mathematically. Premium dynamic wallpapers react continuously and plausibly to touch, motion, page swipes and environment, with smooth temporal continuity and responsible energy use. HALYN should grow into an **engine plus a curated set of scenes**, not a giant wallpaper catalog or a collage of filters.

The existing v0.1 implementation is a functional native `WallpaperService` with an Android 13+ AGSL `RuntimeShader`, six time-driven shader branches and simple touch-glow, page-offset, palette, speed and FPS controls. **It is not yet a fluid simulator, a multi-layer 3D scene engine, gyroscope-controlled parallax, a music-reactive visualizer or a device-tested performance profile.**

The most important future improvement is a **stateful GPU simulation path**. Use the existing AGSL one-pass renderer for lightweight procedural scenes; add an OpenGL ES 3.x/EGL backend for effects requiring persistent textures, multiple passes, particle state or depth. Keep the wallpaper as a real `WallpaperService`, not an overlay or a background video.

## 1. Current ecosystem: what to benchmark

| Product | Verified strengths | Implication for HALYN |
| --- | --- | --- |
| Wallpaper Engine (Android) | Imported videos/GIFs, interactive 2D/3D, playlists, timed rotation, power-saving pause | A collection may combine several rendering modalities. Import and scheduling are valuable later, not required for first premium release. |
| Wave Live Wallpaper (Paper World) | Realtime native 3D, high refresh-rate support, powerful theme editor, shared presets | Better tactile materials, light/shading, 3D geometry and preset interchange are differentiation points. |
| Fluid Simulation (DoGreat Technologies) | Highly tactile, persistent fluid flow and on-touch manipulation; useable as wallpaper | Use genuine simulation rather than merely rendering a touch highlight on a time-based shader. |
| KLWP (Kustom) | Touch/scroll/gyro, animation composition, variables, music metadata, advanced expressions | Useful proof of configurability, but HALYN should avoid reproducing its heavy editor-first UX. |
| Doodle Android | Parallax, dark mode, system-color support, energy efficiency, no heavyweight 3D engine | High-quality low-motion modes and wallpaper color reporting are important. |
| Samsung Wonderland (Good Lock) | Samsung-native photo layering, motion and lock screen effects | Native Samsung look, smart image fitting and distinct lock-screen experience matter. |

Primary references:
- Wallpaper Engine Android: https://play.google.com/store/apps/details?id=io.wallpaperengine.weclient
- Wave Live Wallpaper: https://play.google.com/store/apps/details?id=wave.paperworld.wallpaper
- Fluid Simulation: https://play.google.com/store/apps/details?id=games.paveldogreat.fluidsimfree
- KLWP: https://play.google.com/store/apps/details?id=org.kustom.wallpaper
- Doodle Android: https://github.com/patzly/doodle-android
- Samsung Wonderland: https://r2.community.samsung.com/t5/Tips-How-to/Bring-your-phone-s-wallpaper-to-life/ba-p/23102413

## 2. Categories of real dynamic wallpapers

### A. Procedural light, fabric and geometry

Examples: Aurora curtains, liquid-metal contours, folded silk, crystal prisms, abstract glass, volumetric fog and optical interference. Can be produced with AGSL when a single frame can be derived from time, coordinate and parameters. Quality depends more on precise material treatment than on adding detail: temporal coherence, physically convincing gradients, variation in spatial frequency, controlled luminance, realistic reflections and soft/clean silhouettes.

**HALYN fit:** Keep all six original shaders. Add richer material-lighting models to *new* scene presets, not as retroactive replacements.

### B. Stateful fluid and particle simulation

A touch stroke injects velocity, dye or a force field; that field continues advecting, swirling, fading and colliding afterward. Real-time solvers typically need double-buffered velocity and dye textures, pressure projection, vorticity and repeated GPU passes, plus a controllable render upscale. A single stateless fragment shader cannot recreate persistent fluid advection by merely varying `u_time` and `u_touch`.

**HALYN fit:** Optional OpenGL ES backend with ping-pong framebuffer objects (FBOs), simulation at reduced internal resolution, touch-vector injection and configurable dissipation. A good research implementation is Pavel Dobryakov's MIT-licensed WebGL Fluid Simulation (port core mathematics/shader techniques to GLSL ES; retain required license attribution).

Source: https://github.com/PavelDoGreat/WebGL-Fluid-Simulation

### C. Real depth, layered media and parallax

Parallax needs independent depth coefficients for layers. Offset-only movement of the entire image is not convincing depth. Build a scene graph where each layer has position, depth, scale, mask and a distinct motion response. Android wallpaper scroll offset and gyroscope/rotation-vector input can each move layers, but should be eased and bounded to avoid motion sickness.

**HALYN fit:** Layered 2.5D depth as a separate mode, with proper occlusion and camera safe areas. Importing gallery photos for foreground/background segmentation is a later optional feature; a segmentation model is not needed for the first curated scenes.

Wallpaper Engine parallax reference: https://docs.wallpaperengine.io/en/scene/parallax/introduction.html

### D. Context-aware scenes

Examples: Night-mode palettes, quiet sunrise/day/sunset progression, charging-themed accents, battery-saver dark/low-motion mode, automatic scheduled preset rotation, daily color variation. These need observable system events or scheduled transitions—not constant polling.

**HALYN fit:** Sunrise/day/night palette timeline, battery-saver modes and scheduled preset playlist, all opt-in. Weather/location would add permissions and network dependencies and is not a first-phase feature.

### E. Imported video/GIF wallpapers

Well suited to custom art and clips, but they can look repetitive, require crop and decoder controls, and may perform poorly when always decoding large frames.

**HALYN fit:** Later distinct imported-media engine. Preserve aspect ratio; center/fit/fill controls; explicit loop, trim and mute; avoid mixing it into physics effects.

### F. Audio-reactive scenes

Music can drive envelope, transient and spectral visual parameters rather than merely spinning brighter. However, Android playback capture requires `MediaProjection` consent and `RECORD_AUDIO`; capture of another app depends on that app's audio usage and capture policy. A normal wallpaper service cannot guarantee access to Spotify/other third-party PCM audio. Metadata and playback state are not actual beat waveforms.

**HALYN fit:** Prototype only as a user-enabled mode, not core default. Explicitly separate: (1) local beat source controlled by HALYN, (2) metadata-based cues, (3) consent-based playback capture where permitted. Never secretly intercept audio.

Android source: https://developer.android.com/media/platform/av-capture

## 3. Visual directions suitable for HALYN

Propose **additions**, not replacements:

1. **Silk Reactor** — dark refractive ribbon surfaces with several depth planes, micro-specular highlights and slowed gravity-like folds.
2. **Fluid Bloom** — genuine finger-driven liquid dye, with momentum after touch release, vortices and adjustable viscosity.
3. **Glass Orbit** — semi-transparent 3D glass rings and shifting caustic highlights; extremely slow idle motion.
4. **Magnetic Dust** — hundreds/thousands of responsive particles following an invisible field; tap causes a localized impulse, the field reforms afterward.
5. **Depthfield** — segmented 2.5D layered scenery with gyro/parallax, smooth camera moves and optional static photo.
6. **Chromatic Weather** — ambient abstract scene changes gradually over time of day, never abruptly flickering.
7. **Ink in Water** — fluid smoky diffusion with finer pigment filaments and deliberate negative space.
8. **Luminous Origami** — folded geometric forms with realistic material normals, rim lighting and controlled color accents.

Avoid: oversaturated noise, all-over glow, random particles without purpose, rainbow-on-every-frame effects, shaky camera, blackening overlays, color posterization, and ugly quantized/pixelated fake-paint effects.

### Composition rules

- One focal visual gesture per scene, not six simultaneously.
- Leave safe negative space behind home screen icons and clock, especially upper-middle and lower dock regions.
- Respect portrait crops, lockscreen clock positions, gesture bar and navigation areas.
- Use deep near-black/charcoal backgrounds by default for OLED comfort; allow higher-key themes explicitly.
- Materials should be visibly distinct: metal should reflect, glass should refract, silk should fold, fluid should advect.
- Keep idle motion slow and let direct interaction temporarily raise energy.
- Avoid strobing and provide a reduced-motion option.

## 4. Exact v0.1 architecture audit

**What exists now:**
- `HalynWallpaperService` extends Android `WallpaperService` and stops scheduling render frames when not visible.
- `HalynRenderer` uses `RuntimeShader` / AGSL for the six mathematical scenes.
- Studio offers live preview, six presets, five palettes, intensity/speed controls, FPS selection, touch toggle and page-offset toggle.
- Background render uses `SurfaceHolder.lockHardwareCanvas()`.
- CI builds an APK using Android Gradle tooling, no Unity dependency.

**Important limitations found in the code:**
1. **Touch is a glow, not simulation:** shader computes `nearTouch` but no velocity/dye history or persistent waves.
2. **Parallax is page offset only:** `u_offset` maps launcher page movement; no rotation-vector sensor, per-layer depth or easing.
3. **Monolithic shader:** six scene branches in one AGSL shader source. Separate shader assets and metadata are easier to profile and maintain.
4. **No system color reporting:** `onComputeColors()` is not overridden. Explicit `WallpaperColors` and `notifyColorsChanged()` on palette edits would improve Android/One UI wallpaper color integration.
5. **No system wallpaper zoom:** `onZoomChanged()` is not handled; it could improve transition polish.
6. **Fixed resolution:** heavy full-screen shader computation scales linearly with display pixels. Dedicated render targets and resolution scaling would help complex effects.
7. **Frame scheduling uses timed main-thread handler:** targeted FPS is not guaranteed to be display-synchronized; `Choreographer`/paced EGL swap for a future dedicated renderer is preferable.
8. **No adaptive quality:** does not yet respond explicitly to power saver/thermal load or GPU timing.
9. **No verified A56 runtime measurements:** a successful APK build does not demonstrate smooth performance or compatibility with all One UI lock-screen modes.
10. **No true 3D engine, video renderer, photo importer or music synchronization:** these are potential next-phase features, not existing capabilities.
11. **45 FPS selection** is not a clean divisor of a fixed 120 Hz panel refresh. Prefer 30 or 60 FPS as stable starting targets and let the system negotiate supported rates; test actual device timing.

API documentation: 
- https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine
- https://developer.android.com/develop/ui/views/graphics/agsl
- https://developer.android.com/reference/android/graphics/RuntimeShader
- https://developer.android.com/reference/android/view/Surface
- https://developer.android.com/games/sdk/frame-pacing

## 5. Samsung Galaxy A56 / One UI requirements

Official A56 display: 1080 x 2340, Super AMOLED, up to 120 Hz. At 60 FPS, a full-screen pass evaluates approximately **151.6 million pixels per second**, *before* considering multi-pass simulation, composition and overdraw. This is a pixel throughput estimate, **not** a measured GPU cost or battery percentage.

Samsung technical spec: https://www.samsung.com/za/smartphones/galaxy-a/galaxy-a56-5g-awesome-graphite-256gb-sm-a566bzkwafa/

### Compatibility test matrix

- Launcher preview and applied wallpaper, portrait and landscape where supported.
- A56 home screen 1 page vs multi-page offsets; swipe updates vs tilt behavior.
- Lock-screen compatibility on installed One UI build: home-only, lock-only and both where OS offers the options; verify rather than assume.
- Always On Display / screen off: absolutely no continuing animation work when not visible.
- Home screen icons and folders: readability and depth beneath overlays.
- Navigation pill, system clock, notification shade and wallpaper zoom transitions.
- Battery saver, low battery and thermally throttled conditions.
- Screen on/off repeatedly, live wallpaper switching, activity background/foreground, process recreation.
- Existing wallpaper contrast and Samsung color palette extraction when supplied.

Samsung docs: https://www.samsung.com/us/support/answer/ANS10001632/

## 6. Recommended engine architecture

```text
HALYN Motion studio (native Android UI)
         |
Preset repository / scene metadata / persisted options
         |
WallpaperService lifecycle + session state
         |
HALYN RenderCoordinator (render thread, scheduler, quality profile)
         |
         +---- AGSL SceneRenderer (existing 6 + simple shader scenes)
         |
         +---- EGL / OpenGL ES SceneRenderer
                +---- FBO ping-pong fluid solver
                +---- Particle simulation
                +---- Layer compositor / depth masks
                +---- material / highlight / color grading
         |
Frame pacing + power/thermal adaptation
         |
Android wallpaper Surface
```

**Stateful renderer and surface integration are the highest technical risk**. Prototype EGL rendering inside an Android wallpaper surface, safely rebuild resources on surface loss, enforce one active frame pump and preserve existing AGSL branch as a fallback. Do not replace the existing six effects when introducing this backend.

### Scene API proposal

- `SceneDescriptor`: ID, name, icon, engine, parameters, power profile, minimal API.
- `SceneRenderer`: `onSurfaceAvailable`, `resize`, `onTouch`, `onOffset`, `onSensor`, `renderFrame`, `dispose`.
- `InputState`: low-pass gyroscope, local touch impulses, page offset and optional system context.
- `RenderPolicy`: target frame budget, resolution fraction, battery/thermal states and reduced-motion support.
- `PresetStore`: schema-versioned per-scene settings, restore/import/export once stabilized.

## 7. UX design strategy

HALYN should be a **small, focused studio**, not a marketplace. Recommended top-level structure:

- **Discover**: the six existing presets always visible, then curated new scenes in separate category rows.
- **Studio**: large wallpaper preview, compact mode switch, color picker, motion/interaction/performance sections, one persistent Apply action.
- **Library**: saved configurations, favorite styles, import/export when implemented.

Use a dark Material 3 aesthetic, restrained accent color, segmented pill controls, large previews, clear text contrast, touch target size >=48dp, properly labeled controls and an accessible Reduced Motion switch. Do not put permanent shader settings over the rendered wallpaper.

Offer three presets per advanced scene: **Calm**, **Balanced**, **Dynamic**, with expert settings collapsed by default. Each parameter needs a visible effect; hide controls that are meaningless to a particular scene.

## 8. Performance and energy budget

These are **engineering targets, not measured outcomes**:

- Baseline: **30 FPS when visible**; offer **60 FPS** if frame budget and temperature permit. Consider future 120 FPS only for very light scenes and after measured gains justify it.
- 30 FPS frame interval ~33.3 ms; 60 FPS ~16.7 ms. Optimize GPU time below target to allow compositor and system overhead.
- Maintain graceful degradation: decrease internal simulation resolution, bloom passes, particle count and FPS rather than throwing errors or hanging.
- Render **0 frames while not visible** (including screen off). Shut down sensor listeners in the same transition.
- Use capped simulation `dt`, drop/merge stale touch input, avoid catch-up spirals on resume.
- For fluid scenes: velocity/dye textures at ~25–50% display resolution to start, separately upscaled; tune on-device.
- Prefer fixed throttled quality modes rather than uncapped GPU utilization.
- Measure on the real A56: visual frame pacing, jank, sustained surface stability, device temperature and battery usage over comparable conditions.
- A dark theme is not proof of negligible battery use; animated full-screen GPU work is measurable and requires testing.

## 9. Risks and licensing

- **Runtime GPU variability:** Android AGSL support is API 33+, but syntactically successful compilation in CI does not validate all device GPU compilers or thermal performance.
- **Samsung launcher behavior:** actual touch events, wallpaper offsets and lock screen selection can vary by launcher/OS; test on-device.
- **Audio capture:** microphone/media capture must be consent-driven and media apps may disallow capture; avoid falsely marketing universal beat-reactivity.
- **Open source material:** PavelDoGreat WebGL Fluid Simulation is MIT-licensed and suitable for study/reimplementation with attribution. Doodle is GPLv3; avoid copying its GPL code into a closed-source app without fully satisfying reciprocal obligations.
- **Rendering safety:** never draw a blocking overlay or prevent phone interaction; HALYN should always be removable through ordinary wallpaper settings.
- **Distribution:** do not claim production-grade battery life, Samsung lock-screen compatibility, hardware performance or audio capture before tests confirm them.

Sources:
- https://github.com/PavelDoGreat/WebGL-Fluid-Simulation
- https://github.com/patzly/doodle-android
- https://developer.android.com/media/platform/av-capture
- https://developer.android.com/reference/android/graphics/RuntimeShader

## 10. Prioritized implementation roadmap

### Phase 0 — Stabilize the released v0.1, no visual replacement
1. Confirm each of six existing scenes actually renders on A56; preserve exact original appearances.
2. Verify Android wallpaper apply flow, app lifecycle, screen-off stop, Samsung app/launcher behavior and input.
3. Add noninvasive diagnostics for shader creation/render surface failures, only in debug builds.

**Acceptance:** six originals continue to load, scrolling home screen works, and switching off/display locks does not leave a running frame pump.

### Phase 1 — Quality and Samsung polish
1. Implement wallpaper colors reporting / palette notifications; add reduced-motion and power-saver behavior.
2. Add `onZoomChanged` response and true eased layer/page parallax for *new* scenes; keep the original six unaffected.
3. Replace 45 FPS with more sensible display-paced 30/60 choices, backed by real A56 measurements.
4. Redesign studio into focused preview, scene picker, essentials then advanced.

**Acceptance:** clean home screen icon contrast, usable controls, no significant jank at balanced settings; measured and documented A56 behavior.

### Phase 2 — Truly reactive engine
1. Add a second renderer backed by EGL/OpenGL ES and multiple FBO passes.
2. Launch **Fluid Bloom** with stateful velocity/dye + splat gestures.
3. Add **Magnetic Dust** simulation and **Silk Reactor** multi-layer materials.
4. Implement independent static previews for accessibility and low-power cases.

**Acceptance:** user can stop touching and still observe correctly decaying motion; all persistent GPU resources release on hidden/surface loss.

### Phase 3 — Unique HALYN differentiation
1. Layered user photo depth import (with user review of masks and crops).
2. Time-dependent scenes, scheduled scene playlists, palette blending.
3. Optional video/photo mode and exportable presets.
4. Explore music-aware behavior only after platform permission research/prototyping validates the supported apps and UX.

**Acceptance:** capabilities match honest descriptions on the user's actual Android build, and no obsolete legacy XR/CYN/HALO modules are imported.

## Decision

**Preserve the existing six as a "HALYN Originals" collection.** Focus the next iteration on quality rather than quantity: the right first new effect is **Fluid Bloom**, implemented as real stateful simulation—not a pre-rendered clip and not a touch-colored overlay. Its technical work will also unlock particles, ripples, ink, vortexes and interactive glass distortion.
