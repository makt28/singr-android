package com.singr.node

import android.content.Context
import java.io.File

/** Filesystem layout and shared constants. */
object Config {
    const val TAG = "SingrNode"
    const val NOTIF_CHANNEL = "singr_node"
    const val NOTIF_ID = 1001

    const val PREFS = "singr"
    const val KEY_ENABLED = "enabled" // service (node runner) on/off

    /**
     * libbox working dir = filesDir. Holds server.json / panel.json and the box
     * log. Passed to Libbox.setup as both base and working path; poet reads
     * panel.json from here (wired in SingR's libbox Setup).
     */
    fun workDir(ctx: Context): File = ctx.filesDir

    fun serverConfig(ctx: Context): File = File(workDir(ctx), "server.json")
    fun panelConfig(ctx: Context): File = File(workDir(ctx), "panel.json")

    /** sing-box log.output target; BoxRunner tails it into NodeLog. */
    fun boxLog(ctx: Context): File = File(workDir(ctx), "box.log")

    /** Per-node TLS material copied out of the SAF picker into real paths. */
    fun nodeCert(ctx: Context, id: Int): File = File(workDir(ctx), "node-$id.crt")
    fun nodeKey(ctx: Context, id: Int): File = File(workDir(ctx), "node-$id.key")

    /** Persisted node list and DDNS config. */
    fun nodesFile(ctx: Context): File = File(workDir(ctx), "nodes.json")
    fun ddnsConfig(ctx: Context): File = File(workDir(ctx), "ddns.json")

    fun isEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, on: Boolean) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, on).apply()
}
