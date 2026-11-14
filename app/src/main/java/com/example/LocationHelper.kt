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
    private const val REFRESH_COOLDOWN_MS = 60 * 1000L // 1 minute (only when a valid location is already cached)
    private const val LOCATION_TIMEOUT_MS = 10 * 1000L // 10 seconds max active radio time

    @Volatile
    var lastCachedLocation: Location? = null

    @Volatile
    var lastCachedLocationString: String = "无位置"

    @Volatile
    private var lastRefreshRequestTimeMs: Long = 0L

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRequestInFlight = false
    private val pendingCallbacks = mutableListOf<(String) -> Unit>()
    private val activeCancellationSignals = mutableListOf<CancellationSignal>()
    private val activeListeners = mutableListOf<LocationListener>()
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

    private fun hasFineLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context.applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun formatLocation(loc: Location?): String {
        if (loc == null) return "无位置"
        if (loc.latitude == 0.0 && loc.longitude == 0.0 && !loc.hasAccuracy()) return "无位置"
        val lat = String.format(Locale.US, "%.4f", loc.latitude)
        val lon = String.format(Locale.US, "%.4f", loc.longitude)
        return "经度: $lon, 纬度: $lat"
    }

    private fun isLocationFresh(loc: Location?): Boolean {
        if (loc == null) return false
        return (System.currentTimeMillis() - loc.time) in 0L..LOCATION_FRESHNESS_MS
    }

    private fun notifyCallbackOnMain(callback: ((String) -> Unit)?, result: String) {
        if (callback == null) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback(result)
        } else {
            mainHandler.post { callback(result) }
        }
    }

    private fun finishActiveRequest(lm: LocationManager?, resolvedLocation: Location?) {
        val callbacksToNotify: List<(String) -> Unit>
        val resultStr: String
        synchronized(lock) {
            if (!isRequestInFlight) return
            isRequestInFlight = false
            activeTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            activeTimeoutRunnable = null

            activeCancellationSignals.forEach { signal ->
                try {
                    signal.cancel()
                } catch (_: Throwable) {}
            }
            activeCancellationSignals.clear()

            activeListeners.forEach { listener ->
                try {
                    lm?.removeUpdates(listener)
                } catch (_: Throwable) {}
            }
            activeListeners.clear()

            if (resolvedLocation != null) {
                val formatted = formatLocation(resolvedLocation)
                if (formatted != "无位置") {
                    lastCachedLocation = resolvedLocation
                    lastCachedLocationString = formatted
                }
            }
            resultStr = lastCachedLocationString
            callbacksToNotify = pendingCallbacks.toList()
            pendingCallbacks.clear()
        }

        if (callbacksToNotify.isNotEmpty()) {
            mainHandler.post {
                callbacksToNotify.forEach { cb -> cb(resultStr) }
            }
        }
    }

    private fun collectBestLastKnownLocation(lm: LocationManager, hasFine: Boolean): Location? {
        val providers = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (lm.hasProvider(LocationManager.FUSED_PROVIDER)) {
                    providers.add(LocationManager.FUSED_PROVIDER)
                }
            } catch (_: Throwable) {}
        }
        providers.add(LocationManager.NETWORK_PROVIDER)
        if (hasFine) {
            providers.add(LocationManager.GPS_PROVIDER)
        }
        providers.add(LocationManager.PASSIVE_PROVIDER)

        var bestLoc: Location? = lastCachedLocation
        for (p in providers) {
            try {
                val loc = lm.getLastKnownLocation(p)
                if (loc != null && formatLocation(loc) != "无位置") {
                    if (bestLoc == null ||
                        loc.time > bestLoc.time ||
                        (loc.accuracy > 0 && loc.accuracy < bestLoc.accuracy && loc.time >= bestLoc.time - LOCATION_FRESHNESS_MS)
                    ) {
                        bestLoc = loc
                    }
                }
            } catch (_: Throwable) {}
        }

        if (bestLoc == null) {
            try {
                for (p in lm.allProviders) {
                    if (!hasFine && p == LocationManager.GPS_PROVIDER) continue
                    try {
                        val loc = lm.getLastKnownLocation(p)
                        if (loc != null && formatLocation(loc) != "无位置" && (bestLoc == null || loc.time > bestLoc.time)) {
                            bestLoc = loc
                        }
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}
        }
        return bestLoc
    }

    private fun collectEligibleActiveProviders(lm: LocationManager, hasFine: Boolean): List<String> {
        val result = linkedSetOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (lm.hasProvider(LocationManager.FUSED_PROVIDER) && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
                    result.add(LocationManager.FUSED_PROVIDER)
                }
            } catch (_: Throwable) {}
        }
        try {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                result.add(LocationManager.NETWORK_PROVIDER)
            }
        } catch (_: Throwable) {}
        if (hasFine) {
            try {
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    result.add(LocationManager.GPS_PROVIDER)
                }
            } catch (_: Throwable) {}
        }
        if (result.isEmpty()) {
            try {
                for (p in lm.allProviders) {
                    if (p == LocationManager.PASSIVE_PROVIDER) continue
                    if (!hasFine && p == LocationManager.GPS_PROVIDER) continue
                    try {
                        if (lm.isProviderEnabled(p)) {
                            result.add(p)
                        }
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}
        }
        return result.toList()
    }

    /**
     * Actively refresh location with multi-provider fallback (Fused/Network/GPS),
     * shared in-flight request deduplication, and strict timeout to save battery.
     */
    fun refreshLocation(context: Context, onResult: ((String) -> Unit)? = null) {
        val appContext = context.applicationContext
        if (!checkLocationPermission(appContext)) {
            notifyCallbackOnMain(onResult, "无位置")
            return
        }
        try {
            val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (lm == null) {
                notifyCallbackOnMain(onResult, if (lastCachedLocation != null) lastCachedLocationString else "无位置")
                return
            }

            val hasFine = hasFineLocationPermission(appContext)
            val bestLoc = collectBestLastKnownLocation(lm, hasFine)
            if (bestLoc != null) {
                val formatted = formatLocation(bestLoc)
                if (formatted != "无位置") {
                    lastCachedLocation = bestLoc
                    lastCachedLocationString = formatted
                }
            }

            val now = System.currentTimeMillis()
            // If we already have a fresh fix (< 2 min), return immediately without waking radio.
            if (isLocationFresh(bestLoc)) {
                notifyCallbackOnMain(onResult, lastCachedLocationString)
                return
            }
            // Only enforce cooldown if we already have a cached location; if still "无位置", allow retry!
            if (bestLoc != null && (now - lastRefreshRequestTimeMs) < REFRESH_COOLDOWN_MS) {
                notifyCallbackOnMain(onResult, lastCachedLocationString)
                return
            }

            val eligibleProviders = collectEligibleActiveProviders(lm, hasFine)
            if (eligibleProviders.isEmpty()) {
                notifyCallbackOnMain(onResult, lastCachedLocationString)
                return
            }

            synchronized(lock) {
                if (onResult != null) {
                    pendingCallbacks.add(onResult)
                }
                if (isRequestInFlight) {
                    return
                }
                isRequestInFlight = true
                lastRefreshRequestTimeMs = now
            }

            val timeoutRunnable = Runnable {
                finishActiveRequest(lm, null)
            }
            synchronized(lock) {
                activeTimeoutRunnable = timeoutRunnable
            }
            mainHandler.postDelayed(timeoutRunnable, LOCATION_TIMEOUT_MS)

            var remainingProviders = eligibleProviders.size

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val executor = ContextCompat.getMainExecutor(appContext)
                for (provider in eligibleProviders) {
                    val cancelSignal = CancellationSignal()
                    synchronized(lock) {
                        if (!isRequestInFlight) return
                        activeCancellationSignals.add(cancelSignal)
                    }
                    try {
                        lm.getCurrentLocation(provider, cancelSignal, executor) { loc ->
                            if (loc != null && formatLocation(loc) != "无位置") {
                                finishActiveRequest(lm, loc)
                            } else {
                                val allFailed = synchronized(lock) {
                                    remainingProviders--
                                    remainingProviders <= 0
                                }
                                if (allFailed) {
                                    finishActiveRequest(lm, null)
                                }
                            }
                        }
                    } catch (_: Throwable) {
                        val allFailed = synchronized(lock) {
                            remainingProviders--
                            remainingProviders <= 0
                        }
                        if (allFailed) {
                            finishActiveRequest(lm, null)
                        }
                    }
                }
            } else {
                for (provider in eligibleProviders) {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(loc: Location) {
                            if (formatLocation(loc) != "无位置") {
                                finishActiveRequest(lm, loc)
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(p: String?, s: Int, b: Bundle?) {}
                        override fun onProviderEnabled(p: String) {}
                        override fun onProviderDisabled(p: String) {
                            val allFailed = synchronized(lock) {
                                remainingProviders--
                                remainingProviders <= 0
                            }
                            if (allFailed) {
                                finishActiveRequest(lm, null)
                            }
                        }
                    }
                    synchronized(lock) {
                        if (!isRequestInFlight) return
                        activeListeners.add(listener)
                    }
                    try {
                        @Suppress("DEPRECATION")
                        lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    } catch (_: Throwable) {
                        val allFailed = synchronized(lock) {
                            remainingProviders--
                            remainingProviders <= 0
                        }
                        if (allFailed) {
                            finishActiveRequest(lm, null)
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            finishActiveRequest(null, null)
            notifyCallbackOnMain(onResult, if (lastCachedLocation != null) lastCachedLocationString else "无位置")
        }
    }

    /**
     * Asynchronously resolves a fresh location string and delivers it on the main thread.
     */
    fun resolveLocationAsync(context: Context, onResult: (String) -> Unit) {
        refreshLocation(context, onResult)
    }

    /**
     * Get best available location string immediately.
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

            val hasFine = hasFineLocationPermission(appContext)
            val bestLoc = collectBestLastKnownLocation(lm, hasFine)
            if (bestLoc != null) {
                val formatted = formatLocation(bestLoc)
                if (formatted != "无位置") {
                    lastCachedLocation = bestLoc
                    lastCachedLocationString = formatted
                }
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
