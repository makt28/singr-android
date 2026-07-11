package com.singr.node

import android.content.Context
import android.util.Log
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.SystemProxyStatus
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Runs the SingR core in-process via libbox (the gomobile AAR) instead of
 * exec'ing a standalone binary. libbox feeds sing-box a PlatformInterface
 * ([SingrPlatform]) so interface monitoring comes from ConnectivityManager, not
 * netlink — which is what the bare-binary path could never get past on Android.
 *
 * poet starts automatically inside box.Start() (SingR's Box.preStart), reading
 * panel.json from the libbox working dir (wired in SingR's libbox Setup).
 */
class BoxRunner(private val ctx: Context) : CommandServerHandler {

    private val stopping = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private var commandServer: CommandServer? = null
    private var logTail: Thread? = null

    @Synchronized
    fun start() {
        if (!started.compareAndSet(false, true)) return
        stopping.set(false)
        try {
            ensureSetup(ctx)
            Config.boxLog(ctx).writeText("") // fresh log each launch; the tail follows it
            startLogTail()
            val server = Libbox.newCommandServer(this, SingrPlatform(ctx))
            server.start()
            commandServer = server
            NodeLog.append("starting box (libbox)")
            server.startOrReloadService(Config.serverConfig(ctx).readText(), OverrideOptions())
            NodeLog.append("box started")
            NodeLog.setState(NodeLog.State.RUNNING)
        } catch (t: Throwable) {
            Log.e(Config.TAG, "box start failed", t)
            NodeLog.append("box start failed: ${t.message}")
            stopInternal()
            started.set(false)
            NodeLog.setState(NodeLog.State.STOPPED)
        }
    }

    @Synchronized
    fun stop() {
        stopping.set(true)
        started.set(false)
        stopInternal()
        NodeLog.append("stopped by user")
        NodeLog.setState(NodeLog.State.STOPPED)
    }

    private fun stopInternal() {
        commandServer?.let { s ->
            runCatching { s.closeService() }
            runCatching { s.close() }
        }
        commandServer = null
        logTail?.interrupt()
        logTail = null
    }

    /** sing-box writes log.output to [Config.boxLog]; stream new lines to NodeLog. */
    private fun startLogTail() {
        logTail = thread(name = "box-log", isDaemon = true) {
            try {
                RandomAccessFile(Config.boxLog(ctx), "r").use { raf ->
                    while (!stopping.get() && !Thread.interrupted()) {
                        val line = raf.readLine()
                        if (line == null) Thread.sleep(300) else NodeLog.append(line)
                    }
                }
            } catch (_: InterruptedException) {
            } catch (t: Throwable) {
                Log.w(Config.TAG, "log tail ended", t)
            }
        }
    }

    // --- CommandServerHandler (mostly inert for a server node) ---------------

    override fun serviceReload() {
        commandServer?.let { s ->
            runCatching {
                s.startOrReloadService(Config.serverConfig(ctx).readText(), OverrideOptions())
            }
        }
    }

    override fun serviceStop() {
        stopping.set(true)
        started.set(false)
        NodeLog.append("box requested stop")
        stopInternal()
        NodeLog.setState(NodeLog.State.STOPPED)
    }

    override fun getSystemProxyStatus(): SystemProxyStatus =
        SystemProxyStatus().apply { available = false; enabled = false }

    override fun setSystemProxyEnabled(isEnabled: Boolean) {}

    override fun writeDebugMessage(message: String) {
        NodeLog.append(message)
    }

    companion object {
        // Libbox.setup is process-global and must run exactly once.
        @Volatile
        private var setupDone = false

        @Synchronized
        private fun ensureSetup(ctx: Context) {
            if (setupDone) return
            Libbox.setup(
                SetupOptions().apply {
                    basePath = Config.workDir(ctx).absolutePath
                    workingPath = Config.workDir(ctx).absolutePath
                    tempPath = ctx.cacheDir.absolutePath
                    // Required on Android: mitigates a Go runtime stack crash
                    // (golang/go#68760). Without it, the box crashes the process
                    // natively the moment it starts. SFA sets this too.
                    fixAndroidStack = true
                },
            )
            // Commit the process-global state only after setup succeeds, so a
            // recoverable setup exception does not poison every later retry.
            setupDone = true
            runCatching {
                Libbox.redirectStderr(File(Config.workDir(ctx), "stderr.log").absolutePath)
            }
        }
    }
}
