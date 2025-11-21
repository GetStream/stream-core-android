package io.getstream.android.core.api.observers.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.getstream.android.core.api.model.connection.lifecycle.StreamLifecycleState
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager.Options
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager.Options.Retention
import io.getstream.android.core.testing.TestLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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
            val monitor = StreamLifecycleMonitor(TestLogger, owner.lifecycle, newSubscriptionManager())

            when (state) {
                Lifecycle.State.INITIALIZED -> Unit
                Lifecycle.State.CREATED -> owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
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

    private fun newSubscriptionManager(): StreamSubscriptionManager<StreamLifecycleListener> =
        StreamSubscriptionManager(TestLogger)

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle
            get() = registry
    }
}
