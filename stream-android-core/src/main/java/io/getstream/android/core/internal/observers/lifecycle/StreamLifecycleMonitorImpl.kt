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

package io.getstream.android.core.internal.observers.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.connection.lifecycle.StreamLifecycleState
import io.getstream.android.core.api.observers.lifecycle.StreamLifecycleListener
import io.getstream.android.core.api.observers.lifecycle.StreamLifecycleMonitor
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.ExperimentalCoroutinesApi

internal class StreamLifecycleMonitorImpl(
    private val logger: StreamLogger,
    private val subscriptionManager: StreamSubscriptionManager<StreamLifecycleListener>,
    private val lifecycle: Lifecycle,
) : StreamLifecycleMonitor, DefaultLifecycleObserver {

    private val started = AtomicBoolean(false)

    override fun subscribe(
        listener: StreamLifecycleListener,
        options: StreamSubscriptionManager.Options,
    ): Result<StreamSubscription> = subscriptionManager.subscribe(listener, options)

    override fun start(): Result<Unit> = runCatching {
        if (!started.compareAndSet(false, true)) {
            return@runCatching
        }
        lifecycle.addObserver(this)
    }

    override fun stop(): Result<Unit> = runCatching {
        if (!started.compareAndSet(true, false)) {
            return@runCatching
        }
        lifecycle.removeObserver(this)
    }

    override fun onResume(owner: LifecycleOwner) {
        notifyListeners { it.onForeground() }
    }

    override fun onPause(owner: LifecycleOwner) {
        notifyListeners { it.onBackground() }
    }

    private fun notifyListeners(block: (StreamLifecycleListener) -> Unit) {
        subscriptionManager
            .forEach { listener ->
                runCatching { block(listener) }
                    .onFailure { throwable ->
                        logger.e(throwable) {
                            "StreamLifecycleListener block() failed when notifying"
                        }
                    }
            }
            .onFailure { throwable ->
                logger.e(throwable) { "Failed to iterate lifecycle listeners" }
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getCurrentState(): StreamLifecycleState =
        when (lifecycle.currentState) {
            Lifecycle.State.INITIALIZED -> StreamLifecycleState.Unknown
            Lifecycle.State.DESTROYED -> StreamLifecycleState.Background
            Lifecycle.State.CREATED -> StreamLifecycleState.Background
            Lifecycle.State.STARTED -> StreamLifecycleState.Background
            Lifecycle.State.RESUMED -> StreamLifecycleState.Foreground
        }
}
