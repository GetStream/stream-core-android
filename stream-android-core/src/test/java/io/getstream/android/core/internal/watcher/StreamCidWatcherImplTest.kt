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

@file:OptIn(ExperimentalCoroutinesApi::class)

package io.getstream.android.core.internal.watcher

import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.StreamCid
import io.getstream.android.core.api.model.connection.StreamConnectedUser
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.socket.listeners.StreamClientListener
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.api.watcher.StreamCidRewatchListener
import io.getstream.android.core.api.watcher.StreamCidWatcher
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StreamCidWatcherImplTest {

    private lateinit var logger: StreamLogger
    private lateinit var rewatchSubscriptions: StreamSubscriptionManager<StreamCidRewatchListener>
    private lateinit var clientSubscriptions: StreamSubscriptionManager<StreamClientListener>
    private lateinit var watcher: StreamCidWatcher
    private lateinit var watched: ConcurrentHashMap<StreamCid, Unit>

    @Before
    fun setUp() {
        logger = mockk(relaxed = true)
        rewatchSubscriptions = mockk(relaxed = true)
        clientSubscriptions = mockk(relaxed = true)
        watched = ConcurrentHashMap()

        watcher =
            StreamCidWatcherImpl(
                watched = watched,
                rewatchSubscriptions = rewatchSubscriptions,
                clientSubscriptions = clientSubscriptions,
                logger = logger,
            )
    }

    // ========================================
    // Watch Operations
    // ========================================

    @Test
    fun `watch adds CID to registry`() {
        val cid = StreamCid.parse("messaging:general")

        val result = watcher.watch(cid)

        assertTrue(result.isSuccess)
        assertEquals(cid, result.getOrNull())
        assertTrue(watched.containsKey(cid))
    }

    @Test
    fun `watch same CID twice is idempotent`() {
        val cid = StreamCid.parse("messaging:general")

        watcher.watch(cid)
        watcher.watch(cid)

        assertEquals(1, watched.size)
        assertTrue(watched.containsKey(cid))
    }

    @Test
    fun `watch multiple different CIDs`() {
        val cid1 = StreamCid.parse("messaging:general")
        val cid2 = StreamCid.parse("livestream:sports")
        val cid3 = StreamCid.parse("messaging:random")

        watcher.watch(cid1)
        watcher.watch(cid2)
        watcher.watch(cid3)

        assertEquals(3, watched.size)
        assertTrue(watched.containsKey(cid1))
        assertTrue(watched.containsKey(cid2))
        assertTrue(watched.containsKey(cid3))
    }

    @Test
    fun `stopWatching removes CID from registry`() {
        val cid = StreamCid.parse("messaging:general")
        watcher.watch(cid)

        val result = watcher.stopWatching(cid)

        assertTrue(result.isSuccess)
        assertEquals(cid, result.getOrNull())
        assertFalse(watched.containsKey(cid))
    }

    @Test
    fun `stopWatching non-existent CID is a no-op`() {
        val cid = StreamCid.parse("messaging:general")

        val result = watcher.stopWatching(cid)

        assertTrue(result.isSuccess)
        assertEquals(cid, result.getOrNull())
        assertEquals(0, watched.size)
    }

    @Test
    fun `clear removes all CIDs from registry`() {
        watcher.watch(StreamCid.parse("messaging:general"))
        watcher.watch(StreamCid.parse("livestream:sports"))
        watcher.watch(StreamCid.parse("messaging:random"))

        val result = watcher.clear()

        assertTrue(result.isSuccess)
        assertEquals(0, watched.size)
    }

    @Test
    fun `clear on empty registry is a no-op`() {
        val result = watcher.clear()

        assertTrue(result.isSuccess)
        assertEquals(0, watched.size)
    }

    // ========================================
    // Lifecycle
    // ========================================

    @Test
    fun `start subscribes to client state changes`() {
        val mockSubscription = mockk<StreamSubscription>()
        every { clientSubscriptions.subscribe(any(), any()) } returns
            Result.success(mockSubscription)

        val result = watcher.start()

        assertTrue(result.isSuccess)
        verify(exactly = 1) {
            clientSubscriptions.subscribe(
                any(),
                StreamSubscriptionManager.Options(
                    retention = StreamSubscriptionManager.Options.Retention.KEEP_UNTIL_CANCELLED
                ),
            )
        }
    }

    @Test
    fun `start when already started is idempotent`() {
        val mockSubscription = mockk<StreamSubscription>()
        every { clientSubscriptions.subscribe(any(), any()) } returns
            Result.success(mockSubscription)

        watcher.start()
        watcher.start() // Second call

        // Should only subscribe once
        verify(exactly = 1) { clientSubscriptions.subscribe(any(), any()) }
    }

    @Test
    fun `start fails if subscription fails`() {
        val error = RuntimeException("Subscription failed")
        every { clientSubscriptions.subscribe(any(), any()) } returns Result.failure(error)

        val result = watcher.start()

        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    @Test
    fun `stop cancels subscription and scope`() {
        val mockSubscription = mockk<StreamSubscription>(relaxed = true)
        every { clientSubscriptions.subscribe(any(), any()) } returns
            Result.success(mockSubscription)

        watcher.start()
        val result = watcher.stop()

        assertTrue(result.isSuccess)
        verify { mockSubscription.cancel() }
    }

    @Test
    fun `stop when not started is a no-op`() {
        val result = watcher.stop()

        assertTrue(result.isSuccess)
    }

    @Test
    fun `multiple start and stop cycles`() {
        val mockSubscription1 = mockk<StreamSubscription>(relaxed = true)
        val mockSubscription2 = mockk<StreamSubscription>(relaxed = true)
        every { clientSubscriptions.subscribe(any(), any()) } returnsMany
            listOf(Result.success(mockSubscription1), Result.success(mockSubscription2))

        // Cycle 1
        watcher.start()
        watcher.stop()

        // Cycle 2
        watcher.start()
        watcher.stop()

        verify(exactly = 2) { clientSubscriptions.subscribe(any(), any()) }
        verify { mockSubscription1.cancel() }
        verify { mockSubscription2.cancel() }
    }

    // ========================================
    // State Change Handling
    // ========================================

    @Test
    fun `Connected state triggers rewatch with watched CIDs`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)

        // Setup: Capture the listener
        val clientListenerSlot = slot<StreamClientListener>()
        val mockSubscription = mockk<StreamSubscription>(relaxed = true)
        every { clientSubscriptions.subscribe(capture(clientListenerSlot), any()) } returns
            Result.success(mockSubscription)

        // Setup: Mock rewatch subscriptions
        val rewatchListeners = mutableListOf<StreamCidRewatchListener>()
        every { rewatchSubscriptions.forEach(any<(StreamCidRewatchListener) -> Unit>()) } answers
            {
                val block = firstArg<(StreamCidRewatchListener) -> Unit>()
                rewatchListeners.forEach { block(it) }
                Result.success(Unit)
            }

        // Setup: Add CIDs
        val cid1 = StreamCid.parse("messaging:general")
        val cid2 = StreamCid.parse("livestream:sports")
        watcher.watch(cid1)
        watcher.watch(cid2)

        // Setup: Register rewatch listener
        val receivedCids = mutableListOf<List<StreamCid>>()
        val rewatchListener = StreamCidRewatchListener { cids -> receivedCids.add(cids) }
        rewatchListeners.add(rewatchListener)

        // Start watcher
        watcher.start()

        // Trigger Connected state
        val connectedState =
            StreamConnectionState.Connected(
                connectedUser = mockk<StreamConnectedUser>(relaxed = true),
                connectionId = "conn-123",
            )

        this.launch { clientListenerSlot.captured.onState(connectedState) }

        advanceUntilIdle()

        // Wait for the real coroutine on Dispatchers.Default to complete
        runBlocking { delay(100) }

        // Verify rewatch was called with correct CIDs
        assertEquals(1, receivedCids.size)
        assertEquals(2, receivedCids[0].size)
        assertTrue(receivedCids[0].contains(cid1))
        assertTrue(receivedCids[0].contains(cid2))
    }

    @Test
    fun `Disconnected state does not trigger rewatch`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)

        val clientListenerSlot = slot<StreamClientListener>()
        val mockSubscription = mockk<StreamSubscription>(relaxed = true)
        every { clientSubscriptions.subscribe(capture(clientListenerSlot), any()) } returns
            Result.success(mockSubscription)

        val rewatchListeners = mutableListOf<StreamCidRewatchListener>()
        every { rewatchSubscriptions.forEach(any<(StreamCidRewatchListener) -> Unit>()) } answers
            {
                val block = firstArg<(StreamCidRewatchListener) -> Unit>()
                rewatchListeners.forEach { block(it) }
                Result.success(Unit)
            }

        watcher.watch(StreamCid.parse("messaging:general"))

        val receivedCids = mutableListOf<List<StreamCid>>()
        rewatchListeners.add(StreamCidRewatchListener { cids -> receivedCids.add(cids) })

        watcher.start()

        this.launch {
            clientListenerSlot.captured.onState(StreamConnectionState.Disconnected(null))
        }

        advanceUntilIdle()

        // Verify rewatch was NOT called
        assertEquals(0, receivedCids.size)
    }

    @Test
    fun `Idle state does not trigger rewatch`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)

        val clientListenerSlot = slot<StreamClientListener>()
        val mockSubscription = mockk<StreamSubscription>(relaxed = true)
        every { clientSubscriptions.subscribe(capture(clientListenerSlot), any()) } returns
            Result.success(mockSubscription)

        val rewatchListeners = mutableListOf<StreamCidRewatchListener>()
        every { rewatchSubscriptions.forEach(any<(StreamCidRewatchListener) -> Unit>()) } answers
            {
                val block = firstArg<(StreamCidRewatchListener) -> Unit>()
                rewatchListeners.forEach { block(it) }
                Result.success(Unit)
            }

        watcher.watch(StreamCid.parse("messaging:general"))

        val receivedCids = mutableListOf<List<StreamCid>>()
        rewatchListeners.add(StreamCidRewatchListener { cids -> receivedCids.add(cids) })

        watcher.start()

        this.launch { clientListenerSlot.captured.onState(StreamConnectionState.Idle) }

        advanceUntilIdle()

        assertEquals(0, receivedCids.size)
    }

    @Test
    fun `Connecting state does not trigger rewatch`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)

        val clientListenerSlot = slot<StreamClientListener>()
        val mockSubscription = mockk<StreamSubscription>(relaxed = true)
        every { clientSubscriptions.subscribe(capture(clientListenerSlot), any()) } returns
            Result.success(mockSubscription)

        val rewatchListeners = mutableListOf<StreamCidRewatchListener>()
        every { rewatchSubscriptions.forEach(any<(StreamCidRewatchListener) -> Unit>()) } answers
            {
                val block = firstArg<(StreamCidRewatchListener) -> Unit>()
                rewatchListeners.forEach { block(it) }
                Result.success(Unit)
            }

        watcher.watch(StreamCid.parse("messaging:general"))

        val receivedCids = mutableListOf<List<StreamCid>>()
        rewatchListeners.add(StreamCidRewatchListener { cids -> receivedCids.add(cids) })

        watcher.start()

        this.launch {
            clientListenerSlot.captured.onState(
                StreamConnectionState.Connecting.Opening("user-123")
            )
        }

        advanceUntilIdle()

        assertEquals(0, receivedCids.size)
    }

    @Test
    fun `Connected state with empty CID list does not trigger rewatch`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)

        val clientListenerSlot = slot<StreamClientListener>()
        val mockSubscription = mockk<StreamSubscription>(relaxed = true)
        every { clientSubscriptions.subscribe(capture(clientListenerSlot), any()) } returns
            Result.success(mockSubscription)

        val rewatchListeners = mutableListOf<StreamCidRewatchListener>()
        every { rewatchSubscriptions.forEach(any<(StreamCidRewatchListener) -> Unit>()) } answers
            {
                val block = firstArg<(StreamCidRewatchListener) -> Unit>()
                rewatchListeners.forEach { block(it) }
                Result.success(Unit)
            }

        val receivedCids = mutableListOf<List<StreamCid>>()
        rewatchListeners.add(StreamCidRewatchListener { cids -> receivedCids.add(cids) })

        watcher.start()

        // No CIDs watched
        val connectedState =
            StreamConnectionState.Connected(
                connectedUser = mockk<StreamConnectedUser>(relaxed = true),
                connectionId = "conn-123",
            )

        this.launch { clientListenerSlot.captured.onState(connectedState) }

        advanceUntilIdle()

        assertEquals(0, receivedCids.size)
    }

    // ========================================
    // Multiple Listeners
    // ========================================

    @Test
    fun `multiple rewatch listeners all receive callbacks`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)

        val clientListenerSlot = slot<StreamClientListener>()
        val mockSubscription = mockk<StreamSubscription>(relaxed = true)
        every { clientSubscriptions.subscribe(capture(clientListenerSlot), any()) } returns
            Result.success(mockSubscription)

        val rewatchListeners = mutableListOf<StreamCidRewatchListener>()
        every { rewatchSubscriptions.forEach(any<(StreamCidRewatchListener) -> Unit>()) } answers
            {
                val block = firstArg<(StreamCidRewatchListener) -> Unit>()
                rewatchListeners.forEach { block(it) }
                Result.success(Unit)
            }

        val cid = StreamCid.parse("messaging:general")
        watcher.watch(cid)

        val receivedCids1 = mutableListOf<List<StreamCid>>()
        val receivedCids2 = mutableListOf<List<StreamCid>>()
        val receivedCids3 = mutableListOf<List<StreamCid>>()

        rewatchListeners.add(StreamCidRewatchListener { cids -> receivedCids1.add(cids) })
        rewatchListeners.add(StreamCidRewatchListener { cids -> receivedCids2.add(cids) })
        rewatchListeners.add(StreamCidRewatchListener { cids -> receivedCids3.add(cids) })

        watcher.start()

        val connectedState =
            StreamConnectionState.Connected(
                connectedUser = mockk<StreamConnectedUser>(relaxed = true),
                connectionId = "conn-123",
            )

        this.launch { clientListenerSlot.captured.onState(connectedState) }

        advanceUntilIdle()

        // Wait for the real coroutine on Dispatchers.Default to complete
        runBlocking { delay(100) }

        // All listeners should have received the callback
        assertEquals(1, receivedCids1.size)
        assertEquals(1, receivedCids2.size)
        assertEquals(1, receivedCids3.size)
        assertEquals(listOf(cid), receivedCids1[0])
        assertEquals(listOf(cid), receivedCids2[0])
        assertEquals(listOf(cid), receivedCids3[0])
    }

    // ========================================
    // Error Handling
    // ========================================

    @Test
    fun `rewatch listener exception is caught and logged`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)

        val clientListenerSlot = slot<StreamClientListener>()
        val mockSubscription = mockk<StreamSubscription>(relaxed = true)
        every { clientSubscriptions.subscribe(capture(clientListenerSlot), any()) } returns
            Result.success(mockSubscription)

        val error = RuntimeException("Rewatch failed")
        every { rewatchSubscriptions.forEach(any<(StreamCidRewatchListener) -> Unit>()) } returns
            Result.failure(error)

        val clientListeners = mutableListOf<StreamClientListener>()
        every { clientSubscriptions.forEach(any<(StreamClientListener) -> Unit>()) } answers
            {
                val block = firstArg<(StreamClientListener) -> Unit>()
                clientListeners.forEach { block(it) }
                Result.success(Unit)
            }

        val receivedErrors = mutableListOf<Throwable>()
        clientListeners.add(
            object : StreamClientListener {
                override fun onError(err: Throwable) {
                    receivedErrors.add(err)
                }
            }
        )

        watcher.watch(StreamCid.parse("messaging:general"))
        watcher.start()

        val connectedState =
            StreamConnectionState.Connected(
                connectedUser = mockk<StreamConnectedUser>(relaxed = true),
                connectionId = "conn-123",
            )

        this.launch { clientListenerSlot.captured.onState(connectedState) }

        advanceUntilIdle()

        // Verify error was logged
        verify { logger.e(error, any<() -> String>()) }

        // Verify error was propagated to client listeners
        assertEquals(1, receivedErrors.size)
        assertEquals(error, receivedErrors[0])
    }

    // ========================================
    // Subscription Delegation
    // ========================================

    @Test
    fun `subscribe delegates to rewatchSubscriptions`() {
        val listener = StreamCidRewatchListener {}
        val options =
            StreamSubscriptionManager.Options(
                retention = StreamSubscriptionManager.Options.Retention.KEEP_UNTIL_CANCELLED
            )
        val mockSubscription = mockk<StreamSubscription>()

        every { rewatchSubscriptions.subscribe(listener, options) } returns
            Result.success(mockSubscription)

        val result = watcher.subscribe(listener, options)

        assertTrue(result.isSuccess)
        assertEquals(mockSubscription, result.getOrNull())
        verify { rewatchSubscriptions.subscribe(listener, options) }
    }

    @Test
    fun `subscribe returns failure when rewatchSubscriptions fails`() {
        val listener = StreamCidRewatchListener {}
        val options =
            StreamSubscriptionManager.Options(
                retention = StreamSubscriptionManager.Options.Retention.KEEP_UNTIL_CANCELLED
            )
        val error = RuntimeException("Subscribe failed")

        every { rewatchSubscriptions.subscribe(listener, options) } returns Result.failure(error)

        val result = watcher.subscribe(listener, options)

        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    // ========================================
    // Thread Safety & Concurrency
    // ========================================

    @Test
    fun `concurrent watch and stopWatching operations are thread-safe`() = runTest {
        val cids = (1..100).map { StreamCid.parse("messaging:channel-$it") }

        // Concurrently add and remove CIDs
        val jobs =
            cids.map { cid ->
                this.launch {
                    watcher.watch(cid)
                    watcher.stopWatching(cid)
                }
            }

        jobs.forEach { it.join() }

        // All operations should have completed without crashes
        assertEquals(0, watched.size)
    }

    @Test
    fun `multiple concurrent state changes are handled correctly`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)

        val clientListenerSlot = slot<StreamClientListener>()
        val mockSubscription = mockk<StreamSubscription>(relaxed = true)
        every { clientSubscriptions.subscribe(capture(clientListenerSlot), any()) } returns
            Result.success(mockSubscription)

        val rewatchListeners = mutableListOf<StreamCidRewatchListener>()
        every { rewatchSubscriptions.forEach(any<(StreamCidRewatchListener) -> Unit>()) } answers
            {
                val block = firstArg<(StreamCidRewatchListener) -> Unit>()
                rewatchListeners.forEach { block(it) }
                Result.success(Unit)
            }

        val cid = StreamCid.parse("messaging:general")
        watcher.watch(cid)

        val callCount = mutableListOf<Int>()
        rewatchListeners.add(StreamCidRewatchListener { cids -> callCount.add(cids.size) })

        watcher.start()

        val connectedState =
            StreamConnectionState.Connected(
                connectedUser = mockk<StreamConnectedUser>(relaxed = true),
                connectionId = "conn-123",
            )

        // Trigger multiple state changes concurrently
        repeat(5) { this.launch { clientListenerSlot.captured.onState(connectedState) } }

        advanceUntilIdle()

        // Wait for the real coroutines on Dispatchers.Default to complete
        runBlocking { delay(100) }

        // All 5 state changes should have triggered rewatch
        assertEquals(5, callCount.size)
        assertTrue(callCount.all { it == 1 })
    }

    // ========================================
    // Factory Method Tests
    // ========================================

    @Test
    fun `factory method creates StreamCidWatcherImpl instance`() {
        val factoryWatcher =
            io.getstream.android.core.api.watcher.StreamCidWatcher(
                logger = logger,
                streamRewatchSubscriptionManager = rewatchSubscriptions,
                streamClientSubscriptionManager = clientSubscriptions,
            )

        // Verify it's the correct implementation type
        assertTrue(factoryWatcher is StreamCidWatcherImpl)
    }

    @Test
    fun `factory method wires dependencies correctly`() {
        val factoryWatcher =
            io.getstream.android.core.api.watcher.StreamCidWatcher(
                logger = logger,
                streamRewatchSubscriptionManager = rewatchSubscriptions,
                streamClientSubscriptionManager = clientSubscriptions,
            )

        // Verify the instance is functional by testing basic operations
        val cid = StreamCid.parse("messaging:test")
        val result = factoryWatcher.watch(cid)

        assertTrue(result.isSuccess)
        assertEquals(cid, result.getOrNull())
    }

    // ========================================
    // StreamCidRewatchListener Tests
    // ========================================

    @Test
    fun `StreamCidRewatchListener receives CID list in onRewatch`() {
        val receivedCids = mutableListOf<List<StreamCid>>()

        val listener = StreamCidRewatchListener { cids -> receivedCids.add(cids) }

        val cid1 = StreamCid.parse("messaging:channel1")
        val cid2 = StreamCid.parse("livestream:stream1")
        val testList = listOf(cid1, cid2)

        listener.onRewatch(testList)

        assertEquals(1, receivedCids.size)
        assertEquals(2, receivedCids[0].size)
        assertTrue(receivedCids[0].contains(cid1))
        assertTrue(receivedCids[0].contains(cid2))
    }

    @Test
    fun `StreamCidRewatchListener handles empty list`() {
        var callCount = 0
        val listener = StreamCidRewatchListener { cids -> callCount++ }

        listener.onRewatch(emptyList())

        assertEquals(1, callCount)
    }

    @Test
    fun `StreamCidRewatchListener can be called multiple times`() {
        val allReceivedCids = mutableListOf<List<StreamCid>>()
        val listener = StreamCidRewatchListener { cids -> allReceivedCids.add(cids) }

        val cid1 = StreamCid.parse("messaging:first")
        val cid2 = StreamCid.parse("messaging:second")
        val cid3 = StreamCid.parse("messaging:third")

        listener.onRewatch(listOf(cid1))
        listener.onRewatch(listOf(cid2, cid3))
        listener.onRewatch(emptyList())

        assertEquals(3, allReceivedCids.size)
        assertEquals(1, allReceivedCids[0].size)
        assertEquals(2, allReceivedCids[1].size)
        assertEquals(0, allReceivedCids[2].size)
    }
}
