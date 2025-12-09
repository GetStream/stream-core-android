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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows

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

        // Continuously process main looper tasks while waiting for thread to complete
        while (!completed.await(10, TimeUnit.MILLISECONDS)) {
            shadowLooper.idle()
        }

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

        // Continuously process main looper tasks while waiting for thread to complete
        while (!completed.await(10, TimeUnit.MILLISECONDS)) {
            shadowLooper.idle()
        }

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

            // Continuously process main looper tasks while waiting for thread to complete
            while (!startCompleted.await(10, TimeUnit.MILLISECONDS)) {
                shadowLooper.idle()
            }

            assertEquals(mainLooper.thread, owner.addObserverThread.get(),
                "addObserver should be called on main thread in iteration $iteration")

            val stopCompleted = CountDownLatch(1)
            thread(start = true, name = "StreamLifecycleMonitorTest-stop-$iteration") {
                monitor.stop().getOrThrow()
                stopCompleted.countDown()
            }

            // Continuously process main looper tasks while waiting for thread to complete
            while (!stopCompleted.await(10, TimeUnit.MILLISECONDS)) {
                shadowLooper.idle()
            }

            assertEquals(mainLooper.thread, owner.removeObserverThread.get(),
                "removeObserver should be called on main thread in iteration $iteration")
        }
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

        override val lifecycle: Lifecycle =
            object : Lifecycle() {
                override fun addObserver(observer: LifecycleObserver) {
                    addObserverThread.set(Thread.currentThread())
                    registry.addObserver(observer)
                }

                override fun removeObserver(observer: LifecycleObserver) {
                    removeObserverThread.set(Thread.currentThread())
                    registry.removeObserver(observer)
                }

                override val currentState: State
                    get() = registry.currentState
            }

    }
}
