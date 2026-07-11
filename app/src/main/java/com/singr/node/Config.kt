package com.singr.node

import android.content.Context
import java.io.File

/** Filesystem layout and shared constants. */
object Config {
    const val TAG = "SingrNode"
    const val NOTIF_CHANNEL = "singr_node"
    const val NOTIF_ID = 1001

    /** SharedPreferences: node state + last-used form values. */
    const val PREFS = "singr"
    const val KEY_ENABLED = "enabled"
    const val KEY_APIHOST = "apihost"
    const val KEY_APIKEY = "apikey"
    const val KEY_NODEID = "nodeid"
    const val KEY_NODETYPE = "nodetype" // "anytls" | "hysteria2"

    /** The extracted, exec-able core (jniLibs → nativeLibraryDir). */
    fun coreBinary(ctx: Context): File =
        File(ctx.applicationInfo.nativeLibraryDir, "libsingr.so")

    /** Working directory the core runs in; holds server.json / panel.json. */
    fun workDir(ctx: Context): File = ctx.filesDir

    fun serverConfig(ctx: Context): File = File(workDir(ctx), "server.json")
    fun panelConfig(ctx: Context): File = File(workDir(ctx), "panel.json")

    /** TLS material copied out of the SAF picker into a real filesystem path. */
    fun certFile(ctx: Context): File = File(workDir(ctx), "singr.crt")
    fun keyFile(ctx: Context): File = File(workDir(ctx), "singr.key")

    /** Optional DDNS config; absent → DDNS disabled. */
    fun ddnsConfig(ctx: Context): File = File(workDir(ctx), "ddns.json")

    fun isEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, on: Boolean) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, on).apply()
}
