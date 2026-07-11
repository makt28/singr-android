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

    /** The extracted, exec-able core (jniLibs → nativeLibraryDir). */
    fun coreBinary(ctx: Context): File =
        File(ctx.applicationInfo.nativeLibraryDir, "libsingr.so")

    /**
     * proot + its loader, used only as a DNS shim: the core is a standalone
     * CGO-disabled Go binary whose resolver needs /etc/resolv.conf, absent on
     * Android. proot binds [resolvConf] onto /etc/resolv.conf. Both must sit in
     * nativeLibraryDir (packaged as lib*.so) to be exec-able under Android W^X;
     * PROOT_LOADER points the proot binary at the loader here. Optional — if the
     * files are missing, NativeRunner execs the core directly.
     */
    fun prootBinary(ctx: Context): File =
        File(ctx.applicationInfo.nativeLibraryDir, "libproot.so")

    fun prootLoader(ctx: Context): File =
        File(ctx.applicationInfo.nativeLibraryDir, "libproot-loader.so")

    /** resolv.conf fed to the core via proot; regenerated on each launch. */
    fun resolvConf(ctx: Context): File = File(workDir(ctx), "resolv.conf")

    fun writeResolvConf(ctx: Context) {
        resolvConf(ctx).writeText("nameserver 8.8.8.8\nnameserver 2001:4860:4860::8888\n")
    }

    /**
     * CA bundle for the core's TLS: it's a GOOS=linux Go binary, so x509 hunts
     * for roots at Linux paths absent on Android. The Mozilla bundle is shipped
     * as an asset (build.yml) and proot-bound onto /etc/ssl/certs/ca-certificates
     * .crt. Copied out of assets to filesDir so proot has a real path to bind.
     */
    fun caBundle(ctx: Context): File = File(workDir(ctx), "cacert.pem")

    /** Extract the shipped CA bundle to filesDir; false if none was bundled. */
    fun extractCaBundle(ctx: Context): Boolean = try {
        ctx.assets.open("cacert.pem").use { input ->
            caBundle(ctx).outputStream().use { input.copyTo(it) }
        }
        true
    } catch (_: Exception) {
        false
    }

    /** Working directory the core runs in; holds server.json / panel.json. */
    fun workDir(ctx: Context): File = ctx.filesDir

    fun serverConfig(ctx: Context): File = File(workDir(ctx), "server.json")
    fun panelConfig(ctx: Context): File = File(workDir(ctx), "panel.json")

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
