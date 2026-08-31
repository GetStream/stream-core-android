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

package io.getstream.android.core.api.observers.lifecycle

import android.os.Build
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.getstream.android.core.api.model.connection.lifecycle.StreamLifecycleState
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager.Options
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager.Options.Retention
import io.getstream.android.core.testing.TestLogger
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class StreamLifecycleMonitorTest {

    @Test
    fun `start forwards lifecycle events to listeners`() {
        val owner = TestLifecycleOwner()
        val monitor = StreamLifecycleMonitor(TestLogger, owner.lifecycle, newSubscriptionManager())
        val received = mutableListOf<String>()

        val listener =
            object : StreamLifecycleListener {
                override fun onForeground() {
                    received += "fg"
                }

                override fun onBackground() {
                    received += "bg"
                }
            }
        val noopListener = object : StreamLifecycleListener {}

        val options = Options(retention = Retention.KEEP_UNTIL_CANCELLED)
        val subscription = monitor.subscribe(listener, options).getOrThrow()
        monitor.subscribe(noopListener, options).getOrThrow()

        monitor.start().getOrThrow()

        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)

        assertEquals(listOf("fg", "bg"), received)

        monitor.stop().getOrThrow()
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertEquals(2, received.size)

        subscription.cancel()
    }

    @Test
    fun `getCurrentState reflects lifecycle state`() {
        val expectations =
            listOf(
                Lifecycle.State.INITIALIZED to StreamLifecycleState.Unknown,
                Lifecycle.State.CREATED to StreamLifecycleState.Background,
                Lifecycle.State.STARTED to StreamLifecycleState.Background,
                Lifecycle.State.RESUMED to StreamLifecycleState.Foreground,
                Lifecycle.State.DESTROYED to StreamLifecycleState.Background,
            )

        expectations.forEach { (state, expected) ->
            val owner = TestLifecycleOwner()
            val monitor =
                StreamLifecycleMonitor(TestLogger, owner.lifecycle, newSubscriptionManager())

            when (state) {
                Lifecycle.State.INITIALIZED -> Unit
                Lifecycle.State.CREATED ->
                    owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                Lifecycle.State.STARTED -> {
                    owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                    owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                }
                Lifecycle.State.RESUMED -> {
                    owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                    owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                    owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                }
                Lifecycle.State.DESTROYED -> {
                    owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                    owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                    owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                    owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                    owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                    owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                }
            }

            assertEquals(expected, monitor.getCurrentState())
        }
    }

    @Test
    fun `lifecycle listener default callbacks are no-op`() {
        val listener = object : StreamLifecycleListener {}

        listener.onForeground()
        listener.onBackground()

        assertTrue(true)
    }

    @Test
    fun `start posts lifecycle registration to main thread`() {
        val owner = RecordingLifecycleOwner()
        val monitor = StreamLifecycleMonitor(TestLogger, owner.lifecycle, newSubscriptionManager())
        val completed = CountDownLatch(1)
        val mainLooper = Looper.getMainLooper()
        val shadowLooper = Shadows.shadowOf(mainLooper)

        thread(start = true, name = "StreamLifecycleMonitorTest-worker") {
            monitor.start().getOrThrow()
            completed.countDown()
        }

        // start() queues the registration and returns; it must not wait on the main looper.
        assertTrue(completed.await(5, TimeUnit.SECONDS))
        shadowLooper.idle()

        assertEquals(mainLooper.thread, owner.addObserverThread.get())

        monitor.stop().getOrThrow()
    }

    @Test
    fun `stop posts lifecycle removal to main thread`() {
        val owner = RecordingLifecycleOwner()
        val monitor = StreamLifecycleMonitor(TestLogger, owner.lifecycle, newSubscriptionManager())
        val mainLooper = Looper.getMainLooper()
        val shadowLooper = Shadows.shadowOf(mainLooper)

        // First start on main thread
        monitor.start().getOrThrow()
        shadowLooper.idle()

        // Then stop from background thread
        val completed = CountDownLatch(1)
        thread(start = true, name = "StreamLifecycleMonitorTest-worker") {
            monitor.stop().getOrThrow()
            completed.countDown()
        }

        // stop() queues the removal and returns; it must not wait on the main looper.
        assertTrue(completed.await(5, TimeUnit.SECONDS))
        shadowLooper.idle()

        assertEquals(mainLooper.thread, owner.removeObserverThread.get())
    }

    @Test
    fun `start and stop from multiple background threads always use main thread`() {
        val owner = RecordingLifecycleOwner()
        val monitor = StreamLifecycleMonitor(TestLogger, owner.lifecycle, newSubscriptionManager())
        val mainLooper = Looper.getMainLooper()
        val shadowLooper = Shadows.shadowOf(mainLooper)

        // Test multiple start/stop cycles from different threads
        repeat(3) { iteration ->
            val startCompleted = CountDownLatch(1)
            thread(start = true, name = "StreamLifecycleMonitorTest-start-$iteration") {
                monitor.start().getOrThrow()
                startCompleted.countDown()
            }

            assertTrue(startCompleted.await(5, TimeUnit.SECONDS))
            shadowLooper.idle()

            assertEquals(
                mainLooper.thread,
                owner.addObserverThread.get(),
                "addObserver should be called on main thread in iteration $iteration",
            )

            val stopCompleted = CountDownLatch(1)
            thread(start = true, name = "StreamLifecycleMonitorTest-stop-$iteration") {
                monitor.stop().getOrThrow()
                stopCompleted.countDown()
            }

            assertTrue(stopCompleted.await(5, TimeUnit.SECONDS))
            shadowLooper.idle()

            assertEquals(
                mainLooper.thread,
                owner.removeObserverThread.get(),
                "removeObserver should be called on main thread in iteration $iteration",
            )
        }
    }

    // Regression for AND-1468: start() used to block the caller on a five second latch while
    // waiting for the main looper to run the registration. A caller that is itself holding the
    // main looper can never release it, so the wait could only ever end in the timeout. Here
    // the looper is simply never idled, which has the same shape.
    @Test
    fun `start returns without waiting for the main looper to run`() {
        val owner = RecordingLifecycleOwner()
        val monitor = StreamLifecycleMonitor(TestLogger, owner.lifecycle, newSubscriptionManager())
        val completed = CountDownLatch(1)
        val mainLooper = Looper.getMainLooper()

        thread(start = true, name = "StreamLifecycleMonitorTest-unblocked") {
            monitor.start().getOrThrow()
            completed.countDown()
        }

        assertTrue(completed.await(2, TimeUnit.SECONDS), "start must not wait on the main looper")
        assertNull(owner.addObserverThread.get(), "the registration is queued, not yet run")

        Shadows.shadowOf(mainLooper).idle()

        assertEquals(mainLooper.thread, owner.addObserverThread.get())
    }

    // A registration that is already queued must not be overtaken by a later call that happens
    // to run on the main thread, or the observer ends up in the opposite state to `started`.
    @Test
    fun `a main thread call does not overtake a registration queued from another thread`() {
        val owner = RecordingLifecycleOwner()
        val monitor = StreamLifecycleMonitor(TestLogger, owner.lifecycle, newSubscriptionManager())
        val started = CountDownLatch(1)
        val shadowLooper = Shadows.shadowOf(Looper.getMainLooper())

        thread(start = true, name = "StreamLifecycleMonitorTest-attach") {
            monitor.start().getOrThrow()
            started.countDown()
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))

        // The attach is still queued, so this stop() has to queue behind it rather than run
        // inline on the main thread.
        monitor.stop().getOrThrow()
        shadowLooper.idle()

        assertEquals(listOf("add", "remove"), owner.calls.toList())
    }

    private fun newSubscriptionManager(): StreamSubscriptionManager<StreamLifecycleListener> =
        StreamSubscriptionManager(TestLogger)

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle
            get() = registry
    }

    private class RecordingLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        val addObserverThread = AtomicReference<Thread?>()
        val removeObserverThread = AtomicReference<Thread?>()
        val calls = CopyOnWriteArrayList<String>()

        override val lifecycle: Lifecycle =
            object : Lifecycle() {
                override fun addObserver(observer: LifecycleObserver) {
                    addObserverThread.set(Thread.currentThread())
                    calls.add("add")
                    registry.addObserver(observer)
                }

                override fun removeObserver(observer: LifecycleObserver) {
                    removeObserverThread.set(Thread.currentThread())
                    calls.add("remove")
                    registry.removeObserver(observer)
                }

                override val currentState: State
                    get() = registry.currentState
            }
    }
}
