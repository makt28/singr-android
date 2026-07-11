package com.singr.node

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Process-wide, in-memory view of the core subprocess: a coarse run state plus a
 * bounded ring buffer of recent log lines. The VpnService/NativeRunner and the
 * Activity share one app process, so the UI can read this directly — no IPC.
 *
 * All logs already go to logcat; this exists so the state is visible *in the
 * app*, since on a headless node device logcat isn't reachable.
 */
object NodeLog {

    enum class State { STOPPED, RUNNING, RESTARTING }

    private const val MAX_LINES = 400
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Volatile
    var state: State = State.STOPPED
        private set

    /** Single UI observer; invoked (possibly off the main thread) on any change. */
    @Volatile
    private var listener: (() -> Unit)? = null

    fun observe(l: (() -> Unit)?) { listener = l }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    @Synchronized
    fun append(line: String) {
        lines.addLast("${fmt.format(Date())}  $line")
        while (lines.size > MAX_LINES) lines.removeFirst()
        listener?.invoke()
    }

    fun setState(s: State) {
        state = s
        listener?.invoke()
    }

    @Synchronized
    fun clear() {
        lines.clear()
        listener?.invoke()
    }
}
