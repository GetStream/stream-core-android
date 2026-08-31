/*
 * Copyright (c) 2014-2026 Stream.io Inc. All rights reserved.
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

import android.os.Handler
import android.os.Looper
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
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi

internal class StreamLifecycleMonitorImpl(
    private val logger: StreamLogger,
    private val subscriptionManager: StreamSubscriptionManager<StreamLifecycleListener>,
    private val lifecycle: Lifecycle,
) : StreamLifecycleMonitor, DefaultLifecycleObserver {

    private val started = AtomicBoolean(false)

    /** Attach/detach messages posted to the main looper but not yet executed. */
    private val pendingOnMain = AtomicInteger(0)

    override fun subscribe(
        listener: StreamLifecycleListener,
        options: StreamSubscriptionManager.Options,
    ): Result<StreamSubscription> = subscriptionManager.subscribe(listener, options)

    override fun start(): Result<Unit> = runCatching {
        if (!started.compareAndSet(false, true)) {
            return@runCatching
        }
        onMainLooper { lifecycle.addObserver(this) }
    }

    override fun stop(): Result<Unit> = runCatching {
        if (!started.compareAndSet(true, false)) {
            return@runCatching
        }
        onMainLooper { lifecycle.removeObserver(this) }
    }

    /**
     * Runs [block] on the main looper without waiting for it to complete.
     *
     * Attaching and detaching the observer has to happen on the main thread, but *waiting* for it
     * must not: a caller that already occupies the main looper — a suspend `disconnect()` bridged
     * with `runBlocking`, for example — would block the very thread it is waiting on, and the wait
     * could only ever end in a timeout.
     *
     * Running inline when the caller is already on the main looper is only safe while nothing of
     * ours is queued. Otherwise this call would jump ahead of an attach/detach posted earlier from
     * another thread and invert the two, leaving [started] and the observer disagreeing.
     */
    private fun onMainLooper(block: () -> Unit) {
        val mainLooper = Looper.getMainLooper() ?: error("Main looper is not initialized")
        if (Looper.myLooper() === mainLooper && pendingOnMain.get() == 0) {
            block()
            return
        }
        pendingOnMain.incrementAndGet()
        val posted =
            Handler(mainLooper).post {
                try {
                    block()
                } finally {
                    pendingOnMain.decrementAndGet()
                }
            }
        if (!posted) {
            pendingOnMain.decrementAndGet()
            error("Main looper is no longer accepting messages")
        }
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
