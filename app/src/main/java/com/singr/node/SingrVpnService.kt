package com.singr.node

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.singr.node.R

/**
 * Keep-alive anchor, NOT a traffic tunnel.
 *
 * An active VPN session is treated as user-critical, so the OS/OEM rarely kills
 * it — that stickiness is the whole point. To get it without hijacking the
 * node's own traffic, the tun is deliberately inert:
 *   - one address, one throwaway route (RFC5737/RFC3849 documentation ranges,
 *     so essentially nothing real is routed into tun),
 *   - `addDisallowedApplication(self)` — the libbox core runs in-process (our
 *     UID), so it always bypasses the tun and dials the internet directly.
 *
 * We never read the tun fd; it exists only to keep the VPN "connected". The
 * core itself no longer uses any tun — it's an inbound-only node run via
 * BoxRunner/libbox; this VpnService is purely a keep-alive anchor.
 */
class SingrVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private val runner by lazy { BoxRunner(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        startForeground(Config.NOTIF_ID, buildNotification())
        Config.setEnabled(this, true)

        if (tun == null) {
            tun = establishInertTun()
            if (tun == null) {
                Log.e(Config.TAG, "establish() failed; is VPN consent granted?")
                stopSelfCleanly()
                return START_NOT_STICKY
            }
        }

        runner.start()
        // DDNS is scheduled/cancelled from the DDNS tab; WorkManager persists it
        // across reboots, so the service doesn't manage it.

        // START_STICKY: if the system kills us, recreate. Combined with
        // Always-on VPN this is the "dies -> restarts" guarantee.
        return START_STICKY
    }

    private fun establishInertTun(): ParcelFileDescriptor? = try {
        Builder()
            .setSession("SingR Node")
            .setMtu(1500)
            .addAddress("10.66.0.1", 32)
            .addAddress("fd00:66::1", 128)
            // Throwaway routes: TEST-NET-1 (v4) / documentation prefix (v6).
            .addRoute("192.0.2.0", 32)
            .addRoute("2001:db8::", 128)
            // Our own UID (and thus the SingR subprocess) bypasses the tun.
            .addDisallowedApplication(packageName)
            .establish()
    } catch (t: Throwable) {
        Log.e(Config.TAG, "establishInertTun", t)
        null
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Config.NOTIF_CHANNEL)
            .setContentTitle("SingR node running")
            .setContentText("Keep-alive tunnel active")
            .setSmallIcon(R.drawable.ic_stat_node)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    private fun stopSelfCleanly() {
        Config.setEnabled(this, false)
        runner.stop()
        runCatching { tun?.close() }
        tun = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        // User replaced/revoked the VPN from system settings.
        stopSelfCleanly()
        super.onRevoke()
    }

    override fun onDestroy() {
        runner.stop()
        runCatching { tun?.close() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.singr.node.STOP"
    }
}
