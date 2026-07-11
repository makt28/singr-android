package com.singr.node

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Turns the enabled node list into the `server.json` + `panel.json` the SingR
 * binary expects. Each node gets a unique inbound/outbound tag (`<type>-in-<id>`)
 * so multiple nodes run in one process — poet starts one controller per node.
 *
 * Android-specific vs the SingR samples:
 *   - log `output` points at filesDir/box.log (BoxRunner tails it into the UI);
 *     the sample's /var/log/singr.log is not writable on Android.
 *   - certificate_path / key_path point at per-node files under filesDir (SAF
 *     URIs are not real paths, so picked files are copied there first).
 *   - listen_port stays 0: the real port is delivered by the panel node info.
 */
object ConfigWriter {

    fun copyCert(ctx: Context, id: Int, uri: Uri) = copy(ctx, uri, Config.nodeCert(ctx, id))
    fun copyKey(ctx: Context, id: Int, uri: Uri) = copy(ctx, uri, Config.nodeKey(ctx, id))

    private fun copy(ctx: Context, uri: Uri, dest: File) {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { input.copyTo(it) }
        } ?: error("无法打开所选文件")
    }

    /** True if at least one enabled node was written. */
    fun write(ctx: Context, nodes: List<NodeConfig>): Boolean {
        val enabled = nodes.filter { it.enabled }
        if (enabled.isEmpty()) return false
        Config.serverConfig(ctx).writeText(serverJson(ctx, enabled))
        Config.panelConfig(ctx).writeText(panelJson(enabled))
        return true
    }

    private fun directOut(tag: String) = JSONObject()
        .put("type", "direct")
        .put("tag", tag)
        .put("domain_resolver", JSONObject().put("server", "google").put("strategy", "prefer_ipv6"))

    private fun inbound(ctx: Context, n: NodeConfig): JSONObject {
        val tls = JSONObject()
            .put("enabled", true)
            .put("server_name", "")
            .put("certificate_path", Config.nodeCert(ctx, n.id).absolutePath)
            .put("key_path", Config.nodeKey(ctx, n.id).absolutePath)
        val ib = JSONObject()
            .put("type", n.type)
            .put("tag", n.inTag)
            .put("listen", "::")
            .put("listen_port", 0)
            .put("users", JSONArray())
            .put("tls", tls)
        if (n.type == "hysteria2") {
            ib.put("up_mbps", 0)
                .put("down_mbps", 0)
                .put("ignore_client_bandwidth", false)
                .put("obfs", JSONObject().put("type", "salamander").put("password", ""))
        }
        return ib
    }

    private fun serverJson(ctx: Context, nodes: List<NodeConfig>): String {
        val inbounds = JSONArray()
        val outbounds = JSONArray()
        val rules = JSONArray()
        for (n in nodes) {
            inbounds.put(inbound(ctx, n))
            outbounds.put(directOut(n.outTag))
            rules.put(JSONObject().put("inbound", n.inTag).put("outbound", n.outTag))
        }
        outbounds.put(directOut("direct")) // shared fallback

        return JSONObject()
            .put(
                "log",
                JSONObject().put("disabled", false).put("level", "info").put("timestamp", true)
                    // libbox runs in-process (no stdout to capture), so write to a
                    // file BoxRunner tails. filesDir is writable, unlike /var/log.
                    .put("output", Config.boxLog(ctx).absolutePath),
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
            .put("inbounds", inbounds)
            .put("outbounds", outbounds)
            .put(
                "route",
                JSONObject().put("rules", rules).put("final", "direct").put("auto_detect_interface", false),
            )
            .toString(2)
    }

    private fun panelJson(nodes: List<NodeConfig>): String {
        val arr = JSONArray()
        for (n in nodes) {
            arr.put(
                JSONObject()
                    .put("paneltype", "SSpanel")
                    .put("intag", n.inTag)
                    .put("outtag", n.outTag)
                    .put(
                        "apiconfig",
                        JSONObject()
                            .put("apihost", n.apiHost)
                            .put("apikey", n.apiKey)
                            .put("nodeid", n.nodeId)
                            .put("nodetype", "V2ray")
                            .put("disablecustomconfig", true),
                    ),
            )
        }
        return JSONObject().put("name", "singr-android").put("nodes", arr).toString(2)
    }
}
