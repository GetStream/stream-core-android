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

import io.getstream.android.core.api.model.connection.lifecycle.StreamLifecycleState
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo
import io.getstream.android.core.api.model.connection.network.StreamNetworkState
import io.getstream.android.core.api.observers.lifecycle.StreamLifecycleListener
import io.getstream.android.core.api.observers.lifecycle.StreamLifecycleMonitor
import io.getstream.android.core.api.observers.network.StreamNetworkMonitor
import io.getstream.android.core.api.observers.network.StreamNetworkMonitorListener
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager.Options
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager.Options.Retention
import io.getstream.android.core.testing.TestLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalTime::class)
class StreamNetworkAndLifecycleMonitorImplTest {

    private val networkState = MutableStateFlow<StreamNetworkState>(StreamNetworkState.Unknown)
    private val lifecycleState =
        MutableStateFlow<StreamLifecycleState>(StreamLifecycleState.Unknown)
    private val downstreamSubscriptionManager =
        StreamSubscriptionManager<StreamNetworkAndLifecycleMonitorListener>(TestLogger)
    private val fakeLifecycleMonitor = FakeLifecycleMonitor()
    private val fakeNetworkMonitor = FakeNetworkMonitor()
    private val monitor: StreamNetworkAndLifeCycleMonitor = createMonitor()
    private val options = Options(retention = Retention.KEEP_UNTIL_CANCELLED)

    @Test
    fun `network callbacks update state and notify listeners`() {
        val events = mutableListOf<Pair<StreamNetworkState, StreamLifecycleState>>()
        monitor
            .subscribe(
                object : StreamNetworkAndLifecycleMonitorListener {
                    override fun onNetworkAndLifecycleState(
                        networkState: StreamNetworkState,
                        lifecycleState: StreamLifecycleState,
                    ) {
                        events += networkState to lifecycleState
                    }
                },
                options,
            )
            .getOrThrow()

        monitor.start().getOrThrow()

        val snapshot = StreamNetworkInfo.Snapshot()
        fakeNetworkMonitor.emitConnected(snapshot)
        assertEquals(StreamNetworkState.Available(snapshot), networkState.value)

        fakeNetworkMonitor.emitLost(permanent = false)
        assertTrue(networkState.value is StreamNetworkState.Disconnected)

        fakeNetworkMonitor.emitLost(permanent = true)
        assertEquals(StreamNetworkState.Unavailable, networkState.value)

        assertEquals(
            listOf(
                StreamNetworkState.Available(snapshot) to lifecycleState.value,
                StreamNetworkState.Disconnected to lifecycleState.value,
                StreamNetworkState.Unavailable to lifecycleState.value,
            ),
            events,
        )
    }

    @Test
    fun `network and lifecycle states are updated on start`() {
        val initialNetworkState =
            StreamNetworkState.Available(StreamNetworkInfo.Snapshot(internet = true))
        val initialLifecycleState = StreamLifecycleState.Foreground

        val monitor =
            createMonitor(
                networkMonitor = FakeNetworkMonitor(initialNetworkState.snapshot),
                lifecycleMonitor = FakeLifecycleMonitor(initialLifecycleState),
            )
        monitor.start()

        assertEquals(initialNetworkState, networkState.value)
        assertEquals(initialLifecycleState, lifecycleState.value)
    }

    @Test
    fun `lifecycle callbacks update state and notify listeners`() {
        val events = mutableListOf<Pair<StreamNetworkState, StreamLifecycleState>>()
        monitor
            .subscribe(
                object : StreamNetworkAndLifecycleMonitorListener {
                    override fun onNetworkAndLifecycleState(
                        networkState: StreamNetworkState,
                        lifecycleState: StreamLifecycleState,
                    ) {
                        events += networkState to lifecycleState
                    }
                },
                options,
            )
            .getOrThrow()

        monitor.start().getOrThrow()

        fakeLifecycleMonitor.emitForeground()
        assertEquals(StreamLifecycleState.Foreground, lifecycleState.value)

        fakeLifecycleMonitor.emitBackground()
        assertEquals(StreamLifecycleState.Background, lifecycleState.value)

        assertEquals(
            listOf(
                networkState.value to StreamLifecycleState.Foreground,
                networkState.value to StreamLifecycleState.Background,
            ),
            events,
        )
    }

