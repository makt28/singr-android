package com.singr.node

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import org.json.JSONObject

/**
 * DDNS config UI → writes filesDir/ddns.json (read by DdnsWorker). Cloudflare
 * only for now; the AAAA target is the device's own global IPv6 (auto).
 */
class DdnsFragment : Fragment(R.layout.fragment_ddns) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val token = view.findViewById<EditText>(R.id.etToken)
        val zone = view.findViewById<EditText>(R.id.etZone)
        val name = view.findViewById<EditText>(R.id.etName)
        val enabled = view.findViewById<CompoundButton>(R.id.swDdns)

        val f = Config.ddnsConfig(requireContext())
        if (f.exists()) runCatching {
            val o = JSONObject(f.readText())
            token.setText(o.optString("token"))
            zone.setText(o.optString("zoneId"))
            name.setText(o.optString("name"))
            enabled.isChecked = true
        }

        view.findViewById<Button>(R.id.btnSaveDdns).setOnClickListener {
            if (!enabled.isChecked) {
                f.delete()
                DdnsWorker.cancel(requireContext())
                toast("DDNS 已关闭")
                return@setOnClickListener
            }
            val t = token.text.toString().trim()
            val z = zone.text.toString().trim()
            val n = name.text.toString().trim()
            if (t.isEmpty() || z.isEmpty() || n.isEmpty()) {
                toast("请填全 Token / Zone ID / 域名"); return@setOnClickListener
            }
            val o = JSONObject()
                .put("provider", "cloudflare")
                .put("token", t)
                .put("zoneId", z)
                .put("name", n)
            f.writeText(o.toString(2))
            DdnsWorker.schedule(requireContext())
            toast("DDNS 已保存并启用")
        }
    }

    private fun toast(m: String) = Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show()
}
