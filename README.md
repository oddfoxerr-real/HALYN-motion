# HALYN Motion

## v0.22.0 — Four independent colours for every wallpaper + Material X minimalism

**Four-colour palettes:** Every procedural wallpaper, Mountain landscape, Star Window and imported 4K photo now supports four editable colour swatches. Select a wallpaper, enable **Four-colour palette** in the Colour panel, then tap colour 1–4 to use the existing HEX/HSV picker. Each preset persists its own four opaque colour values in private shared preferences, with no upper limit on customization slots across the installed collection. Disabling the option restores the scene's previous native palette.

AGSL-rendered HALYN Originals and the 62 original-style wallpapers blend all four chosen colours spatially into their actual shader emissions and soft ambient light. On non-AGSL wallpapers (Star Window, photograph and mountains), the four colours create a low-opacity gradient tint so the artwork remains visible. Wallpaper previews, gallery cards and the running wallpaper share the same per-scene settings. The Material X scenes use the four selected shades as the base background, midground, accent and foreground.

**Material X · Minimal** adds eight independently animated original vector wallpapers: **Paper Horizon, Arc Stack, Soft Fold, Pebble Lines, Ribbon Cutout, Mono Dunes, Orbit Insets and Ceramic Waves**. Their smooth curves, matte material layers, sculpted inset shapes and subtle depth use Android Canvas rather than importing static wallpaper images or copying any proprietary Material visual assets. These scenes are lightweight and use no new system permissions. The existing HALYN visuals and scene IDs are preserved.

Total: **98 selectable procedural wallpapers**, including the original 28, previous 30+12+20 additions, and 8 Material X scenes. Photograph imports remain a separate category. GitHub Actions validates compilation and package integrity; device-side visual quality still needs inspection on the Samsung Galaxy A56.

## v0.21.0 — Original Apple- and Samsung-inspired wallpaper collections

HALYN now contains **90 original procedural wallpapers**: the existing 28 classic effects, 30 earlier original-style extras, 12 reactive originals, plus **20 new device-inspired designs**. None of the existing preset IDs or names changed.

**Apple-inspired originals (10):** Satin Tide, Frosted Petals, Liquid Pearl, Pastel Canyon, Aurora Fold, Glass Tunnels, Velvet Horizon, Opaline Rings, Prism Ribbon, Soft Eclipse. Deliberately soft, satin-like, glassy and layered, with brighter diffuse reflections and translucent color flow. The shader geometries include pearl refraction, nested opaline rings, sculpted canyon glass, ribbon braids, frosted petals and atmospheric horizons.

**Samsung-inspired originals (10):** One Pulse, Infinity Bloom, AMOLED Current, Galaxy Orbit, Dynamic Fold, Edge Aurora, Prism Cascade, Signal Halo, Electric Canvas, Midnight Wave. Built for AMOLED contrast: predominantly black backgrounds, luminous electric filaments, dynamic folds, curved edge auroras, infinity-like orbiting forms and deeper optical highlights.

Every wallpaper contains its **own original animation equation** in a distinct, lazily compiled AGSL shader program; these aren't hue-shifted copies of previous wallpapers. Several use existing privacy-conscious device signals: gyro/tilt, charging, time of day, music playing (not captured audio), touch, optional notification timestamps and silent mode. Most have independent motion without any special permission. Android's regular GPU Canvas renderer is used, with the previous per-scene fallback protection maintained.

The gallery offers two separate collections in 4 horizontally scrollable rails. All existing colors, shader quality, performance settings, clock features, imported fonts and photographic backgrounds remain available.

The new effects are *original style interpretations only* — no Apple or Samsung wallpaper assets, logos, proprietary illustrations or claims of affiliation are included. CI verifies Android compilation, APK packaging and shader/preset counts. Actual AGSL appearance still requires a device test on the Galaxy A56.

## v0.20.1 — Original-style shader rendering repair

The newer gallery could display identical dark blue-green gradient tiles when Android rejected the combined 42-variant AGSL program. The previous implementation cached a global shader failure and substituted the same background for every card.

The renderer now **builds each scene as an independent AGSL program**, retains a small per-scene shader cache for scrolling, and isolates failures by preset. The original shader formulas and visual style remain in place. When a device driver still rejects an individual program, a distinct animated Canvas silhouette replaces the featureless gradient.

No screen capture, microphone or accessibility permission is necessary. GitHub Actions verifies Android compilation; actual scene appearance still needs testing on the Galaxy A56.

## v0.20.0 — 12 reactive originals, no screen capture

Twelve brand-new original-style shaders extend the 28 original effects and the previous 30 extra wallpapers, preserving their IDs, appearance, palette behavior, and shader pipeline. New scenes: Resonant Veil, Melody Vortex, Harmonic Ladder, Charge Current, Battery Halo, Solar Dial, Nightfall Canopy, Notification Ripple, Signal Wings, Gyro Filaments, Touch Lantern, and Quiet Sanctuary.

