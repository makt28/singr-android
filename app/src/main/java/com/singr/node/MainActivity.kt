package com.singr.node

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.singr.node.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val vpnConsent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) startNode()
        else toast("VPN consent denied")
    }

    private val notifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* best-effort; foreground service still runs without it */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.panelConfig.setText(
            Config.panelConfig(this).takeIf { it.exists() }?.readText().orEmpty()
        )

        binding.savePanel.setOnClickListener {
            Config.panelConfig(this).writeText(binding.panelConfig.text.toString())
            toast("panel.json saved")
        }
        binding.start.setOnClickListener { requestAndStart() }
        binding.stop.setOnClickListener { stopNode() }
        binding.battery.setOnClickListener { requestIgnoreBattery() }
    }

    private fun requestAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val prepare = VpnService.prepare(this)
        if (prepare != null) vpnConsent.launch(prepare) else startNode()
    }

    private fun startNode() {
        val svc = Intent(this, SingrVpnService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
        else startService(svc)
        toast("node starting")
    }

    private fun stopNode() {
        startService(Intent(this, SingrVpnService::class.java).setAction(SingrVpnService.ACTION_STOP))
        DdnsWorker.cancel(this)
        toast("node stopping")
    }

    private fun requestIgnoreBattery() {
        // Guides the operator to whitelist us; enable system Always-on VPN too.
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
