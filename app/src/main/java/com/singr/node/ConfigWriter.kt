package com.singr.node

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Turns the simple form (apiHost / apiKey / nodeId / type + cert & key files)
 * into the `server.json` + `panel.json` the SingR binary expects. Mirrors
 * release/poet/{server,panel}_{anytls,hysteria2}.json from the SingR repo, with
 * two Android-specific changes:
 *   - log has NO file `output` → goes to stdout (NativeRunner captures it);
 *     the sample's /var/log/singr.log is not writable on Android.
 *   - certificate_path / key_path point at files under filesDir (SAF URIs are
 *     not real paths, so the picked files are copied there first).
 *
 * listen_port stays 0: the real port is delivered by the panel node info and
 * hot-reloaded by the inbound (same as the linux server).
 */
object ConfigWriter {

    data class Input(
        val apiHost: String,
        val apiKey: String,
        val nodeId: Int,
        val type: String, // "anytls" | "hysteria2"
    )

    fun copyCert(ctx: Context, uri: Uri) = copy(ctx, uri, Config.certFile(ctx))
    fun copyKey(ctx: Context, uri: Uri) = copy(ctx, uri, Config.keyFile(ctx))

    private fun copy(ctx: Context, uri: Uri, dest: File) {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { input.copyTo(it) }
        } ?: error("无法打开所选文件")
    }

    fun write(ctx: Context, input: Input) {
        Config.serverConfig(ctx).writeText(serverJson(ctx, input.type))
        Config.panelConfig(ctx).writeText(panelJson(input))
    }

    private fun inTag(type: String) = "$type-in"
    private fun outTag(type: String) = "$type-out"

    private fun directOut(tag: String) = JSONObject()
        .put("type", "direct")
        .put("tag", tag)
        .put("domain_resolver", JSONObject().put("server", "google").put("strategy", "prefer_ipv6"))

    private fun serverJson(ctx: Context, type: String): String {
        val tls = JSONObject()
            .put("enabled", true)
            .put("server_name", "")
            .put("certificate_path", Config.certFile(ctx).absolutePath)
            .put("key_path", Config.keyFile(ctx).absolutePath)

        val inbound = JSONObject()
            .put("type", type)
            .put("tag", inTag(type))
            .put("listen", "::")
            .put("listen_port", 0)
            .put("users", JSONArray())
            .put("tls", tls)
        if (type == "hysteria2") {
            inbound.put("up_mbps", 0)
                .put("down_mbps", 0)
                .put("ignore_client_bandwidth", false)
                .put("obfs", JSONObject().put("type", "salamander").put("password", ""))
        }

        val root = JSONObject()
            .put(
                "log",
                JSONObject().put("disabled", false).put("level", "info").put("timestamp", true),
            )
            .put(
                "dns",
                JSONObject()
                    .put(
                        "servers",
                        JSONArray().put(
                            JSONObject().put("tag", "google").put("type", "udp").put("server", "8.8.8.8"),
                        ),
                    )
                    .put("strategy", "prefer_ipv6"),
            )
            .put("inbounds", JSONArray().put(inbound))
            .put("outbounds", JSONArray().put(directOut(outTag(type))).put(directOut("direct")))
            .put(
                "route",
                JSONObject()
                    .put(
                        "rules",
                        JSONArray().put(
                            JSONObject().put("inbound", inTag(type)).put("outbound", outTag(type)),
                        ),
                    )
                    .put("final", "direct")
                    .put("auto_detect_interface", false),
            )
        return root.toString(2)
    }

    private fun panelJson(input: Input): String {
        val node = JSONObject()
            .put("paneltype", "SSpanel")
            .put("intag", inTag(input.type))
            .put("outtag", outTag(input.type))
            .put(
                "apiconfig",
                JSONObject()
                    .put("apihost", input.apiHost)
                    .put("apikey", input.apiKey)
                    .put("nodeid", input.nodeId)
                    .put("nodetype", "V2ray")
                    .put("disablecustomconfig", true),
            )
        return JSONObject()
            .put("name", "singr-android")
            .put("nodes", JSONArray().put(node))
            .toString(2)
    }
}
