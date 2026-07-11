package com.singr.node

import android.util.Log

/** Runs commands as root via `su -c`. Used only for the port-hopping NAT rules. */
object RootShell {

    /** Whether a working `su` is present (cached per process after first probe). */
    val isRooted: Boolean by lazy { runAsRoot("id -u").let { it.ok && it.out.trim() == "0" } }

    data class Result(val ok: Boolean, val out: String, val err: String)

    fun runAsRoot(cmd: String): Result = try {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(false).start()
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        val code = p.waitFor()
        Result(code == 0, out, err)
    } catch (t: Throwable) {
        Log.w(Config.TAG, "su failed", t)
        Result(false, "", t.message ?: "no su")
    }

    /**
     * REDIRECT a UDP port range to a single target port, on both IPv4 and IPv6
     * (a v6 node still wants ip6tables). Idempotent-ish: callers should clear
     * first. `-A` to add, `-D` to remove.
     */
    private fun rule(op: String, start: Int, end: Int, target: Int): List<String> {
        val spec = "-t nat $op PREROUTING -p udp --dport $start:$end -j REDIRECT --to-ports $target"
        return listOf("iptables $spec", "ip6tables $spec")
    }

    fun applyPortHop(start: Int, end: Int, target: Int): Result {
        clearPortHop(start, end, target) // avoid stacking duplicates
        val cmd = rule("-A", start, end, target).joinToString(" && ")
        return runAsRoot(cmd)
    }

    fun clearPortHop(start: Int, end: Int, target: Int): Result {
        // Deleting a nonexistent rule errors; ignore failures by chaining with `;`.
        val cmd = rule("-D", start, end, target).joinToString("; ")
        return runAsRoot(cmd)
    }
}