    @Test
    fun `stop resets states and detaches listeners`() {
        val events = mutableListOf<Pair<StreamNetworkState, StreamLifecycleState>>()
        monitor
            .subscribe(
                object : StreamNetworkAndLifecycleMonitorListener {
                    override fun onNetworkAndLifecycleState(
                        networkState: StreamNetworkState,
                        lifecycleState: StreamLifecycleState,
                    ) {
                        events += networkState to lifecycleState
                    }
                },
                options,
            )
            .getOrThrow()

        monitor.start().getOrThrow()
        fakeLifecycleMonitor.emitForeground()
        fakeNetworkMonitor.emitConnected(StreamNetworkInfo.Snapshot())

        monitor.stop().getOrThrow()

        assertEquals(StreamLifecycleState.Unknown, lifecycleState.value)
        assertEquals(StreamNetworkState.Unknown, networkState.value)
        assertEquals(1, fakeLifecycleMonitor.stopCalls)
        assertEquals(1, fakeNetworkMonitor.stopCalls)
        assertFalse(fakeLifecycleMonitor.hasListeners())
        assertFalse(fakeNetworkMonitor.hasListener())

        val previousEventCount = events.size
        fakeLifecycleMonitor.emitForeground()
        fakeNetworkMonitor.emitConnected(StreamNetworkInfo.Snapshot())
        assertEquals(previousEventCount, events.size)
    }

    private class FakeLifecycleMonitor(
        private val initialState: StreamLifecycleState = StreamLifecycleState.Unknown
    ) : StreamLifecycleMonitor {
        private val listeners = mutableSetOf<StreamLifecycleListener>()
        var stopCalls: Int = 0
            private set

        override fun start(): Result<Unit> = Result.success(Unit)

        override fun stop(): Result<Unit> =
            Result.success(Unit).also {
                stopCalls++
                listeners.clear()
            }

        override fun subscribe(
            listener: StreamLifecycleListener,
            options: Options,
        ): Result<StreamSubscription> {
            listeners += listener
            return Result.success(
                object : StreamSubscription {
                    override fun cancel() {
                        listeners -= listener
                    }
                }
            )
        }

        override fun getCurrentState(): StreamLifecycleState = initialState

        fun emitForeground() {
            listeners.forEach { it.onForeground() }
        }

        fun emitBackground() {
            listeners.forEach { it.onBackground() }
        }

        fun hasListeners(): Boolean = listeners.isNotEmpty()
    }

    private class FakeNetworkMonitor(
        private val initialSnapshot: StreamNetworkInfo.Snapshot? = null
    ) : StreamNetworkMonitor {
        private var listener: StreamNetworkMonitorListener? = null
        var stopCalls: Int = 0
            private set

        override fun start(): Result<Unit> {
            initialSnapshot?.let(::emitConnected)
            return Result.success(Unit)
        }

        override fun stop(): Result<Unit> =
            Result.success(Unit).also {
                stopCalls++
                listener = null
            }

        override fun subscribe(
            listener: StreamNetworkMonitorListener,
            options: Options,
        ): Result<StreamSubscription> {
            this.listener = listener
            return Result.success(
                object : StreamSubscription {
                    override fun cancel() {
                        if (this@FakeNetworkMonitor.listener === listener) {
                            this@FakeNetworkMonitor.listener = null
                        }
                    }
                }
            )
        }

        fun emitConnected(snapshot: StreamNetworkInfo.Snapshot?) {
            runBlocking { listener?.onNetworkConnected(snapshot) }
        }

        fun emitLost(permanent: Boolean) {
            runBlocking { listener?.onNetworkLost(permanent) }
        }

        fun hasListener(): Boolean = listener != null
    }

    private fun createMonitor(
        networkMonitor: StreamNetworkMonitor = fakeNetworkMonitor,
        lifecycleMonitor: StreamLifecycleMonitor = fakeLifecycleMonitor,
    ) =
        StreamNetworkAndLifeCycleMonitor(
            logger = TestLogger,
            lifecycleMonitor = lifecycleMonitor,
            networkMonitor = networkMonitor,
            mutableLifecycleState = lifecycleState,
            mutableNetworkState = networkState,
            subscriptionManager = downstreamSubscriptionManager,
        )
}
