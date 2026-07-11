package com.singr.node

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/** Add (id == -1) or edit one node. */
class NodeEditActivity : AppCompatActivity() {

    private var id = -1
    private var certUri: Uri? = null
    private var keyUri: Uri? = null

    private val pickCert = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { certUri = it; findViewById<TextView>(R.id.tvCert).text = displayName(it) }
    }
    private val pickKey = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { keyUri = it; findViewById<TextView>(R.id.tvKey).text = displayName(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_node_edit)
        id = intent.getIntExtra("id", -1)

        val existing = if (id >= 0) NodeStore.load(this).firstOrNull { it.id == id } else null
        existing?.let { n ->
            findViewById<EditText>(R.id.etName).setText(n.name)
            findViewById<EditText>(R.id.etApiHost).setText(n.apiHost)
            findViewById<EditText>(R.id.etApiKey).setText(n.apiKey)
            findViewById<EditText>(R.id.etNodeId).setText(n.nodeId.toString())
            if (n.type == "hysteria2") findViewById<RadioButton>(R.id.rbHy2).isChecked = true
            else findViewById<RadioButton>(R.id.rbAnytls).isChecked = true
            if (Config.nodeCert(this, n.id).exists()) findViewById<TextView>(R.id.tvCert).text = getString(R.string.file_saved)
            if (Config.nodeKey(this, n.id).exists()) findViewById<TextView>(R.id.tvKey).text = getString(R.string.file_saved)
        }

        findViewById<Button>(R.id.btnPickCert).setOnClickListener { pickCert.launch(arrayOf("*/*")) }
        findViewById<Button>(R.id.btnPickKey).setOnClickListener { pickKey.launch(arrayOf("*/*")) }
        findViewById<Button>(R.id.btnSaveNode).setOnClickListener { save() }
    }

    private fun save() {
        val name = findViewById<EditText>(R.id.etName).text.toString().trim()
        val host = findViewById<EditText>(R.id.etApiHost).text.toString().trim()
        val key = findViewById<EditText>(R.id.etApiKey).text.toString().trim()
        val nid = findViewById<EditText>(R.id.etNodeId).text.toString().trim().toIntOrNull()
        val type = if (findViewById<RadioButton>(R.id.rbHy2).isChecked) "hysteria2" else "anytls"

        if (host.isEmpty() || key.isEmpty() || nid == null) {
            toast("API 地址 / Key / NodeID 必填"); return
        }

        // Assign an id for new nodes before copying certs (cert files are id-keyed).
        val nodes = NodeStore.load(this)
        val realId = if (id >= 0) id else NodeStore.nextId(nodes)

        try {
            certUri?.let { ConfigWriter.copyCert(this, realId, it) }
            keyUri?.let { ConfigWriter.copyKey(this, realId, it) }
        } catch (t: Throwable) {
            toast("读取证书失败：${t.message}"); return
        }
        if (!Config.nodeCert(this, realId).exists() || !Config.nodeKey(this, realId).exists()) {
            toast("请选择证书和私钥文件"); return
        }

        val enabled = nodes.firstOrNull { it.id == realId }?.enabled ?: true
        NodeStore.upsert(this, NodeConfig(realId, name, type, host, key, nid, enabled))
        toast("已保存")
        finish()
    }

    private fun displayName(uri: Uri): String =
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        } ?: uri.lastPathSegment ?: "已选择"

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
