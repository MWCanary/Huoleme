package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.Locale

object LocationHelper {
    @Volatile
    var lastCachedLocation: Location? = null

    @Volatile
    var lastCachedLocationString: String = "无位置"

    fun checkLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun formatLocation(loc: Location?): String {
        if (loc == null) return "无位置"
        val lat = String.format(Locale.US, "%.4f", loc.latitude)
        val lon = String.format(Locale.US, "%.4f", loc.longitude)
        return "经度: $lon, 纬度: $lat"
    }

    /**
     * Actively refresh and warm up location for Android 17 / API 30+ / older versions
     */
    fun refreshLocation(context: Context) {
        if (!checkLocationPermission(context)) return
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
            
            // 1. Check last known locations across all providers
            val providers = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    if (lm.hasProvider(LocationManager.FUSED_PROVIDER)) {
                        providers.add(LocationManager.FUSED_PROVIDER)
                    }
                } catch (e: Throwable) {}
            }
            providers.add(LocationManager.GPS_PROVIDER)
            providers.add(LocationManager.NETWORK_PROVIDER)
            providers.add(LocationManager.PASSIVE_PROVIDER)

            var bestLoc: Location? = lastCachedLocation
            for (p in providers) {
                try {
                    if (lm.isProviderEnabled(p)) {
                        val loc = lm.getLastKnownLocation(p)
                        if (loc != null) {
                            if (bestLoc == null || loc.time > bestLoc.time || (loc.accuracy > 0 && loc.accuracy < bestLoc.accuracy)) {
                                bestLoc = loc
                            }
                        }
                    }
                } catch (e: Throwable) {}
            }
            if (bestLoc != null) {
                lastCachedLocation = bestLoc
                lastCachedLocationString = formatLocation(bestLoc)
            }

            // 2. Android 11+ / 14+ / 17 (API 30+) getCurrentLocation API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val activeProvider = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && providers.contains(LocationManager.FUSED_PROVIDER) && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
                    lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    else -> LocationManager.PASSIVE_PROVIDER
                }
                try {
                    val cancelSignal = CancellationSignal()
                    lm.getCurrentLocation(
                        activeProvider,
                        cancelSignal,
                        ContextCompat.getMainExecutor(context)
                    ) { loc ->
                        if (loc != null) {
                            lastCachedLocation = loc
                            lastCachedLocationString = formatLocation(loc)
                        }
                    }
                } catch (e: Throwable) {}
            }

            // 3. Fallback one-shot listener for older APIs or if getCurrentLocation is slow
            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    lastCachedLocation = loc
                    lastCachedLocationString = formatLocation(loc)
                    try { lm.removeUpdates(this) } catch (e: Throwable) {}
                }
                override fun onStatusChanged(p: String?, s: Int, b: Bundle?) {}
                override fun onProviderEnabled(p: String) {}
                override fun onProviderDisabled(p: String) {}
            }

            for (p in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
                try {
                    if (lm.isProviderEnabled(p)) {
                        lm.requestLocationUpdates(p, 1000L, 1f, listener, Looper.getMainLooper())
                    }
                } catch (e: Throwable) {}
            }
        } catch (e: Throwable) {}
    }

    /**
     * Get best available location string
     */
    fun getLocationString(context: Context): String {
        if (!checkLocationPermission(context)) {
            return "无位置"
        }
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return if (lastCachedLocation != null) lastCachedLocationString else "无位置"

            var bestLoc: Location? = lastCachedLocation
            val providers = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    if (lm.hasProvider(LocationManager.FUSED_PROVIDER)) {
                        providers.add(LocationManager.FUSED_PROVIDER)
                    }
                } catch (e: Throwable) {}
            }
            providers.add(LocationManager.GPS_PROVIDER)
            providers.add(LocationManager.NETWORK_PROVIDER)
            providers.add(LocationManager.PASSIVE_PROVIDER)

            for (p in providers) {
                try {
                    if (lm.isProviderEnabled(p)) {
                        val loc = lm.getLastKnownLocation(p)
                        if (loc != null) {
                            if (bestLoc == null || loc.time > bestLoc.time || (loc.accuracy > 0 && loc.accuracy < bestLoc.accuracy)) {
                                bestLoc = loc
                            }
                        }
                    }
                } catch (e: Throwable) {}
            }

            if (bestLoc == null) {
                for (p in lm.allProviders) {
                    try {
                        val loc = lm.getLastKnownLocation(p)
                        if (loc != null && (bestLoc == null || loc.time > bestLoc.time)) {
                            bestLoc = loc
                        }
                    } catch (e: Throwable) {}
                }
            }

            // Also trigger background refresh
            refreshLocation(context)

            if (bestLoc != null) {
                lastCachedLocation = bestLoc
                lastCachedLocationString = formatLocation(bestLoc)
                return lastCachedLocationString
            }
            return lastCachedLocationString
        } catch (e: Throwable) {
            return if (lastCachedLocation != null) lastCachedLocationString else "无位置"
        }
    }
}
