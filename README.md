# HALYN Motion

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
