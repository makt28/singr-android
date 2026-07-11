package com.singr.node

import android.content.Context
import org.json.JSONArray

/** Loads/saves the node list to filesDir/nodes.json. */
object NodeStore {

    fun load(ctx: Context): MutableList<NodeConfig> {
        val f = Config.nodesFile(ctx)
        if (!f.exists()) return mutableListOf()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { NodeConfig.fromJson(arr.getJSONObject(it)) }.toMutableList()
        }.getOrDefault(mutableListOf())
    }

    fun save(ctx: Context, nodes: List<NodeConfig>) {
        Config.nodesFile(ctx).writeText(nodes.toJsonArray().toString(2))
    }

    /** Next free id (max + 1), starting at 1. */
    fun nextId(nodes: List<NodeConfig>): Int = (nodes.maxOfOrNull { it.id } ?: 0) + 1

    fun upsert(ctx: Context, node: NodeConfig) {
        val nodes = load(ctx)
        val i = nodes.indexOfFirst { it.id == node.id }
        if (i >= 0) nodes[i] = node else nodes.add(node)
        save(ctx, nodes)
    }

    fun delete(ctx: Context, id: Int) {
        val nodes = load(ctx).filterNot { it.id == id }
        save(ctx, nodes)
        Config.nodeCert(ctx, id).delete()
        Config.nodeKey(ctx, id).delete()
    }
}
