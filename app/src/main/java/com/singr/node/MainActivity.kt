package com.singr.node

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.singr.node.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var certUri: Uri? = null
    private var keyUri: Uri? = null

    private val pickCert = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { certUri = it; b.tvCert.text = displayName(it) }
    }
    private val pickKey = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { keyUri = it; b.tvKey.text = displayName(it) }
    }
    private val vpnConsent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r -> if (r.resultCode == RESULT_OK) startNode() else toast("VPN 授权被拒绝") }

    private val notifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* best-effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        val p = getSharedPreferences(Config.PREFS, MODE_PRIVATE)
        b.etApiHost.setText(p.getString(Config.KEY_APIHOST, ""))
        b.etApiKey.setText(p.getString(Config.KEY_APIKEY, ""))
        p.getInt(Config.KEY_NODEID, 0).let { if (it != 0) b.etNodeId.setText(it.toString()) }
        if (p.getString(Config.KEY_NODETYPE, "anytls") == "hysteria2") b.rbHy2.isChecked = true
        else b.rbAnytls.isChecked = true
        if (Config.certFile(this).exists()) b.tvCert.text = getString(R.string.file_saved)
        if (Config.keyFile(this).exists()) b.tvKey.text = getString(R.string.file_saved)

        b.btnPickCert.setOnClickListener { pickCert.launch(arrayOf("*/*")) }
        b.btnPickKey.setOnClickListener { pickKey.launch(arrayOf("*/*")) }
        b.btnSave.setOnClickListener { if (save()) toast("配置已保存") }
        b.btnStart.setOnClickListener { requestAndStart() }
        b.btnStop.setOnClickListener { stopNode() }
        b.btnBattery.setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    /** Validate form, copy certs, generate configs, persist. Returns success. */
    private fun save(): Boolean {
        val host = b.etApiHost.text.toString().trim()
        val key = b.etApiKey.text.toString().trim()
        val nid = b.etNodeId.text.toString().trim().toIntOrNull()
        val type = if (b.rbHy2.isChecked) "hysteria2" else "anytls"

        if (host.isEmpty() || key.isEmpty() || nid == null) {
            toast("API 地址 / Key / NodeID 必填"); return false
        }
        try {
            certUri?.let { ConfigWriter.copyCert(this, it) }
            keyUri?.let { ConfigWriter.copyKey(this, it) }
        } catch (t: Throwable) {
            toast("读取证书失败：${t.message}"); return false
        }
        if (!Config.certFile(this).exists() || !Config.keyFile(this).exists()) {
            toast("请选择证书和私钥文件"); return false
        }

        ConfigWriter.write(this, ConfigWriter.Input(host, key, nid, type))
        getSharedPreferences(Config.PREFS, MODE_PRIVATE).edit()
            .putString(Config.KEY_APIHOST, host)
            .putString(Config.KEY_APIKEY, key)
            .putInt(Config.KEY_NODEID, nid)
            .putString(Config.KEY_NODETYPE, type)
            .apply()
        return true
    }

    private fun requestAndStart() {
        if (!Config.panelConfig(this).exists() || !Config.serverConfig(this).exists()) {
            if (!save()) return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val prep = VpnService.prepare(this)
        if (prep != null) vpnConsent.launch(prep) else startNode()
    }

    private fun startNode() {
        val svc = Intent(this, SingrVpnService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
        else startService(svc)
        toast("节点启动中")
    }

    private fun stopNode() {
        startService(Intent(this, SingrVpnService::class.java).setAction(SingrVpnService.ACTION_STOP))
        DdnsWorker.cancel(this)
        toast("节点已停止")
    }

    private fun displayName(uri: Uri): String =
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        } ?: uri.lastPathSegment ?: "已选择"

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
