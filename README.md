# HALYN Motion

Minimalist native Android live wallpaper studio featuring **13 animated GPU wallpaper scenes** and **5 real-time clock styles**.

## What's new in v0.5

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
- `StarWindowRenderer.kt`: fixed-world spherical starfield GPU shader with depth-dependent parallax.
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
- Phone orientation is **calibrated relative to the initial screen direction**, not geolocated or astronomically aligned to your true local sky. No camera or location permission is used.
- The Solar System and near stars occupy wildly different real-world scales. Correct mathematical coordinates are used, but Solar System body markers are deliberately magnified for visibility. Only the eight major planets and Sun are modelled; this is not a complete asteroid/moon catalogue.
