# HALYN Motion

## v0.31.0 — Real sourced artwork, no built-in wallpaper collection

The previous bundled scene gallery was rejected as visually unconvincing.
The **active wallpaper selection is now photo-only**. All procedural preset
entries have been removed from the studio navigation, including Fluid, Space
and Monolith. Legacy shader sources remain in the codebase temporarily for
compatibility but can no longer be selected through app preferences or the UI.
Existing personally imported/downloaded wallpaper files are **not deleted**.

HALYN now opens on six researched 4K–6K photographs hosted by Wikimedia Commons.
Each item is resolved live from the official imageinfo API, so the creator,
license and original download URL cannot be invented. The image pages were
checked individually; see [ARTWORK_SOURCES.md](ARTWORK_SOURCES.md).
Thumbnails and original files are fetched only when using the gallery.
An additional real Commons search remains available.

The studio is reduced to a large image preview, real-photo gallery, image
import, source attribution, a clock editor and optional foreground-depth
compositing. Photographs are described as **static**, not deceptively sold
as animated. Optional gyroscope parallax and the real-time clock remain.
Automatic subject segmentation is not included.

A successful APK build does not prove the visual results or source availability
on a Galaxy A56. Verify artwork crops, network reliability, clock placement,
depth masks and battery usage on the phone.

> Earlier versions and their wallpapers remain documented below as historical
> notes, not current catalogue entries.

---


## v0.30.0 — Minimal Depth Studio (development preview)

This version introduces three distinct directions in the currently focused HALYN gallery: existing Fluid Motion materials, the preserved interactive Space wallpaper, and new photographic Cinematic Depth plus a sculptural Minimal Architecture original.

### Real subject-over-clock compositing
- **Photo mode is selectable again.** v0.29's preset sanitizer unintentionally mapped the existing photo slot (ID 51) back to Fluid; HALYN now preserves it.
- The **Minimal Depth** clock renders large four-digit condensed numerals and a locally formatted date in neutral monochrome. The real foreground layer renders **after** the clock, hiding only the portions overlapped by a subject.
- Choose a photo from Android, or **browse 4K+ Wikimedia Commons** photography. Downloaded art retains its creator, source page, license and resolution in app preferences. The source and license are exposed from the editor, and remote content is fetched only on request.
- Draw a continuous outline around a subject using **Trace subject outline**. The outline is mapped into original-image coordinates; HALYN creates an anti-aliased transparent PNG cutout offline on a worker thread. Alternatively, import a matching full-frame transparent PNG/WebP from another editor.
- The foreground is stored offline and rendered with a small gyroscope parallax offset. Importing a new background invalidates an old cutout to prevent mismatched foregrounds. Enable/disable depth and adjust strength in the editor.
- Automatic image segmentation is **not included**. Complex hair and translucent objects need a professionally prepared transparent overlay; a single finger outline is not equivalent to AI matting.

### Original Minimal Architecture
**Monolith Light** (scene 182) is an original dark graphite sculpture with slow viewpoint and gyro movement. The primary shader uses an analytic ray/box intersection with surface lighting and deterministic grain, avoiding an expensive ray-marching loop. It has a geometry-specific Canvas fallback; shader compilation and screenshot appearance still require Android hardware validation.

The existing six Fluid materials and Star Window/Space are unchanged. No new permissions, continuous network activity, microphone access or camera capture is required.

### Verification scope
GitHub Actions should compile the APK and check the wiring of masking, clock layering, the new architecture scene and legacy presets. It **cannot** verify real subject edge quality, artwork taste, gyro feel, Samsung launcher cutout behavior, battery endurance or AGSL driver appearance. Test these on the Galaxy A56 before declaring visual quality complete.

---


## v0.26.0 — Aura / Cyber rendering replacement (quality repair)

The v0.25.0 Aura Battle / Cyber Systems collection used Android Canvas strokes, arcs and translucent flat gradients as its **primary** scene artwork. Although each preset had different motion curves, it did not provide the three-dimensional-feeling aura, filmic light or elegantly dense cyber surface the collection was supposed to deliver.

