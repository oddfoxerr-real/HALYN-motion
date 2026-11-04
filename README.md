# HALYN Motion

Minimalist native Android live wallpaper studio featuring **13 animated GPU wallpaper scenes** and **5 real-time clock styles**.

## What's new in v0.4

- **Six preserved HALYN Originals:** Ion Veil, Liquid Chrome, Nebula Drift, Electric Silk, Solar Bloom and Prism Tide.
- **Six additional scenes:** Silk Reactor, Glass Orbit, Magnetic Dust, Midnight Horizon, Chromatic Flow and Circuit Pulse.
- **New Star Window:** live 3D star sphere with three fixed-depth star layers; device rotation changes viewing direction while linear acceleration provides bounded virtual movement without a camera. Adjustable sensitivity, density and optional decorative constellation lines, plus manual recenter.
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

The added Star Window is an estimated inertial navigation effect: accelerometers cannot reliably recover absolute travel distance. The new Glass Orbit, Magnetic Dust and Chromatic Flow scenes are procedural shader effects. Chromatic Flow is **not a persistent fluid physics solver**; adding a multi-pass fluid simulation is future work. The original six scene formulas remain unchanged. Samsung launcher placement, thermal performance, Always On Display and lock-screen behaviour must be tested on the actual phone.