Each scene has a different functional mechanic:

- **Music & motion**: reacts to playback being active (not beat, waveform, title, or volume analysis). Uses Android AudioManager; microphone and playback-capture permissions are not requested.
- **Battery & charging**: charging state powers moving light, while battery level controls the halo geometry.
- **Time**: an orbital day clock and nightfall-driven aurora use the phone's local time.
- **Notifications**: optional Android Notification Listener access drives two short event animations. HALYN stores only the event timestamp; never notification text, sender, app identity, or IDs. All other wallpaper mechanics work without notification access.
- **Gyro, touch, ringer**: live tilt steering, touch bloom, and silent/vibrate-responsive ripples.

No screen recording, MediaProjection, microphone, root, accessibility service, or network access is required by these smart reactions. Rendering and signal polling stop when the wallpaper is hidden. The notification listener is opt-in through the Preferences shortcut in HALYN's studio.

## v0.19.0 — Unlimited clock-font library and working presets

HALYN no longer imposes a **3 MB file-size ceiling** or replaces the previous imported font each time. You can store any number of valid TrueType/OpenType font files (`.ttf`, `.otf`, `.ttc`) up to the actual free storage and file-system constraints of your Android device. Files are imported in a streaming manner with a bounded RAM buffer, validated by Android's font loader, and kept in app-private storage. They are content-addressed with SHA-256 to avoid duplicate copies. Previous single custom-font imports are migrated automatically.

The clock editor now displays six **visually differentiated preset fonts** (Default, Casual, Serif, Monospace, Condensed, Heavy), rendered in their actual chosen font. Selecting a preset or imported font changes the live clock, and selecting a font while the clock is switched off automatically opens the Digital clock so the effect is visible. The Analogue clock shows hour numerals when a non-default font is selected. The renderer fits longer clock text to the screen width.

Tap **Imported fonts** to browse and preview the local font library. Long-press an entry to remove its copy from HALYN, or choose **Add fonts** to import multiple files through Android's document picker. No font requires Internet or root access, and the library survives app updates.

The original 28 effects, added 30 shaders, 4K wallpaper gallery, clock positioning and all existing motion controls remain untouched.


## v0.18.0 — Original HALYN look, 30 new shapes

The previous release's new effects used an independent surface-normal/material shader and therefore looked more like illuminated objects than the original HALYN designs. This version deliberately reuses **the actual original AGSL shading pipeline**: black/ink backgrounds, the same five colour palettes, companion highlights, soft bloom, emissive contours and filmic tone mapping. Only the 30 new effects' geometry, waveform patterns, silhouettes and motion are new.

All **28 original effect definitions and preset IDs stay unchanged**. The 30 new shaders appear in their own gallery as five sets of six:
- **Fluid ribbons:** Ripple Cadence, Helix Undertow, Viscous Portal, Mirror Interference, Pressure Lines, Current Fold.
- **Optical light:** Glass Caustics, Refractive Helix, Prism Diffusion, Phase Lens, Lumen Chamber, Chroma Shell.
- **Cosmic formations:** Accretion Band, Void Echo, Distant Bloom, Event Ribbons, Parallax Nebula, Cosmic Aperture.
- **Energy fields:** Ion Threads, Plasma Convergence, Flux Cascade, Electric Lacing, Arc Reservoir, Magnetic Pulse.
- **Geometry & motion:** Gyroid Passage, Elastic Membrane, Sonic Contours, Folded Continuum, Morphing Torus, Infinite Weave.

Each scene is implemented with a unique time-varying procedural formula, creating different narrow trails, flowing contours, orbital curves, caustics, resonance and luminous edge behaviour. The 30 additions share the *original* optical renderer by design, so they belong to the same visual family rather than constituting a different surface/material renderer. Unused 3D material-depth controls were removed from the UI; strength, speed, colour, detail, clock and gyro remain.

A continuity check in GitHub Actions verifies both the original shader source and the new original-style optical composition are present. Android CI checks compilation, not whether the shader runtime compiles or looks right on the Galaxy A56.


## v0.17.0 — Independent effect mechanics

The 30 new abstract wallpapers now have **30 independently authored animation and interaction functions**, not just differing surface equations evaluated under a universal animation. Each function defines spatial displacement, time-phase distortion, its own emission mechanic and tap response. Scene playback rates vary based on the intended motion.

Five shader response models now distinguish liquids (broad specular flow), optics (focusing/caustic refraction), space (multilayer scattering), electricity (energy transfer and charge bursts), and geometric forms (precision highlights and edge illumination). These material responses are applied only to the 30 additions. The **28 original shaders are unchanged** and remain in their original gallery sections.