**Architecture change:** All 12 existing wallpapers (IDs **156–167**) now use their own small, lazy-compiled **AGSL GPU shader**. The materials no longer depend on the same handful of Canvas line and arc primitives: Aura Strike has a warped noisy energy plume with layered glow, Nova Clash has colliding volumetric fronts, Impact Veil uses translucent optical folds, Void Rush uses a depth-compressed spatial tunnel, Energy Break flows along an elastic light membrane, and Pulse Slash uses luminous curved optical filaments. Cipher Veil adds quiet glass panels and sparse scan traces, Ghost Trace uses a signal graph, Vector Breach renders diagonal refractive seams, Packet Drift draws curved packet paths, Zero Grid uses a technical scan field, and Signal Intrusion uses granular low-contrast interference.

The existing velocity-aware gesture detection and filtered tilt detection remain in place. The new GPU shader composites the actual swipe direction into brief optical slash trails or more subtle signal ripples rather than painting a pile of unshaded line segments over the image. A reusable filmic tone map and stable dither reduce washed-out light and gradient banding. Preset four-colour choices, intensity, flow, sensitivity and burst duration still work.

Each GPU scene is independently cached and failure-isolated. On systems without Android 13+ RuntimeShader hardware support, the old Canvas renderer remains an explicit **fallback only**. Every existing 121 procedural wallpaper ID, including Glass Orbit, the live Material X range and Bubble Garden, is retained. No new permissions or background processes are needed.

**Validation scope:** CI can compile Kotlin and the APK, and can verify uniform plumbing, scene coverage and fallback wiring. It cannot verify visual appearance or compile AGSL for the actual Samsung Galaxy A56 GPU driver. That visual check remains necessary; this update should not be described as visually proven until device testing.

## v0.25.0 — Aura Battle & Cyber Systems: 12 live wallpapers

HALYN adds **12 fully animated, touch-reactive wallpapers** without modifying any of the original 109 procedural presets or their existing saved per-wallpaper four-colour settings. This extends HALYN to **121 procedural wallpapers** (plus the imported photograph slot), using new IDs **156–167**.

### Anime battle energy (156–161)
| Scene | Distinct animated mechanism |
| --- | --- |
| Aura Strike | Flowing central aura, moving energy ribbons, floating particles, directional anime strike lines and a pressure wave when swiped fast |
| Nova Clash | Independently moving opposing luminous energy fronts with a collision seam and expanding ring |
| Impact Veil | Semi-transparent diagonal energy sheets with a quick slash and clean afterglow |
| Void Rush | Receding optical tunnel rings and spatial speed trails converging on a tilting vanishing point |
| Energy Break | Moving flexible membrane of light contours with a fast-gesture rupture |
| Pulse Slash | Independent filament paths with staggered speed-driven slash afterimages |

### Cyber Systems (162–167)
| Scene | Distinct animated mechanism |
| --- | --- |
| Cipher Veil | Matte optical glass panels, travelling signal packets, a scan line and a subtle optional notification pulse |
| Ghost Trace | Branching low-contrast network routes and travelling node signals that flare when swiped |
| Vector Breach | Diagonal optical seams, panel drift and a temporary movement-driven breach; charging adds restrained illumination |
| Packet Drift | Curved packet traffic, optional music-active density increase and gesture-proximate route deflection |
| Zero Grid | A thin technical grid, scanning wave and slow-moving highlighted cells |
| Signal Intrusion | Controlled fine signal bands, a narrow scan and brief velocity-triggered interference |

**Interactive gesture physics:** HALYN uses ACTION_DOWN/MOVE/UP/CANCEL with event timestamps, calculating touch speed in **density-independent pixels per second**. A swipe around 1,200 dp/s activates the directional impact engine after a short travel threshold; ordinary taps remain calm. The controls offer **Swipe sensitivity (0.5–2.0×)**, **Burst duration (0.5–1.8×)**, **Aura / signal strength** and **Animation flow**. Fast changes to the existing filtered tilt pose can activate a smaller impact when gyro motion is enabled. Active home-screen swipe detection depends on whether the device's launcher forwards MOVE events to the live wallpaper; the in-app studio supports them directly.

