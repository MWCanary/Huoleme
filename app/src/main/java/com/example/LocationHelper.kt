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
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.Locale

object LocationHelper {
    private const val LOCATION_FRESHNESS_MS = 2 * 60 * 1000L // 2 minutes
    private const val REFRESH_COOLDOWN_MS = 60 * 1000L // 1 minute
    private const val LOCATION_TIMEOUT_MS = 10 * 1000L // 10 seconds max active radio time

    @Volatile
    var lastCachedLocation: Location? = null

    @Volatile
    var lastCachedLocationString: String = "无位置"

    @Volatile
    private var lastRefreshRequestTimeMs: Long = 0L

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeCancellationSignal: CancellationSignal? = null
    private var activeListener: LocationListener? = null
    private var activeTimeoutRunnable: Runnable? = null

    fun checkLocationPermission(context: Context): Boolean {
        val appContext = context.applicationContext
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
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

    private fun isLocationFresh(loc: Location?): Boolean {
        if (loc == null) return false
        return (System.currentTimeMillis() - loc.time) in 0L..LOCATION_FRESHNESS_MS
    }

    private fun cancelPendingRequests(lm: LocationManager?) {
        synchronized(lock) {
            activeTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            activeTimeoutRunnable = null
            try {
                activeCancellationSignal?.cancel()
            } catch (_: Throwable) {}
            activeCancellationSignal = null
            activeListener?.let { listener ->
                try {
                    lm?.removeUpdates(listener)
                } catch (_: Throwable) {}
            }
            activeListener = null
        }
    }

    private fun collectBestLastKnownLocation(lm: LocationManager): Location? {
        val providers = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (lm.hasProvider(LocationManager.FUSED_PROVIDER)) {
                    providers.add(LocationManager.FUSED_PROVIDER)
                }
            } catch (_: Throwable) {}
        }
        providers.add(LocationManager.NETWORK_PROVIDER)
        providers.add(LocationManager.GPS_PROVIDER)
        providers.add(LocationManager.PASSIVE_PROVIDER)

        var bestLoc: Location? = lastCachedLocation
        for (p in providers) {
            try {
                if (lm.isProviderEnabled(p)) {
                    val loc = lm.getLastKnownLocation(p)
                    if (loc != null) {
                        if (bestLoc == null || loc.time > bestLoc.time || (loc.accuracy > 0 && loc.accuracy < bestLoc.accuracy && loc.time >= bestLoc.time - LOCATION_FRESHNESS_MS)) {
                            bestLoc = loc
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        if (bestLoc == null) {
            for (p in lm.allProviders) {
                try {
                    val loc = lm.getLastKnownLocation(p)
                    if (loc != null && (bestLoc == null || loc.time > bestLoc.time)) {
                        bestLoc = loc
                    }
                } catch (_: Throwable) {}
            }
        }
        return bestLoc
    }

    /**
     * Actively refresh location with strict timeout and throttling to minimize battery and memory impact.
     */
    fun refreshLocation(context: Context) {
        val appContext = context.applicationContext
        if (!checkLocationPermission(appContext)) return
        try {
            val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return

            val bestLoc = collectBestLastKnownLocation(lm)
            if (bestLoc != null) {
                lastCachedLocation = bestLoc
                lastCachedLocationString = formatLocation(bestLoc)
            }

            val now = System.currentTimeMillis()
            if (isLocationFresh(bestLoc) || (now - lastRefreshRequestTimeMs) < REFRESH_COOLDOWN_MS) {
                return
            }
            lastRefreshRequestTimeMs = now

            cancelPendingRequests(lm)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val activeProvider = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lm.hasProvider(LocationManager.FUSED_PROVIDER) && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    else -> null
                } ?: return

                val cancelSignal = CancellationSignal()
                val timeoutRunnable = Runnable {
                    cancelPendingRequests(lm)
                }
                synchronized(lock) {
                    activeCancellationSignal = cancelSignal
                    activeTimeoutRunnable = timeoutRunnable
                }
                mainHandler.postDelayed(timeoutRunnable, LOCATION_TIMEOUT_MS)

                try {
                    lm.getCurrentLocation(
                        activeProvider,
                        cancelSignal,
                        ContextCompat.getMainExecutor(appContext)
                    ) { loc ->
                        if (loc != null) {
                            lastCachedLocation = loc
                            lastCachedLocationString = formatLocation(loc)
                        }
                        cancelPendingRequests(lm)
                    }
                } catch (_: Throwable) {
                    cancelPendingRequests(lm)
                }
            } else {
                val provider = when {
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    else -> null
                } ?: return

                val listener = object : LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        lastCachedLocation = loc
                        lastCachedLocationString = formatLocation(loc)
                        cancelPendingRequests(lm)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(p: String?, s: Int, b: Bundle?) {}
                    override fun onProviderEnabled(p: String) {}
                    override fun onProviderDisabled(p: String) {}
                }

                val timeoutRunnable = Runnable {
                    cancelPendingRequests(lm)
                }
                synchronized(lock) {
                    activeListener = listener
                    activeTimeoutRunnable = timeoutRunnable
                }
                mainHandler.postDelayed(timeoutRunnable, LOCATION_TIMEOUT_MS)

                try {
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                } catch (_: Throwable) {
                    cancelPendingRequests(lm)
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * Get best available location string.
     * Set allowActiveRefresh = false in background BroadcastReceivers to avoid waking GPS radio.
     */
    fun getLocationString(context: Context, allowActiveRefresh: Boolean = true): String {
        val appContext = context.applicationContext
        if (!checkLocationPermission(appContext)) {
            return "无位置"
        }
        try {
            val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return if (lastCachedLocation != null) lastCachedLocationString else "无位置"

            val bestLoc = collectBestLastKnownLocation(lm)
            if (bestLoc != null) {
                lastCachedLocation = bestLoc
                lastCachedLocationString = formatLocation(bestLoc)
            }

            if (allowActiveRefresh) {
                refreshLocation(appContext)
            }

            return lastCachedLocationString
        } catch (_: Throwable) {
            return if (lastCachedLocation != null) lastCachedLocationString else "无位置"
        }
    }
}
