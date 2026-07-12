package com.singr.node

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.NetworkInterface
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * DDNS config, read from filesDir/ddns.json. Absent → DDNS disabled.
 *
 * Example (Cloudflare):
 * {
 *   "provider": "cloudflare",
 *   "token": "CF_API_TOKEN",
 *   "zoneId": "...",
 *   "name": "node.example.com"
 * }
 */
data class DdnsConfig(
    val provider: String,
    val token: String,
    val zoneId: String,
    val name: String,
) {
    companion object {
        fun parse(json: String): DdnsConfig? = runCatching {
            val o = JSONObject(json)
            DdnsConfig(
                provider = o.optString("provider", "cloudflare"),
                token = o.getString("token"),
                zoneId = o.getString("zoneId"),
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

    /** Find (or create) the named AAAA record and push an update. */
    fun update(cfg: DdnsConfig, ipv6: String): Boolean {
        if (cfg.provider != "cloudflare") {
            Log.w(Config.TAG, "unsupported DDNS provider: ${cfg.provider}")
            return false
        }
        return runCatching {
            val recordsUrl =
                "https://api.cloudflare.com/client/v4/zones/${cfg.zoneId}/dns_records"
            val encodedName = URLEncoder.encode(cfg.name, StandardCharsets.UTF_8.name())
            val lookup = request(
                cfg,
                URL("$recordsUrl?type=AAAA&name=$encodedName"),
                "GET",
            )
            if (lookup.code !in 200..299) {
                Log.w(Config.TAG, "DDNS record lookup HTTP ${lookup.code}")
                return false
            }

            val result = JSONObject(lookup.body).getJSONArray("result")
            if (result.length() > 1) {
                Log.w(Config.TAG, "multiple AAAA records found for ${cfg.name}; refusing to guess")
                return false
            }

            val recordId = if (result.length() == 1) result.getJSONObject(0).getString("id") else null
            val body = JSONObject()
                .put("type", "AAAA")
                .put("name", cfg.name)
                .put("content", ipv6)
                .put("ttl", 120)
                .put("proxied", false)
                .toString()
            val method = if (recordId == null) "POST" else "PUT"
            val url = URL(if (recordId == null) recordsUrl else "$recordsUrl/$recordId")
            val response = request(cfg, url, method, body)
            val ok = response.code in 200..299
            if (!ok) Log.w(Config.TAG, "DDNS $method HTTP ${response.code}")
            ok
        }.getOrElse {
            Log.e(Config.TAG, "DDNS update failed", it)
            false
        }
    }

    private data class Response(val code: Int, val body: String)

    private fun request(
        cfg: DdnsConfig,
        url: URL,
        method: String,
        body: String? = null,
    ): Response = (url.openConnection() as HttpURLConnection).run {
        try {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Authorization", "Bearer ${cfg.token}")
            setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            val code = responseCode
            val stream = if (code in 200..299) inputStream else errorStream
            Response(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            disconnect()
        }
    }
}
