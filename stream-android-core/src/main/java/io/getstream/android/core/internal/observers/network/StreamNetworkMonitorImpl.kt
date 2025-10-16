/*
 * Copyright (c) 2014-2025 Stream.io Inc. All rights reserved.
 *
 * Licensed under the Stream License;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://github.com/GetStream/stream-core-android/blob/main/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.getstream.android.core.internal.observers.network

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo
import io.getstream.android.core.api.observers.network.StreamNetworkMonitor
import io.getstream.android.core.api.observers.network.StreamNetworkMonitorListener
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@StreamInternalApi
internal class StreamNetworkMonitorImpl(
    private val logger: StreamLogger,
    private val scope: CoroutineScope,
    private val streamSubscriptionManager: StreamSubscriptionManager<StreamNetworkMonitorListener>,
    private val snapshotBuilder: StreamNetworkSnapshotBuilder,
    private val connectivityManager: ConnectivityManager,
) : StreamNetworkMonitor {
    private val started = AtomicBoolean(false)
    private val networkCallbackRef = AtomicReference<ConnectivityManager.NetworkCallback?>()
    private val activeState = AtomicReference<ActiveNetworkState?>()

    override fun subscribe(
        listener: StreamNetworkMonitorListener,
        options: StreamSubscriptionManager.Options,
    ): Result<StreamSubscription> = streamSubscriptionManager.subscribe(listener, options)

    @SuppressLint("MissingPermission")
    override fun start(): Result<Unit> = runCatching {
        if (!started.compareAndSet(false, true)) {
            logger.v { "StreamNetworkMonitor already started" }
            return@runCatching
        }

        val callback = MonitorCallback()
        networkCallbackRef.set(callback)

        try {
            registerCallback(callback)
            resolveInitialState()?.also { initialState ->
                activeState.set(initialState)
                notifyConnected(initialState.snapshot)
            }
        } catch (throwable: Throwable) {
            logger.e(throwable) { "Failed to start network monitor" }
            safeUnregister(callback)
            cleanup()
            throw throwable
        }
    }

    override fun stop(): Result<Unit> = runCatching {
        val callback = networkCallbackRef.getAndSet(null)
        if (callback != null) {
            safeUnregister(callback)
        }
        cleanup()
    }

    private fun registerCallback(callback: ConnectivityManager.NetworkCallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } else {
            val request =
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
            connectivityManager.registerNetworkCallback(request, callback)
        }
    }

    private fun resolveInitialState(): ActiveNetworkState? {
        val defaultNetwork =
            resolveDefaultNetwork()
                ?: run {
                    logger.v { "No active network available at start" }
                    return null
                }
        val capabilities = connectivityManager.getNetworkCapabilities(defaultNetwork)
        val linkProperties = connectivityManager.getLinkProperties(defaultNetwork)
        val snapshot = buildSnapshot(defaultNetwork, capabilities, linkProperties) ?: return null
        return ActiveNetworkState(defaultNetwork, capabilities, linkProperties, snapshot)
    }

    private fun resolveDefaultNetwork(): Network? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connectivityManager.activeNetwork ?: connectivityManager.allNetworks.firstOrNull()
        } else {
            connectivityManager.allNetworks.firstOrNull()
        }

    private fun buildSnapshot(
        network: Network,
        capabilities: NetworkCapabilities?,
        linkProperties: LinkProperties?,
    ): StreamNetworkInfo.Snapshot? =
        snapshotBuilder.build(network, capabilities, linkProperties).getOrElse { throwable ->
            logger.e(throwable) { "Failed to assemble network snapshot" }
            null
        }

    private fun handleUpdate(
        network: Network,
        capabilities: NetworkCapabilities?,
        linkProperties: LinkProperties?,
        reason: UpdateReason,
    ) {
        if (!shouldProcessNetwork(network)) {
            logger.v { "[handleUpdate] Ignoring network $network; not default." }
            return
        }

        val resolvedCapabilities =
            capabilities ?: connectivityManager.getNetworkCapabilities(network)
        val resolvedLink = linkProperties ?: connectivityManager.getLinkProperties(network)
        val snapshot = buildSnapshot(network, resolvedCapabilities, resolvedLink)
        if (snapshot == null) {
            logger.v { "[handleUpdate] Snapshot unavailable; skipping notification." }
            return
        }

        val newState = ActiveNetworkState(network, resolvedCapabilities, resolvedLink, snapshot)
        val previousState = activeState.getAndSet(newState)

        val networkChanged = previousState?.network != network || previousState == null
        val snapshotChanged = previousState?.snapshot != snapshot

        when {
            reason == UpdateReason.AVAILABLE || networkChanged -> {
                logger.v { "[handleUpdate] Active network set to $network" }
                notifyConnected(snapshot)
            }

            snapshotChanged -> {
                logger.v { "[handleUpdate] Network properties updated for $network" }
                notifyPropertiesChanged(snapshot)
            }

            else -> logger.v { "[handleUpdate] No meaningful changes detected for $network" }
        }
    }

    private fun handleLoss(network: Network?, permanent: Boolean) {
        val current = activeState.get()
        if (current == null) {
            logger.v { "[handleLoss] No active network to clear." }
            return
        }

        if (network != null && network != current.network) {
            logger.v { "[handleLoss] Ignoring loss for non-active network: $network" }
            return
        }

        if (activeState.compareAndSet(current, null)) {
            logger.v { "[handleLoss] Network lost: ${current.network}" }
            notifyLost(permanent)
        }
    }

    private fun shouldProcessNetwork(network: Network): Boolean {
        val tracked = activeState.get()?.network
        if (tracked != null && tracked == network) return true
        return isDefaultNetwork(network)
    }

    private fun isDefaultNetwork(network: Network): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connectivityManager.activeNetwork == network
        } else {
            connectivityManager.allNetworks.firstOrNull() == network
        }

    private fun notifyConnected(snapshot: StreamNetworkInfo.Snapshot?) {
        if (snapshot == null) {
            return
        }
        notifyListeners { listener -> listener.onNetworkConnected(snapshot) }
    }

    private fun notifyPropertiesChanged(snapshot: StreamNetworkInfo.Snapshot) {
        notifyListeners { listener -> listener.onNetworkPropertiesChanged(snapshot) }
    }

    private fun notifyLost(permanent: Boolean = false) {
        notifyListeners { listener -> listener.onNetworkLost(permanent) }
    }

    private fun notifyListeners(block: suspend (StreamNetworkMonitorListener) -> Unit) {
        streamSubscriptionManager
            .forEach { listener ->
                scope.launch {
                    runCatching { block(listener) }
                        .onFailure { throwable ->
                            logger.e(throwable) { "Network monitor listener failure" }
                        }
                }
            }
            .onFailure { throwable ->
                logger.e(throwable) { "Failed to iterate network monitor listeners" }
            }
    }

    private fun cleanup() {
        activeState.set(null)
        started.set(false)
    }

    private fun safeUnregister(callback: ConnectivityManager.NetworkCallback) {
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            .onFailure { logger.w { "Failed to unregister network callback: ${it.message}" } }
    }

    private inner class MonitorCallback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            logger.v { "Network available: $network" }
            handleUpdate(network, null, null, UpdateReason.AVAILABLE)
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            logger.v { "Network capabilities changed for $network" }
            handleUpdate(network, networkCapabilities, null, UpdateReason.PROPERTIES)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            logger.v { "Link properties changed for $network" }
            handleUpdate(network, null, linkProperties, UpdateReason.PROPERTIES)
        }

        override fun onLost(network: Network) {
            handleLoss(network, permanent = false)
        }

        override fun onUnavailable() {
            handleLoss(network = null, permanent = true)
        }
    }

    private data class ActiveNetworkState(
        val network: Network,
        val capabilities: NetworkCapabilities?,
        val linkProperties: LinkProperties?,
        val snapshot: StreamNetworkInfo.Snapshot,
    )

    private enum class UpdateReason {
        AVAILABLE,
        PROPERTIES,
    }
}
