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

package io.getstream.android.core.internal.observers

import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.connection.lifecycle.StreamLifecycleState
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo
import io.getstream.android.core.api.model.connection.network.StreamNetworkState
import io.getstream.android.core.api.observers.lifecycle.StreamLifecycleListener
import io.getstream.android.core.api.observers.lifecycle.StreamLifecycleMonitor
import io.getstream.android.core.api.observers.network.StreamNetworkMonitor
import io.getstream.android.core.api.observers.network.StreamNetworkMonitorListener
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.api.utils.flatMap
import io.getstream.android.core.api.utils.times
import io.getstream.android.core.api.utils.update
import kotlinx.coroutines.flow.MutableStateFlow

internal class StreamNetworkAndLifecycleMonitorImpl(
    private val logger: StreamLogger,
    private val networkMonitor: StreamNetworkMonitor,
    private val lifecycleMonitor: StreamLifecycleMonitor,
    private val mutableNetworkState: MutableStateFlow<StreamNetworkState>,
    private val mutableLifecycleState: MutableStateFlow<StreamLifecycleState>,
    private val subscriptionManager:
        StreamSubscriptionManager<StreamNetworkAndLifecycleMonitorListener>,
) : StreamNetworkAndLifeCycleMonitor {
    private var networkHandle: StreamSubscription? = null
    private var lifecycleHandle: StreamSubscription? = null
    private val lifecycleListener =
        object : StreamLifecycleListener {

            override fun onForeground() {
                logger.v { "Lifecycle foregrounded" }
                val lifecycleState = StreamLifecycleState.Foreground
                val networkState = mutableNetworkState.value
                mutableLifecycleState.update(lifecycleState)
                subscriptionManager.forEach {
                    it.onNetworkAndLifecycleState(networkState, lifecycleState)
                }
            }

            override fun onBackground() {
                logger.v { "Lifecycle backgrounded" }
                val lifecycleState = StreamLifecycleState.Background
                val networkState = mutableNetworkState.value
                mutableLifecycleState.update(lifecycleState)
                subscriptionManager.forEach {
                    it.onNetworkAndLifecycleState(networkState, lifecycleState)
                }
            }
        }
    private val networkMonitorListener =
        object : StreamNetworkMonitorListener {
            override suspend fun onNetworkConnected(snapshot: StreamNetworkInfo.Snapshot?) {
                logger.v { "Network connected: $snapshot" }
                val state = StreamNetworkState.Available(snapshot)
                mutableNetworkState.update(state)
                val lifecycleState = mutableLifecycleState.value
                subscriptionManager.forEach { it.onNetworkAndLifecycleState(state, lifecycleState) }
            }

            override suspend fun onNetworkLost(permanent: Boolean) {
                logger.v { "Network lost" }
                val state =
                    if (permanent) {
                        StreamNetworkState.Unavailable
                    } else {
                        StreamNetworkState.Disconnected
                    }
                mutableNetworkState.update(state)
                val lifecycleState = mutableLifecycleState.value
                subscriptionManager.forEach { it.onNetworkAndLifecycleState(state, lifecycleState) }
            }
        }

    override fun start(): Result<Unit> {
        val lifecycleStart =
            lifecycleMonitor
                .start()
                .flatMap { lifecycleMonitor.subscribe(lifecycleListener) }
                .also { result -> lifecycleHandle = result.getOrNull() }
        mutableLifecycleState.update(lifecycleMonitor.getCurrentState())

        val networkStart =
            networkMonitor
                // Subscribe before start so we get notified about the initial state
                .subscribe(networkMonitorListener)
                .flatMap { subscription -> networkMonitor.start().map { subscription } }
                .also { result -> networkHandle = result.getOrNull() }

        return (networkStart * lifecycleStart).flatMap { Result.success(Unit) }
    }

    override fun stop(): Result<Unit> {
        networkHandle?.cancel()
        lifecycleHandle?.cancel()
        networkHandle = null
        lifecycleHandle = null
        mutableNetworkState.update(StreamNetworkState.Unknown)
        mutableLifecycleState.update(StreamLifecycleState.Unknown)
        subscriptionManager.clear()
        return (lifecycleMonitor.stop() * networkMonitor.stop()).flatMap { Result.success(Unit) }
    }

    override fun subscribe(
        listener: StreamNetworkAndLifecycleMonitorListener,
        options: StreamSubscriptionManager.Options,
    ): Result<StreamSubscription> = subscriptionManager.subscribe(listener, options)
}