Each scene uses an original low-overhead Android Canvas renderer, not screenshots, static bitmaps or copied anime art. The visuals are black-first with controlled motion blur approximations, soft radial haze, precise strokes and no fake terminal dialogue, green falling code, skulls or four-spoke symbols. **Four-colour palettes** work throughout. Background rendering stops when the live wallpaper becomes hidden. No new microphone, notification-read, accessibility, screen-recording or network permissions were introduced. The optional signal response uses existing battery, Boolean music-active and opt-in notification timestamp sampling only. GitHub Actions validates APK compilation and structural coverage; appearance and Android launcher gesture forwarding still require an actual Galaxy A56 test.

## v0.24.0 — Bubble Garden interactive live wallpaper

**Bubble Garden**, preset **155**, adds eleven carefully positioned translucent bubbles with soft glass reflections and nearly fixed positions. They are not procedurally rearranged or continuously floating upward: their coordinates remain anchored, with only tiny shimmer and gentle breathing.

**Drag-to-pop:** With wallpaper touch interaction enabled, place a finger on a bubble and drag it farther than approximately 53% of its radius. The bubble stretches toward the drag before popping into seven small droplets and a fading circular shockwave. The fragments vanish in under a second; the bubble takes a short rest and regrows smoothly in the **same exact position** after approximately 2.35 seconds. Releasing without reaching the threshold produces a gentle spring wobble, not an unwanted burst.

Interaction is handled via Android's regular wallpaper touch callbacks and the HALYN studio preview's gesture events (DOWN / MOVE / UP / CANCEL). Bubble drawings are powered by native Android Canvas and cached gradients, with no required microphone, screen capture, network access or new permissions. Some Android launchers may intercept swipe gestures before a live wallpaper receives them, so verify real home-screen drag behaviour on the Galaxy A56.

The existing four-colour editor applies to individual bubble rims and droplets, and the existing palettes and custom accent remain available. All **108 preceding procedural wallpapers** keep their previous IDs, and Bubble Garden becomes procedural wallpaper **109**.

## v0.23.0 — Glass Orbit Collection & real Material X live motion

**Glass Orbit remains intact.** Ten new glass-ring wallpapers are added at fixed preset IDs **145–154**, after the earlier eight Material X scenes (137–144), keeping every older saved scene ID stable.

| New wallpaper | Distinct mechanic |
| --- | --- |
| Orbital Lens | Tilt steers a double-layer refractive lens |
| Refractive Orbit | Independently moving refractive lens curvature |
| Twin Glass Rings | Two transparent rings counter-orbit and exchange highlights |
| Caustic Ring | Charging brightens moving liquid-like glints |
| Eclipse Lens | Foreground glass disc eclipses another ring, plus optional notification pulse |
| Prism Orbit | Two tilted optical orbits intersect at different rates |
| Crystal Orbit | Touch pushes an optical glint across crystal-like rims |
| Orbital Bloom | Smooth expanding and contracting glass shell |
| Glass Nebula Ring | Translucent lens mist responds to local day phase |
| Luminous Orbit | Music-playback-active status brightens a circulating light arc |

Every Glass Orbit Collection wallpaper has a separate short AGSL program and its own geometry, with a scene-specific moving Canvas ring fallback if a GPU driver rejects its shader. No four-spoke or swastika-like iconography and no rejected names were introduced. All ten work without microphone access, screen recording or screen sharing. The notification mechanic uses the existing optional notification listener and stores no message content.

**Material X motion rework:** all eight existing minimalist wallpapers now use visibly larger, constant movement cycles: the paper sun travels and each horizon moves, arches open independently, fold surfaces slide, pebbles move on individual tracks, a ribbon's Bezier geometry flows, the dunes rise and fall, frame insets translate and breathe, and ceramic bowls oscillate. This corrects the previous almost-static look. Material X and Glass Orbit gallery previews are also live at a battery-conscious ~12 fps while visible, instead of being frozen at a fixed time.

**All 4-color customizations and preexisting original wallpapers remain**. This release contains **108 procedural scenes** (28 original + 30 earlier optical + 12 reactive + 20 brand-inspired + 8 Material X + 10 Glass Orbit family). The user photo slot is separate. The project builds through GitHub Actions; on-device GPU and animation quality must still be verified on the target Galaxy A56.

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
