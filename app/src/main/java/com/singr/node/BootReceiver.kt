package com.singr.node

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Belt-and-suspenders autostart. The primary boot mechanism is system
 * "Always-on VPN", which relaunches SingrVpnService without any receiver and
 * survives kills. This receiver only helps on ROMs where the user enabled the
 * node but not Always-on VPN.
 *
 * Note: we can only (re)start the VPN if consent was already granted; the
 * first-ever run still needs a manual enable from MainActivity.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        if (!Config.isEnabled(context)) return

        val svc = Intent(context, SingrVpnService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc)
            } else {
                context.startService(svc)
            }
        } catch (t: Throwable) {
            Log.w(Config.TAG, "boot autostart failed (consent likely not granted)", t)
        }
    }
}
