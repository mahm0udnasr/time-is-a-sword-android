package com.mahm0udnasr.sword.prayer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.mahm0udnasr.sword.core.prayer.Coords
import com.mahm0udnasr.sword.core.prayer.normalizeCoords
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * "Use my location" (step 7.3): coarse, one-shot, rounded to 2 decimals
 * (about 1 km). Asked only when the user presses the button; the position is
 * used on the phone only and never sent anywhere.
 */
object LocationHelper {
    sealed interface Result {
        data class Ok(val coords: Coords) : Result
        data object Denied : Result
        data object Off : Result
        data object Failed : Result
    }

    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked first
    suspend fun locate(ctx: Context): Result {
        if (!hasPermission(ctx)) return Result.Denied
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return Result.Failed
        if (!LocationManagerCompat.isLocationEnabled(lm)) return Result.Off
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31 && lm.allProviders.contains(LocationManager.FUSED_PROVIDER)) add(LocationManager.FUSED_PROVIDER)
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
        }
        // A recent known position is good enough for prayer times (≈ 1 km).
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos < MAX_AGE_NS }
            .maxByOrNull { it.elapsedRealtimeNanos }
            ?.let { loc -> normalizeCoords(Coords(loc.latitude, loc.longitude))?.let { return Result.Ok(it) } }

        for (p in providers) {
            val loc = withTimeoutOrNull(TIMEOUT_MS) { current(ctx, lm, p) } ?: continue
            normalizeCoords(Coords(loc.latitude, loc.longitude))?.let { return Result.Ok(it) }
        }
        return Result.Failed
    }

    @SuppressLint("MissingPermission")
    private suspend fun current(ctx: Context, lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val cancel = CancellationSignal()
            cont.invokeOnCancellation { cancel.cancel() }
            runCatching {
                LocationManagerCompat.getCurrentLocation(lm, provider, cancel, ContextCompat.getMainExecutor(ctx)) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }

    private const val TIMEOUT_MS = 15_000L
    private const val MAX_AGE_NS = 10L * 60 * 1_000_000_000
}
