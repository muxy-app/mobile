package com.muxy.app.networking.muxy1.discovery

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import com.muxy.app.core.logging.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executor

class NsdDiscovery(
    private val nsd: NsdManager,
) : ServiceDiscovery {
    private val lock = Any()
    private val executor = Executor(Runnable::run)
    private val mutableServices = MutableStateFlow<List<DiscoveredService>>(emptyList())
    private val infoCallbacks = mutableMapOf<String, NsdManager.ServiceInfoCallback>()
    private val pendingResolves = ArrayDeque<NsdServiceInfo>()
    private var isResolving = false
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    override val services: StateFlow<List<DiscoveredService>> = mutableServices.asStateFlow()

    override fun start() {
        val listener =
            synchronized(lock) {
                if (discoveryListener != null) return
                DiscoveryListener().also { discoveryListener = it }
            }
        nsd.discoverServices(NsdMapping.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    override fun stop() {
        val listener =
            synchronized(lock) {
                val current = discoveryListener ?: return
                discoveryListener = null
                pendingResolves.clear()
                isResolving = false
                current
            }
        runCatching { nsd.stopServiceDiscovery(listener) }.onFailure { Log.discovery.error("Stopping discovery failed", it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) unregisterAllInfoCallbacks()
        mutableServices.value = emptyList()
    }

    private fun serviceFound(info: NsdServiceInfo) {
        val name = info.serviceName ?: return
        if (mutableServices.value.any { it.name == name }) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerInfoCallback(name, info)
            return
        }
        enqueueResolve(info)
    }

    private fun serviceLost(info: NsdServiceInfo) {
        val name = info.serviceName ?: return
        mutableServices.update { it.removing(name) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) unregisterInfoCallback(name)
    }

    private fun resolved(info: NsdServiceInfo) {
        if (synchronized(lock) { discoveryListener == null }) return
        val service = NsdMapping.service(info.serviceName.orEmpty(), hostAddresses(info), info.port) ?: return
        mutableServices.update { it.upserting(service) }
    }

    private fun hostAddresses(info: NsdServiceInfo): List<String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return info.hostAddresses.mapNotNull { it.hostAddress }
        return legacyHostAddresses(info)
    }

    @Suppress("DEPRECATION")
    private fun legacyHostAddresses(info: NsdServiceInfo): List<String> = listOfNotNull(info.host?.hostAddress)

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun registerInfoCallback(
        name: String,
        info: NsdServiceInfo,
    ) {
        val callback =
            synchronized(lock) {
                if (discoveryListener == null || infoCallbacks.containsKey(name)) return
                InfoCallback(name).also { infoCallbacks[name] = it }
            }
        runCatching { nsd.registerServiceInfoCallback(info, executor, callback) }.onFailure {
            Log.discovery.error("Resolving a service failed", it)
            synchronized(lock) { infoCallbacks.remove(name) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun unregisterInfoCallback(name: String) {
        val callback = synchronized(lock) { infoCallbacks.remove(name) } ?: return
        runCatching { nsd.unregisterServiceInfoCallback(callback) }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun unregisterAllInfoCallbacks() {
        val callbacks =
            synchronized(lock) {
                infoCallbacks.values.toList().also { infoCallbacks.clear() }
            }
        callbacks.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } }
    }

    private fun enqueueResolve(info: NsdServiceInfo) {
        synchronized(lock) {
            if (discoveryListener == null) return
            if (pendingResolves.any { it.serviceName == info.serviceName }) return
            pendingResolves.addLast(info)
        }
        resolveNext()
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val next =
            synchronized(lock) {
                if (isResolving) return
                val info = pendingResolves.removeFirstOrNull() ?: return
                isResolving = true
                info
            }
        nsd.resolveService(next, LegacyResolveListener())
    }

    private fun resolveFinished() {
        synchronized(lock) { isResolving = false }
        resolveNext()
    }

    private inner class DiscoveryListener : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            Log.discovery.debug("Discovery started")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.discovery.debug("Discovery stopped")
        }

        override fun onStartDiscoveryFailed(
            serviceType: String,
            errorCode: Int,
        ) {
            Log.discovery.error("Discovery failed to start: $errorCode")
            synchronized(lock) { if (discoveryListener === this) discoveryListener = null }
        }

        override fun onStopDiscoveryFailed(
            serviceType: String,
            errorCode: Int,
        ) {
            Log.discovery.error("Discovery failed to stop: $errorCode")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            serviceFound(serviceInfo)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            serviceLost(serviceInfo)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private inner class InfoCallback(
        private val name: String,
    ) : NsdManager.ServiceInfoCallback {
        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
            Log.discovery.error("Resolving a service failed: $errorCode")
            synchronized(lock) { infoCallbacks.remove(name) }
        }

        override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
            resolved(serviceInfo)
        }

        override fun onServiceLost() {
            mutableServices.update { it.removing(name) }
        }

        override fun onServiceInfoCallbackUnregistered() {
            Log.discovery.debug("Stopped resolving a service")
        }
    }

    private inner class LegacyResolveListener : NsdManager.ResolveListener {
        override fun onResolveFailed(
            serviceInfo: NsdServiceInfo,
            errorCode: Int,
        ) {
            Log.discovery.error("Resolving a service failed: $errorCode")
            resolveFinished()
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            resolved(serviceInfo)
            resolveFinished()
        }
    }
}
