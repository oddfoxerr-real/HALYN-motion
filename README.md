# HALYN Motion

Minimalist native Android live wallpaper studio featuring **51 original live wallpaper scenes plus a personal image option** and **5 real-time clock styles**, including four layered mountain landscapes.

## v0.8.0 — Subject-led poster collection

HALYN's collection now includes **51 built-in wallpapers**, with 30 new, original subject-based scenes rather than more anonymous geometry.

- **Botanical posters:** Blue Rose, Red Anemone, Sunflower Hour, Lunar Lotus and Sakura Drift.
- **Road & speed:** Sunset GT, Midnight Rider, Coastal Drive, Neon Circuit and Desert Runner.
- **Quiet worlds:** Alone by the Sea, Foggy Pines, Peach Horizon, Deep Blue and Ember Forest.
- **Illustrated creatures:** Night Cat, Little Fox, Moon Owl, Cozy Panda and Shadow Rabbit.
- **City after dark:** Quiet Street, Streetlamp Rain, Skyline Blue, Lantern Alley and Rooftop Glow.
- **Places & ruins:** Mountain Cabin, Island Gate, Desert Arch, Last Lighthouse and Temple Sunrise.

The poster artwork uses lightweight Canvas illustration, subject-specific environments and gentle motion. Each foreground is drawn **after** the live clock, allowing the clock to partly sit behind a rose, car, creature or structure. The existing five clock styles, font selector, gyro controls, and on-home-screen positioning mode continue to work.

**My Image** is one additional gallery option: tap **Choose an image**, pick a photo or artwork using Android's system file picker, and HALYN imports it into private on-device storage. Import supports up to 24 MB and produces a downsampled WebP for lightweight wallpaper use. The image does not leave your phone, is not stored in the GitHub repository and is not shared with any server. Gyro pans the image only inside its safe crop margins so no black borders appear. Your selectable clock is rendered over the image.

The poster scenes deliberately **do not reproduce branded characters** from example galleries. To use character art you own or are authorized to use, choose **My Image**.

## v0.7.1 — Gyro & mountain stability

- **Motion that responds to your phone:** Non-Star Window scenes now use a dedicated fused rotation-vector sensor pipeline with per-activation neutral calibration, an anti-jitter deadzone, smooth damped response, hard travel limits and sensor shutdown when hidden. This is distinct from Star Window's existing space tracking.
- **Four gyro modes:** Off, Soft, Dynamic and Space in **Motion → Gyro depth**, with an **intensity** slider. The mountain series adds a separate **Mountain depth** slider for near/far layer separation. Sensor-based movement works in the app preview and live wallpaper; launcher swipe movement remains an independent toggle.
- **Mountain rendering fixed:** Sky, stars, light source, distant ridge, middle ridge, mist and nearest ridge move at deliberately different depths. Extending the mountain polygons beyond the screen prevents empty vertical strips at the edges during tilt.
- **Clock masking preserved:** The same gyro pose is used before and after the clock is drawn, so the foreground cannot shear relative to its background.
- **Gallery and colour controls:** Mountain thumbnails now include the foreground ridge. Colour selection, Strength and Flow now produce a controlled visual change on the mountain scenes.
- **Less jank:** Scene-sized gradients and mountain geometry are cached instead of recreated on every frame, cutting avoidable allocations.
- **No change to Star Window's navigation, sky alignment or opt-in Tap to travel.**

**Verification:** Android CI validates APK compilation and package metadata. Actual gyro feel, GPU behaviour and overlay alignment still need testing on a Samsung device.

## What's new in v0.7 — Mountain Clock Studio

- **21 scenes:** Keeps all 13 original wallpapers and adds *Orbital Halo*, *Cosmic Rain*, *Plasma Loom* and *Eclipse Glass* (procedural artistic visuals), plus *Twilight Peaks*, *Moonlit Ridge*, *Alpine Mist* and *Ember Valley* (minimal mountain landscapes).
- **Clock behind mountains:** Mountain wallpapers render the sky and distant ridges first, then the clock, then the foreground mountain silhouette. This provides real partial clock occlusion as the clock moves.
- **Clock font selector:** Default, Rounded, Serif, Mono, Condensed, Bold, and locally imported .ttf/.otf fonts (3 MB size limit, validated before replacing an existing font).
- **Move clock on Home screen:** Turn on the placement toggle in Clock atelier. Android requests permission for **Draw over other apps** on first use. HALYN opens the actual launcher with a transparent **5% snap grid**. Drag anywhere to reposition the clock; tap the **X in the top-right corner** to exit. The grid disappears, the clock position remains saved, and a temporary foreground notification also provides Exit.
- **Permission lifecycle:** No overlay is created unless you start the editor; no persistent overlay survives editing. No image captures, location polling or network communication are needed for clock placement.
- **Reset placement:** Tapping Upper, Centre or Lower in Clock atelier disables the custom grid placement and reuses the chosen preset. The mountain scene's Centre default is intentionally placed near the front ridge.
- **Important:** A genuine launcher overlay requires user-granted Android overlay permission; the service uses Android 14+ `specialUse` foreground service declaration. Test Samsung launcher alignment/permissions on the phone. Debug builds are not Play Store production-signed.

