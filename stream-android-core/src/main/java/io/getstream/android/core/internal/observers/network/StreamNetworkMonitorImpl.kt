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
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.observers.network.StreamNetworkMonitor
import io.getstream.android.core.api.observers.network.StreamNetworkMonitorListener
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope

@StreamInternalApi
internal class StreamNetworkMonitorImpl(
    private val logger: StreamLogger,
    private val scope: CoroutineScope,
    private val streamSubscriptionManager: StreamSubscriptionManager<StreamNetworkMonitorListener>,
    private val snapshotBuilder: StreamNetworkSnapshotBuilder,
    private val connectivityManager: ConnectivityManager,
) : StreamNetworkMonitor {

    private val started = AtomicBoolean(false)
    private val callbackRef = AtomicReference<StreamNetworkMonitorCallback?>()

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

        val callback =
            StreamNetworkMonitorCallback(
                logger = logger,
                scope = scope,
                subscriptionManager = streamSubscriptionManager,
                snapshotBuilder = snapshotBuilder,
                connectivityManager = connectivityManager,
            )
        callbackRef.set(callback)

        try {
            registerCallback(callback)
            callback.onRegistered()
        } catch (throwable: Throwable) {
            logger.e(throwable) { "Failed to start network monitor" }
            safeUnregister(callback)
            callback.onCleared()
            cleanup()
            throw throwable
        }
    }

    override fun stop(): Result<Unit> = runCatching {
        val callback = callbackRef.getAndSet(null) ?: return@runCatching
        safeUnregister(callback)
        callback.onCleared()
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

    private fun cleanup() {
        started.set(false)
    }

    private fun safeUnregister(callback: ConnectivityManager.NetworkCallback) {
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            .onFailure { logger.w { "Failed to unregister network callback: ${it.message}" } }
    }
}
