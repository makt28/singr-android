package com.singr.node

import org.json.JSONArray
import org.json.JSONObject

/**
 * One SingR node. Cert/key live as files keyed by [id] (Config.nodeCert/nodeKey),
 * not inline. [type] is "anytls" | "hysteria2".
 */
data class NodeConfig(
    val id: Int,
    var name: String,
    var type: String,
    var apiHost: String,
    var apiKey: String,
    var nodeId: Int,
    var enabled: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("type", type)
        .put("apiHost", apiHost)
        .put("apiKey", apiKey)
        .put("nodeId", nodeId)
        .put("enabled", enabled)

    // Unique sing-box tags per node so multiple nodes coexist in one process.
    val inTag get() = "$type-in-$id"
    val outTag get() = "$type-out-$id"

    companion object {
        fun fromJson(o: JSONObject) = NodeConfig(
            id = o.getInt("id"),
            name = o.optString("name"),
            type = o.optString("type", "anytls"),
            apiHost = o.optString("apiHost"),
            apiKey = o.optString("apiKey"),
            nodeId = o.optInt("nodeId"),
            enabled = o.optBoolean("enabled", true),
        )
    }
}

fun List<NodeConfig>.toJsonArray(): JSONArray =
    JSONArray().apply { this@toJsonArray.forEach { put(it.toJson()) } }
