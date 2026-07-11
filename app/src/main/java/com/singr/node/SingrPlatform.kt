package com.singr.node

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.HandlerThread
import android.system.Os
import android.util.Log
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterface as LibboxInterface
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import java.net.NetworkInterface as JavaInterface

/**
 * The Android side of sing-box's PlatformInterface. Its whole reason to exist
 * here is [startDefaultInterfaceMonitor]: a bare linux build watches routes via
 * netlink, which Android sandboxes (the "subscribe route updates: permission
 * denied" that killed the subprocess). Feeding sing-box the default interface
 * from ConnectivityManager sidesteps netlink entirely.
 *
 * This is a server node (inbound-only, no tun), so tun/proxy/process methods are
 * inert stubs — sing-box never calls them for our config.
 */
class SingrPlatform(private val ctx: Context) : PlatformInterface {

    private val cm by lazy { ctx.getSystemService(ConnectivityManager::class.java) }
    private val callbacks = mutableMapOf<InterfaceUpdateListener, ConnectivityManager.NetworkCallback>()

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = notify(network, listener)
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                notify(network, listener, caps)
            override fun onLinkPropertiesChanged(network: Network, props: LinkProperties) =
                notify(network, listener)
        }
        synchronized(callbacks) { callbacks[listener] = callback }
        // Always deliver callbacks on another thread. In particular, never call
        // listener.updateDefaultInterface() while this Go -> Kotlin call is
        // still on the stack: re-entering Go synchronously can abort gomobile's
        // runtime with "stack split at bad time" (golang/go#68760).
        cm.registerDefaultNetworkCallback(callback, networkHandler)
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        val callback = synchronized(callbacks) { callbacks.remove(listener) } ?: return
        runCatching { cm.unregisterNetworkCallback(callback) }
    }

    private fun notify(
        network: Network,
        listener: InterfaceUpdateListener,
        caps: NetworkCapabilities? = cm.getNetworkCapabilities(network),
    ) {
        val name = cm.getLinkProperties(network)?.interfaceName ?: return
        val index = runCatching { Os.if_nametoindex(name) }.getOrDefault(0)
        val expensive = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
        runCatching {
            // isConstrained left false: the constrained-network capability isn't
            // public API, and it's only an advisory hint for a server node.
            listener.updateDefaultInterface(name, index, expensive, false)
        }.onFailure { Log.w(Config.TAG, "updateDefaultInterface", it) }
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        val out = ArrayList<LibboxInterface>()
        runCatching {
            for (nif in JavaInterface.getNetworkInterfaces()) {
                val entry = LibboxInterface()
                entry.name = nif.name
                entry.index = nif.index
                entry.mtu = runCatching { nif.mtu }.getOrDefault(0)
                entry.addresses = StringArrayIterator(
                    nif.interfaceAddresses.mapNotNull { interfaceAddress ->
                        val host = interfaceAddress.address?.hostAddress
                            ?.substringBefore('%')
                            ?: return@mapNotNull null
                        "$host/${interfaceAddress.networkPrefixLength}"
                    },
                )
                var flags = 0
                if (nif.isUp) flags = flags or 1        // IFF_UP
                if (nif.isLoopback) flags = flags or 8  // IFF_LOOPBACK
                if (nif.supportsMulticast()) flags = flags or 0x1000 // IFF_MULTICAST
                entry.flags = flags
                out.add(entry)
            }
        }.onFailure { Log.w(Config.TAG, "getInterfaces", it) }
        return NetworkInterfaceArrayIterator(out)
    }

    // --- inert stubs: not reached for an inbound-only server node ------------

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = false
    override fun autoDetectInterfaceControl(fd: Int) {}
    override fun useProcFS(): Boolean = false
    override fun openTun(options: TunOptions): Int = throw UnsupportedOperationException("no tun")
    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
    ): ConnectionOwner = throw UnsupportedOperationException("no process finder")

    override fun localDNSTransport(): LocalDNSTransport? = null
    override fun readWIFIState(): WIFIState? = null
    override fun systemCertificates(): StringIterator = StringArrayIterator(emptyList())
    override fun includeAllNetworks(): Boolean = false
    override fun underNetworkExtension(): Boolean = false
    override fun clearDNSCache() {}
    override fun sendNotification(notification: Notification) {}

    companion object {
        // Shared for the process: each BoxRunner restart creates a new platform
        // instance, so an instance-owned HandlerThread would leak on every run.
        private val networkThread = HandlerThread("singr-network").apply { start() }
        private val networkHandler = Handler(networkThread.looper)
    }
}