Each selected new effect displays a short explanation of its mechanism below the wallpaper preview. Touch events decay over 2.4 seconds and propagate through the effect's own shader logic, rather than adding an always-on circle. Existing touch toggle, gyro parallax, gallery, clock and wallpapers remain compatible.

### Example motion mechanisms

- **Ripple Cadence:** phase-cancelling expanding waves from two sources
- **Helix Undertow:** counter-rotating strands trading depth
- **Viscous Portal:** nonlinear inward vortex advection
- **Glass Caustics:** dynamic light focusing through moving lenses
- **Accretion Band:** radius-dependent orbital angular velocity
- **Parallax Nebula:** three independent layers drifting at different velocities
- **Ion Threads:** branching filaments with changing junction charge
- **Arc Reservoir:** cyclical charge, discharge and ring recovery
- **Elastic Membrane:** damped wave propagation from touch impacts
- **Infinite Weave:** over-under crossing transitions of looped strands

This release uses Android Canvas/AGSL; it does **not** require or claim hardware RTX, OpenGL switching or Vulkan. The GitHub build checks Kotlin/APK packaging; on-device AGSL runtime compilation, aesthetics and thermals remain to be verified.


## v0.16.0 — Original shaders restored, 30 new abstract motion effects added

HALYN Motion now contains **28 original animated shader wallpapers** and **30 additional smooth abstract effects**, shown as separate gallery collections. The previous v0.15.0 update incorrectly removed the original 28 instead of adding wallpapers alongside them. This release restores their original effect definitions, preset IDs, render semantics and studio controls. Older saved original preset selections remain valid; the updated reader no longer redirects them to the new collection.

**Preserved original shaders:** Ion Veil, Liquid Chrome, Nebula Drift, Electric Silk, Solar Bloom, Prism Tide, Silk Reactor, Glass Orbit, Magnetic Dust, Midnight Horizon, Chromatic Flow, Circuit Pulse, Orbital Halo, Cosmic Rain, Plasma Loom, Eclipse Glass, Velvet Flux, Crystal Current, Neon Ink, Zenith Borealis, Quartz Ripple, Lumen Dusk, Flux Filament, Celestial Mist, Liquid Copper, Polar Halo, Prism Flow and Voltage Bloom.

### Thirty new motion scenes

The added scenes use HALYN's **smooth, luminous abstract motion aesthetic**, without duplicating the originals' individual shader formulas. Each new scene has a separately authored spatial field, silhouette, dynamic motion equation, material response, and lighting composition. The unwanted 0.15.0 material/architectural collection was replaced instead of being added on top.

**Fluid dynamics:** Ripple Cadence, Helix Undertow, Viscous Portal, Mirror Interference, Pressure Lines, Current Fold.

**Optical structures:** Glass Caustics, Refractive Helix, Prism Diffusion, Phase Lens, Lumen Chamber, Chroma Shell.

**Space dynamics:** Accretion Band, Void Echo, Distant Bloom, Event Ribbons, Parallax Nebula, Cosmic Aperture.

**Energy & motion:** Ion Threads, Plasma Convergence, Flux Cascade, Electric Lacing, Arc Reservoir, Magnetic Pulse.

**Geometric motion:** Gyroid Passage, Elastic Membrane, Sonic Contours, Folded Continuum, Morphing Torus, Infinite Weave.

No new pixel-art or low-resolution textures are used. Procedural noise is smoothly interpolated, and shading is evaluated at Canvas resolution with filmic tone mapping, surface-normal lighting and sub-LSB static dithering. All 30 newly added shaders remain independent of the old coloured SHADES materials renderer, which stays removed.

### Other features

The four minimalist mountain scenes and Star Window remain available, along with the licensed online 4K gallery, imported photos, gyro, touch settings, custom clock fonts, clock move mode, and live-clock overlay.

### Code

- `HalynRenderer.kt`: restored original AGSL effect renderer from the earlier HALYN revision, plus additional scene dispatch to `SmoothWallpaperRenderer`. Previous photo/mountain/star routing and original IDs are preserved.
- `SmoothWallpaperRenderer.kt`: 30 new scene-specific motion functions, dynamic shader compilation, six-program LRU and safe fallback.
- `MainActivity.kt`: originals and additions displayed as separate rails, with matching native preview and controls.
- `HalynWallpaperService.kt`: existing adaptive frame pacing, motion sensors and live wallpaper surface lifecycle remain unchanged.

### Installation

Android 13+ (API 33), package `com.oddf0x.halynmotion`. The GitHub release contains a debug-signed APK; CI verifies build and packaging, but shader output and thermal performance must still be tested on the actual phone.
