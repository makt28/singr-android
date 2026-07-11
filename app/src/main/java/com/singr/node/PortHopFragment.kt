package com.singr.node

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import kotlin.concurrent.thread

/**
 * hysteria2 port hopping = NAT-redirect a UDP port range to the listen port.
 * That needs root on Android (iptables/nftables). If su is present we run the
 * rule; otherwise the controls are disabled with a "needs root" notice.
 */
class PortHopFragment : Fragment(R.layout.fragment_porthop) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val status = view.findViewById<TextView>(R.id.tvRootStatus)
        val apply = view.findViewById<Button>(R.id.btnApplyHop)
        val clear = view.findViewById<Button>(R.id.btnClearHop)
        val start = view.findViewById<EditText>(R.id.etRangeStart)
        val end = view.findViewById<EditText>(R.id.etRangeEnd)
        val target = view.findViewById<EditText>(R.id.etTarget)

        val prefs = requireContext().getSharedPreferences(Config.PREFS, 0)
        start.setText(prefs.getString("hop_start", ""))
        end.setText(prefs.getString("hop_end", ""))
        target.setText(prefs.getString("hop_target", ""))

        apply.isEnabled = false
        clear.isEnabled = false
        status.text = "检测 root 中…"

        // su probe can block; do it off the UI thread.
        thread {
            val rooted = RootShell.isRooted
            view.post {
                if (!isAdded) return@post
                status.text = if (rooted) "已获取 root，可应用规则" else "未检测到 root，本机无法使用端口跳跃"
                apply.isEnabled = rooted
                clear.isEnabled = rooted
            }
        }

        fun readRange(): Triple<Int, Int, Int>? {
            val s = start.text.toString().trim().toIntOrNull()
            val e = end.text.toString().trim().toIntOrNull()
            val t = target.text.toString().trim().toIntOrNull()
            if (s == null || e == null || t == null || s !in 1..65535 || e !in 1..65535 || t !in 1..65535 || s > e) {
                toast("请填写有效的端口范围和目标端口"); return null
            }
            prefs.edit().putString("hop_start", "$s").putString("hop_end", "$e").putString("hop_target", "$t").apply()
            return Triple(s, e, t)
        }

        apply.setOnClickListener {
            val (s, e, t) = readRange() ?: return@setOnClickListener
            thread {
                val r = RootShell.applyPortHop(s, e, t)
                view.post { toast(if (r.ok) "已应用：$s-$e → $t" else "失败：${r.err.ifBlank { r.out }}") }
            }
        }
        clear.setOnClickListener {
            val (s, e, t) = readRange() ?: return@setOnClickListener
            thread {
                RootShell.clearPortHop(s, e, t)
                view.post { toast("已清除规则") }
            }
        }
    }

    private fun toast(m: String) = Toast.makeText(requireContext(), m, Toast.LENGTH_LONG).show()
}
