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

    /** Guards the [started] flip together with the decision to run inline or to post. */
    private val transitionLock = Any()

    override fun subscribe(
        listener: StreamLifecycleListener,
        options: StreamSubscriptionManager.Options,
    ): Result<StreamSubscription> = subscriptionManager.subscribe(listener, options)

    override fun start(): Result<Unit> = runCatching {
        transition(from = false, to = true) { lifecycle.addObserver(this) }
    }

    override fun stop(): Result<Unit> = runCatching {
        transition(from = true, to = false) { lifecycle.removeObserver(this) }
    }

    /**
     * Flips [started] from [from] to [to] and runs [block] on the main looper without waiting for
     * it to complete.
     *
     * Attaching and detaching the observer has to happen on the main thread, but *waiting* for it
     * must not: a caller that already occupies the main looper — a suspend `disconnect()` bridged
     * with `runBlocking`, for example — would block the very thread it is waiting on, and the wait
     * could only ever end in a timeout.
     *
     * Running inline when the caller is already on the main looper is only safe while nothing of
     * ours is queued. Otherwise this call would jump ahead of an attach/detach posted earlier from
     * another thread and invert the two, leaving [started] and the observer disagreeing.
     *
     * The flip and that decision are taken under [transitionLock] as one step. Apart they leave a
     * window: a background `start()` that has won the flip but not yet posted looks like nothing is
     * queued, so a `stop()` arriving on the main thread in between detaches inline and the attach
     * lands after it — observer attached, [started] false. [block] itself runs outside the lock;
     * the inline path holds the main thread, so nothing of ours can overtake it there anyway, and
     * the lock stays clear of the listener callbacks the attach fans out to.
     */
    private fun transition(from: Boolean, to: Boolean, block: () -> Unit) {
        val mainLooper = Looper.getMainLooper() ?: error("Main looper is not initialized")
        val runInline =
            synchronized(transitionLock) {
                if (!started.compareAndSet(from, to)) {
                    return
                }
                val inline = Looper.myLooper() === mainLooper && pendingOnMain.get() == 0
                if (!inline) {
                    postToMainLooper(mainLooper, block)
                }
                inline
            }
        if (runInline) {
            block()
        }
    }

    private fun postToMainLooper(mainLooper: Looper, block: () -> Unit) {
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
