package com.jwoglom.controlx2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.jwoglom.controlx2.shared.enums.DeviceRole
import timber.log.Timber

/**
 * Restarts CommService after a device reboot, but only when the user has the background
 * service enabled (the same gate CommService.onCreate() uses) and the phone is pump-host.
 *
 * Without this a reboot leaves the pump unmonitored until the app is opened, because the
 * Wearable data layer only starts CommService when the watch is alive and messaging. This
 * is intentionally gated on serviceEnabled(): if the user has not turned the service on we
 * must not start a foreground service or request a wake lock on their behalf.
 */
class LaunchCommServiceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }
        val ctx = context ?: return
        if (!Prefs(ctx).serviceEnabled()) {
            Timber.d("Not restarting CommService after boot: service not enabled")
            return
        }
        if (Prefs(ctx).deviceRole() != DeviceRole.PUMP_HOST) {
            Timber.d("Not restarting CommService after boot: device role is not PUMP_HOST")
            return
        }
        Timber.i("Restarting CommService after boot")
        val serviceIntent = Intent(ctx, CommService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(serviceIntent)
            } else {
                ctx.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to restart CommService after boot")
        }
    }
}