## What's new in v0.6.2

**Tap to travel is now optional and switched off by default.** In **Star Window → Tap to travel**, turn it on to navigate by tapping the studio preview or your home-screen live wallpaper. Tap a planet or named star to approach it; tap empty space to warp forward. When disabled, Star Window ignores touch-based navigation and does not request home-screen touch events, preventing accidental travel while using your launcher.

The **Warp one light-year**, **Explore a destination**, and **Return to the Solar System** buttons work regardless of the toggle. Device tilt and motion-based sky viewing are unchanged. The independent **Touch interaction** option under Preferences continues to control the other 12 GPU scenes, not Star Window navigation.

Existing installations with no saved Star Window touch preference will default to off. The preference is remembered across launches.

## What's new in v0.6

- **Warp fixed visually and functionally:** A travel tunnel with moving star streaks, a central portal ring, progress animation, one-light-year displacement in J2000 coordinates, distance HUD, and a visible arrival pulse. The app's Warp action now also signals the live wallpaper engine instead of affecting only its preview.
- **New space atmospheres:** Survey (astronomy only), Nebula (animated luminous cloud field), Expedition (cinematic nebula, ringed artistic planet after traveling, brighter warp effects). Fictional worlds are **clearly marked as procedural**, not represented as NASA discoveries.
- **Location-aware sky:** Opt-in foreground GPS/network location sync, approximate location supported, local-only cached coordinates, local sidereal time plus compass/magnetic declination for astronomical orientation. GPS is **not** used for indoor movement tracking or polled continually by the wallpaper.
- **Existing content preserved:** All 13 wallpapers, all original wallpaper styles, all five clocks, original palettes, and the HSV colour picker.

### How to use the new Star Window

1. Choose **Star Window** in the New Collection, then **Expedition** under Visual Atmosphere.
2. Tap **Warp one light-year** in the studio (always works when the app is open). To tap the wallpaper to travel, enable **Tap to travel** under Star Window first (off by default); launcher support varies.
3. You'll see an animated hyperspace tunnel, distance-from-origin label and warp-complete effect. **Return to the Solar System** resets the virtual camera.
4. For the *real sky above your location*, choose **Sync sky location** and grant approximate or precise location. This uses the saved coordinates and Earth's rotation, not live GPS polling. Warping switches back into exploration mode.

**Reality check:** This is an Android procedural *live wallpaper*, not a 3D game with terrain generation or walkable planets. The cinematic space atmosphere is original procedural art alongside the existing genuine Yale/Hipparcos star and approximate JPL planet coordinates.

## Previous v0.5 changes

- **Six preserved HALYN Originals:** Ion Veil, Liquid Chrome, Nebula Drift, Electric Silk, Solar Bloom and Prism Tide.
- **Six additional scenes:** Silk Reactor, Glass Orbit, Magnetic Dust, Midnight Horizon, Chromatic Flow and Circuit Pulse.
- **Star Window (v0.5):** Slow, weightless accelerometer-driven movement and rotation-anchored viewing. The camera starts above the actual JPL approximate solar system, shows **9,096 real Yale Bright Star Catalogue entries** with Hipparcos parallax distances where available, 150 real constellation strokes, and the Sun and eight planets computed for the current date (JPL approximate orbital elements, valid 1800–2050).
- **Tap to warp:** In the preview, a short tap animates one light-year forward over 2.6 seconds. On the installed Home screen, tap-to-warp works only if the launcher forwards wallpaper touch events; an explicit Warp button in the app always provides access. Return to the Solar System resets distance and motion.
- **Sensor-stutter masking:** Rejects implausible acceleration/rotation spikes, smooths pose with a continuous filter, predicts only brief (<85 ms) sensor gaps, clamps missed-frame animation time, and uses damped momentum to avoid fast, jittery motion.
- **Clock Atelier:** Digital, Stacked, Mono, Analogue and Orbit clock faces, directly rendered on the live wallpaper and live preview (not a separate widget).
- **Clock options:** Show/hide, Upper/Centre/Lower position, 12h or 24h format, date, seconds indicator and tint to the custom wallpaper colour.
- **Precision colour editor:** HSV canvas, hue rail, arbitrary HEX colours and five original palettes.
- True Android WallpaperService; GPU procedural rendering, no Unity, ads, external PC or network permission.
- Android 13+; hardware performance still requires real-device verification.

