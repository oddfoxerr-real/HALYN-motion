package com.oddf0x.halynmotion

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock

/**
 * On-demand, foreground-only Android location capture.
 *
 * No background location permission, location service, network API, telemetry
 * or continuous GPS tracking. User taps Sync Sky and chooses precise or
 * approximate permission. The fix is stored locally for sidereal sky alignment.
 */
class SkyLocationController(
    private val activity: Activity,
    private val prefs: MotionPreferences,
    private val onStatus: (String) -> Unit,
    private val onSaved: () -> Unit
) {
    private val manager =
        activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var cancellation: CancellationSignal? = null

    fun sync() {
        val fine = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            activity.requestPermissions(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION
            )
            return
        }
        obtainLocation(fine)
    }

    fun handlePermissionResult(requestCode: Int) {
        if (requestCode != REQUEST_LOCATION) return
        val fine = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (fine || coarse) obtainLocation(fine)
        else onStatus("Location was not enabled. Your wallpaper remains fully usable.")
    }

    private fun obtainLocation(fineAllowed: Boolean) {
        cancel()
        onStatus("Finding your local sky…")
        val preferred = if (fineAllowed && isEnabled(LocationManager.GPS_PROVIDER)) {
            LocationManager.GPS_PROVIDER
        } else if (isEnabled(LocationManager.NETWORK_PROVIDER)) {
            LocationManager.NETWORK_PROVIDER
        } else if (isEnabled(LocationManager.FUSED_PROVIDER)) {
            LocationManager.FUSED_PROVIDER
        } else null

        if (preferred == null) {
            onStatus("Turn on Location in phone settings, then tap Sync Sky again.")
            return
        }
        capture(preferred, allowFallback = true)
    }

    private fun capture(provider: String, allowFallback: Boolean) {
        val signal = CancellationSignal()
        cancellation = signal
        try {
            manager.getCurrentLocation(provider, signal, activity.mainExecutor) { location ->
                if (signal.isCanceled) return@getCurrentLocation
                if (location == null && allowFallback &&
                    provider != LocationManager.NETWORK_PROVIDER &&
                    isEnabled(LocationManager.NETWORK_PROVIDER)) {
                    capture(LocationManager.NETWORK_PROVIDER, allowFallback = false)
                } else if (location != null) {
                    save(location)
                } else {
                    onStatus("No location fix available. Try again in an open area.")
                }
            }
        } catch (_: SecurityException) {
            onStatus("Location permission changed. Tap Sync Sky to allow it again.")
        } catch (_: IllegalArgumentException) {
            onStatus("The selected location provider is unavailable.")
        } catch (_: Exception) {
            onStatus("Sky sync was interrupted. Try again.")
        }
    }

    private fun save(location: Location) {
        val lat = location.latitude
        val lon = location.longitude
        if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            onStatus("Location returned invalid coordinates.")
            return
        }
        prefs.saveSkyFix(lat, lon, System.currentTimeMillis())
        val accuracy = if (location.hasAccuracy()) {
            val estimate = location.accuracy.toInt().coerceAtLeast(1)
            " · ±${estimate} m"
        } else ""
        onStatus("Sky aligned to your location$accuracy · Stored on this device")
        onSaved()
    }

    private fun isEnabled(provider: String): Boolean = try {
        manager.isProviderEnabled(provider)
    } catch (_: Exception) {
        false
    }

    fun cancel() {
        cancellation?.cancel()
        cancellation = null
    }

    companion object {
        const val REQUEST_LOCATION = 4162
    }
}
