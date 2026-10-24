package com.jwoglom.controlx2.pump

import android.content.Context
import android.os.PowerManager
import timber.log.Timber

/**
 * Reference-counted partial wake lock held while a live BLE pump connection is open.
 *
 * Doze suspends the CPU between BLE transactions, dropping the pump link — the "service
 * stops running" failure we are porting fixes for. A PARTIAL_WAKE_LOCK keeps the CPU awake
 * during active pump communication. Acquire on connect, release on disconnect; the count
 * guards against a disconnect racing a reconnect and releasing the lock early.
 *
 * Acquire is gated by [enabled] so the lock is never held when the user has disabled the
 * background service.
 */
internal object PumpWakeLock {
    @Volatile
    private var count = 0

    @Volatile
    private var wakeLock: PowerManager.WakeLock? = null

    fun acquire(context: Context, enabled: Boolean) {
        if (!enabled || count > 0) return
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ControlX2:pump-ble").apply { acquire() }
        wakeLock = lock
        count = 1
        Timber.d("PumpWakeLock acquired")
    }

    fun release() {
        if (count == 0) return
        count--
        if (count == 0) {
            wakeLock?.apply { if (isHeld) release() }
            wakeLock = null
            Timber.d("PumpWakeLock released")
        }
    }
}