## Download

[Latest releases](https://github.com/oddfoxerr-real/HALYN-motion/releases)

## Architecture

- `SpatialTracker.kt`: gravity-free acceleration transformed to world axes, damped integrated movement, orientation sensor, lifecycle and recenter. Inertial drift prevents exact room-scale navigation.
- `StarWindowRenderer.kt`: real-star Canvas renderer in 3D astronomical coordinates, with approximate planet positions and constellation paths.
- `SpaceEffectsRenderer.kt`: optional nebula AGSL, artistic worlds, warp tunnel, progress and arrival overlays.
- `SkyLocationController.kt`: user-initiated foreground GPS/network location capture, permission and local cache.
- `SkyCoordinates.kt`: local sidereal time and geomagnetic true-north correction for aligning the sky.
- `MainActivity.kt`: native minimalist wallpaper studio and Clock Atelier UI.
- `ClockOverlayRenderer.kt`: 5 live-time clock face renderers on a hardware-accelerated Android Canvas.
- `HalynRenderer.kt`: 12 procedural AGSL GPU wallpaper scenes and preferences.
- `HalynWallpaperService.kt`: Android live wallpaper service that draws wallpaper and clock in the same frame and stops when hidden.
- `ColorPickerDialog.kt`: HSV + HEX colour editor.

### Important engineering distinctions

The Star Window is an estimated inertial navigation effect: accelerometers cannot reliably recover absolute travel distance. Warp moves the virtual view one light-year mathematically; it is a rendering interaction, not physical tracking. Star coordinates and available distances originate from Yale/Hipparcos, rather than NASA; JPL supplies the planetary elements. Unknown star distances are drawn as remote directions, never invented numeric distances. Planetary marker sizes are enlarged for visibility, while their heliocentric spatial coordinates retain approximate JPL scale. The new Glass Orbit, Magnetic Dust and Chromatic Flow scenes are procedural shader effects. Chromatic Flow is **not a persistent fluid physics solver**; adding a multi-pass fluid simulation is future work. The original six scene formulas remain unchanged. Samsung launcher placement, thermal performance, Always On Display and lock-screen behaviour must be tested on the actual phone.


## Astronomy data and attribution

- **Planetary position model:** NASA/JPL SSD [Approximate Positions of the Planets](https://ssd.jpl.nasa.gov/planets/approx_pos.html), Table 1, J2000 approximate orbital elements and rates valid 1800–2050. Earth uses the Earth–Moon barycenter approximation. These are *not* real-time Horizons high-precision vectors.
- **Stars:** [Yale Bright Star Catalogue, 5th revised edition](http://tdc-www.harvard.edu/catalogs/bsc5.html), J2000 RA/Dec and visual magnitudes, ESA [Hipparcos parallaxes](https://cdsarc.cds.unistra.fr/viz-bin/cat/I/239) where reliable, and IAU proper names. Offline facts compiled by [isene/starmap](https://github.com/isene/starmap/blob/master/data/README.md); the original data are public domain. Dataset is **9,096 bright stars**, not every star in the Galaxy.
- **Constellation lines:** [d3-celestial](https://github.com/ofrohn/d3-celestial), copyright 2015–2020 Olaf Frohn, licensed BSD-3-Clause; its copyright, permission and disclaimer are retained in `docs/CONSTELLATION-LICENSE.md`. These are sky-chart line segments, not physical structures in space.
- In **Explore** mode, phone orientation is calibrated relative to the initial screen direction. In **Location Sky** mode, a user-requested approximate/precise foreground location fix, sidereal time and compass sensor align view to the local sky (approximately). No camera or background-location permission is used; location is stored locally and can be disabled.
- The Solar System and near stars occupy wildly different real-world scales. Correct mathematical coordinates are used, but Solar System body markers are deliberately magnified for visibility. Only the eight major planets and Sun are modelled; this is not a complete asteroid/moon catalogue.
