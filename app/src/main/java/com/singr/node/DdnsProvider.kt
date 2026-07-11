package com.singr.node

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.NetworkInterface
import java.net.URL

/**
 * DDNS config, read from filesDir/ddns.json. Absent → DDNS disabled.
 *
 * Example (Cloudflare):
 * {
 *   "provider": "cloudflare",
 *   "token": "CF_API_TOKEN",
 *   "zoneId": "...",
 *   "recordId": "...",
 *   "name": "node.example.com"
 * }
 */
data class DdnsConfig(
    val provider: String,
    val token: String,
    val zoneId: String,
    val recordId: String,
    val name: String,
) {
    companion object {
        fun parse(json: String): DdnsConfig? = runCatching {
            val o = JSONObject(json)
            DdnsConfig(
                provider = o.optString("provider", "cloudflare"),
                token = o.getString("token"),
                zoneId = o.getString("zoneId"),
                recordId = o.getString("recordId"),
                name = o.getString("name"),
            )
        }.getOrNull()
    }
}

object Ddns {

    /** First global-scope IPv6 on any up interface, or null. */
    fun globalIpv6(): String? {
        val ifaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull()
            ?: return null
        for (iface in ifaces) {
            if (!iface.isUp || iface.isLoopback) continue
            for (addr in iface.inetAddresses) {
                if (addr is Inet6Address &&
                    !addr.isLinkLocalAddress &&
                    !addr.isLoopbackAddress &&
                    !addr.isSiteLocalAddress &&      // fec0::/10 (legacy)
                    !addr.isAnyLocalAddress &&
                    !isUniqueLocal(addr)             // fc00::/7 ULA
                ) {
                    return addr.hostAddress?.substringBefore('%')
                }
            }
        }
        return null
    }

    private fun isUniqueLocal(a: Inet6Address): Boolean =
        (a.address.firstOrNull()?.toInt()?.and(0xfe)) == 0xfc

    /** Push an AAAA update. Returns true on success. Cloudflare only for now. */
    fun update(cfg: DdnsConfig, ipv6: String): Boolean {
        if (cfg.provider != "cloudflare") {
            Log.w(Config.TAG, "unsupported DDNS provider: ${cfg.provider}")
            return false
        }
        return runCatching {
            val url = URL(
                "https://api.cloudflare.com/client/v4/zones/${cfg.zoneId}/dns_records/${cfg.recordId}"
            )
            val body = JSONObject()
                .put("type", "AAAA")
                .put("name", cfg.name)
                .put("content", ipv6)
                .put("ttl", 120)
                .put("proxied", false)
                .toString()

            (url.openConnection() as HttpURLConnection).run {
                requestMethod = "PUT"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer ${cfg.token}")
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toByteArray()) }
                val ok = responseCode in 200..299
                if (!ok) Log.w(Config.TAG, "DDNS update HTTP $responseCode")
                disconnect()
                ok
            }
        }.getOrElse {
            Log.e(Config.TAG, "DDNS update failed", it)
            false
        }
    }
}
