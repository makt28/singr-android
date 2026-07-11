package com.singr.node

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class NodesFragment : Fragment(R.layout.fragment_nodes) {

    private lateinit var adapter: NodeAdapter

    private val vpnConsent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r -> if (r.resultCode == android.app.Activity.RESULT_OK) startNode() else toast("VPN 授权被拒绝") }

    private val notifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = NodeAdapter(
            onToggle = { node, on ->
                node.enabled = on
                NodeStore.upsert(requireContext(), node)
            },
            onClick = { node -> openEdit(node.id) },
            onDelete = { node ->
                NodeStore.delete(requireContext(), node.id)
                refresh()
            },
        )
        view.findViewById<RecyclerView>(R.id.rvNodes).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@NodesFragment.adapter
        }
        view.findViewById<View>(R.id.btnAdd).setOnClickListener { openEdit(-1) }
        view.findViewById<View>(R.id.btnStart).setOnClickListener { applyAndStart() }
        view.findViewById<View>(R.id.btnStop).setOnClickListener { stopNode() }
        view.findViewById<View>(R.id.btnBattery).setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        view.findViewById<View>(R.id.btnLogCopy).setOnClickListener { copyLog() }
        view.findViewById<View>(R.id.btnLogClear).setOnClickListener { NodeLog.clear() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        // Push updates from the (background) watchdog thread onto the UI thread.
        NodeLog.observe { view?.post { renderRunState() } }
        renderRunState()
    }

    override fun onPause() {
        super.onPause()
        NodeLog.observe(null)
    }

    private fun renderRunState() {
        val v = view ?: return
        val status = v.findViewById<TextView>(R.id.tvStatus)
        val (textRes, colorRes) = when (NodeLog.state) {
            NodeLog.State.RUNNING -> R.string.status_running to android.R.color.holo_green_dark
            NodeLog.State.RESTARTING -> R.string.status_restarting to android.R.color.holo_orange_dark
            NodeLog.State.STOPPED -> R.string.status_stopped to android.R.color.darker_gray
        }
        status.setText(textRes)
        status.setTextColor(ContextCompat.getColor(requireContext(), colorRes))

        val log = v.findViewById<TextView>(R.id.tvLog)
        val lines = NodeLog.snapshot()
        if (lines.isEmpty()) {
            log.setText(R.string.log_empty)
        } else {
            log.text = lines.joinToString("\n")
            val scroll = v.findViewById<ScrollView>(R.id.logScroll)
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun copyLog() {
        val text = NodeLog.snapshot().joinToString("\n")
        if (text.isEmpty()) return
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("singr log", text))
        toast(getString(R.string.log_copied))
    }

    private fun refresh() {
        adapter.submit(NodeStore.load(requireContext()))
    }

    private fun openEdit(id: Int) {
        startActivity(Intent(requireContext(), NodeEditActivity::class.java).putExtra("id", id))
    }

    private fun applyAndStart() {
        val nodes = NodeStore.load(requireContext())
        if (!ConfigWriter.write(requireContext(), nodes)) {
            toast("没有已启用的节点"); return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val prep = VpnService.prepare(requireContext())
        if (prep != null) vpnConsent.launch(prep) else startNode()
    }

    private fun startNode() {
        val svc = Intent(requireContext(), SingrVpnService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) requireContext().startForegroundService(svc)
        else requireContext().startService(svc)
        toast("节点启动中")
    }

    private fun stopNode() {
        requireContext().startService(
            Intent(requireContext(), SingrVpnService::class.java).setAction(SingrVpnService.ACTION_STOP)
        )
        toast("节点已停止")
    }

    private fun toast(m: String) = Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show()
}

class NodeAdapter(
    private val onToggle: (NodeConfig, Boolean) -> Unit,
    private val onClick: (NodeConfig) -> Unit,
    private val onDelete: (NodeConfig) -> Unit,
) : RecyclerView.Adapter<NodeAdapter.VH>() {

    private val items = mutableListOf<NodeConfig>()

    fun submit(list: List<NodeConfig>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: android.widget.TextView = v.findViewById(R.id.tvNodeName)
        val sub: android.widget.TextView = v.findViewById(R.id.tvNodeSub)
        val sw: android.widget.CompoundButton = v.findViewById(R.id.swEnabled)
        val del: View = v.findViewById(R.id.btnDeleteNode)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_node, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val n = items[position]
        val label = n.name.ifBlank { "节点 ${n.id}" }
        h.name.text = label
        h.sub.text = "${n.type} · nodeId ${n.nodeId} · ${n.apiHost}"
        h.sw.setOnCheckedChangeListener(null)
        h.sw.isChecked = n.enabled
        h.sw.setOnCheckedChangeListener { _, on -> onToggle(n, on) }
        h.itemView.setOnClickListener { onClick(n) }
        h.del.setOnClickListener { onDelete(n) }
    }
}
