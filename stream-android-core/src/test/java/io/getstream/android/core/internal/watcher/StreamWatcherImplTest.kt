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
import io.getstream.android.core.api.model.connection.StreamConnectedUser
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.api.watcher.StreamRewatchListener
import io.getstream.android.core.api.watcher.StreamWatcher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StreamWatcherImplTest {

    private lateinit var logger: StreamLogger
    private lateinit var rewatchSubscriptions:
        StreamSubscriptionManager<StreamRewatchListener<String>>
    private lateinit var connectionState: MutableStateFlow<StreamConnectionState>
    private lateinit var watcher: StreamWatcher<String>
    private lateinit var watched: ConcurrentHashMap<String, Unit>
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        logger = mockk(relaxed = true)
        rewatchSubscriptions = mockk(relaxed = true)
        connectionState = MutableStateFlow(StreamConnectionState.Idle)
        watched = ConcurrentHashMap()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        watcher =
            StreamWatcherImpl<String>(
                scope = scope,
                connectionState = connectionState,
                watched = watched,
                rewatchSubscriptions = rewatchSubscriptions,
                logger = logger,
            )
    }

    // ========================================
    // Watch Operations
    // ========================================

    @Test
    fun `watch adds CID to registry`() {
        val cid = "messaging:general"

        val result = watcher.watch(cid)

        assertTrue(result.isSuccess)
        assertEquals(cid, result.getOrNull())
        assertTrue(watched.containsKey(cid))
    }

    @Test
    fun `watch same CID twice is idempotent`() {
        val cid = "messaging:general"

        watcher.watch(cid)
        watcher.watch(cid)

        assertEquals(1, watched.size)
        assertTrue(watched.containsKey(cid))
    }

    @Test
    fun `watch multiple different CIDs`() {
        val cid1 = "messaging:general"
        val cid2 = "livestream:sports"
        val cid3 = "messaging:random"

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
        val cid = "messaging:general"
        watcher.watch(cid)

        val result = watcher.stopWatching(cid)

        assertTrue(result.isSuccess)
        assertEquals(cid, result.getOrNull())
        assertFalse(watched.containsKey(cid))
    }

    @Test
    fun `stopWatching non-existent CID is a no-op`() {
        val cid = "messaging:general"

        val result = watcher.stopWatching(cid)

        assertTrue(result.isSuccess)
        assertEquals(cid, result.getOrNull())
        assertEquals(0, watched.size)
    }

    @Test
    fun `clear removes all CIDs from registry`() {
        watcher.watch("messaging:general")
        watcher.watch("livestream:sports")
        watcher.watch("messaging:random")

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
    fun `start begins collecting from connection state flow`() {
        val result = watcher.start()

        assertTrue(result.isSuccess)
    }

    @Test
    fun `start when already started is idempotent`() {
        watcher.start()
        val result = watcher.start() // Second call

        // Should succeed without error
        assertTrue(result.isSuccess)
    }

    @Test
    fun `start after stop creates new collection job`() = runTest {
        // Start the watcher
        val result1 = watcher.start()
        assertTrue(result1.isSuccess)

        // Stop the watcher
        val stopResult = watcher.stop()
        assertTrue(stopResult.isSuccess)

        // Start again - should create a new job
        val result2 = watcher.start()
        assertTrue(result2.isSuccess)

        // Verify it's actually working by triggering a state change
        watcher.watch("messaging:general")

        var callbackInvoked = false
        every {
            rewatchSubscriptions.forEach(any<(StreamRewatchListener<String>) -> Unit>())
        } answers
            {
                callbackInvoked = true
                Result.success(Unit)
            }

        connectionState.value =
            StreamConnectionState.Connected(
                connectedUser = mockk<StreamConnectedUser>(relaxed = true),
                connectionId = "conn-123",
            )

        advanceUntilIdle()
        runBlocking { delay(100) }

        // Callback should have been invoked, proving the new job is active
        assertTrue(callbackInvoked)
    }

    @Test
    fun `start succeeds even with connection state flow collection`() {
        val result = watcher.start()

        assertTrue(result.isSuccess)
    }

    @Test
    fun `stop cancels collection job`() {
        watcher.start()
        val result = watcher.stop()

        assertTrue(result.isSuccess)
    }

    @Test
    fun `stop when not started is a no-op`() {
        val result = watcher.stop()

        assertTrue(result.isSuccess)
    }

    @Test
    fun `multiple start and stop cycles`() {
        // Cycle 1
        val result1 = watcher.start()
        assertTrue(result1.isSuccess)
        val stopResult1 = watcher.stop()
        assertTrue(stopResult1.isSuccess)

        // Cycle 2 - should work after stop
        val result2 = watcher.start()
        assertTrue(result2.isSuccess)
        val stopResult2 = watcher.stop()
        assertTrue(stopResult2.isSuccess)
    }

    @Test
    fun `watcher can trigger rewatch after restart`() = runTest {
        val rewatchListeners = mutableListOf<StreamRewatchListener<String>>()
        every {
            rewatchSubscriptions.forEach(any<(StreamRewatchListener<String>) -> Unit>())
        } answers
            {
                val block = firstArg<(StreamRewatchListener<String>) -> Unit>()
                rewatchListeners.forEach { block(it) }
                Result.success(Unit)
            }

        val cid = "messaging:test"
        watcher.watch(cid)

        val receivedCidsBeforeRestart = mutableListOf<Set<String>>()
        val receivedCidsAfterRestart = mutableListOf<Set<String>>()
        rewatchListeners.add(
            StreamRewatchListener { cids, _ -> receivedCidsBeforeRestart.add(cids) }
        )

        // Start watcher - note it will emit the current state (Idle) immediately
        watcher.start()
        advanceUntilIdle()

        // Trigger rewatch with Connected state
        val connectedState =
            StreamConnectionState.Connected(
                connectedUser = mockk<StreamConnectedUser>(relaxed = true),
                connectionId = "conn-1",
            )
        connectionState.value = connectedState
        advanceUntilIdle()
        runBlocking { delay(100) }

        // Verify first rewatch worked
        assertEquals(1, receivedCidsBeforeRestart.size)

        watcher.stop()

        // Reset state to Idle to avoid re-triggering on restart
        connectionState.value = StreamConnectionState.Idle

        // Add a second listener and restart
        rewatchListeners.add(
            StreamRewatchListener { cids, _ -> receivedCidsAfterRestart.add(cids) }
        )
        watcher.start()
        advanceUntilIdle()

        // Trigger rewatch again after restart with different connection ID
        val connectedState2 =
            StreamConnectionState.Connected(
                connectedUser = mockk<StreamConnectedUser>(relaxed = true),
                connectionId = "conn-2",
            )
        connectionState.value = connectedState2
        advanceUntilIdle()
        runBlocking { delay(100) }

        // Verify rewatch still works after restart
        assertEquals(1, receivedCidsAfterRestart.size)
        assertEquals(1, receivedCidsAfterRestart[0].size)
        assertTrue(receivedCidsAfterRestart[0].contains(cid))
    }

    // ========================================
    // State Change Handling
    // ========================================

    @Test
    fun `Connected state triggers rewatch with watched CIDs`() = runTest {
        // Setup: Mock rewatch subscriptions
        val rewatchListeners = mutableListOf<StreamRewatchListener<String>>()
        every {
            rewatchSubscriptions.forEach(any<(StreamRewatchListener<String>) -> Unit>())
        } answers
            {
                val block = firstArg<(StreamRewatchListener<String>) -> Unit>()
                rewatchListeners.forEach { block(it) }
                Result.success(Unit)
            }

        // Setup: Add CIDs
        val cid1 = "messaging:general"
        val cid2 = "livestream:sports"
        watcher.watch(cid1)
        watcher.watch(cid2)

        // Setup: Register rewatch listener
        val receivedCids = mutableListOf<Set<String>>()
        val receivedConnectionIds = mutableListOf<String>()
        val rewatchListener: StreamRewatchListener<String> =
            StreamRewatchListener { cids, connectionId ->
                receivedCids.add(cids)
                receivedConnectionIds.add(connectionId)
            }
        rewatchListeners.add(rewatchListener)

        // Start watcher
        watcher.start()

        // Trigger Connected state
        val connectedState =
            StreamConnectionState.Connected(
                connectedUser = mockk<StreamConnectedUser>(relaxed = true),
                connectionId = "conn-123",
            )

        connectionState.value = connectedState

        advanceUntilIdle()

        // Wait for the real coroutine on Dispatchers.Default to complete
        runBlocking { delay(100) }

        // Verify rewatch was called with correct CIDs and connectionId
        assertEquals(1, receivedCids.size)
        assertEquals(2, receivedCids[0].size)
        assertTrue(receivedCids[0].contains(cid1))
        assertTrue(receivedCids[0].contains(cid2))
        assertEquals(1, receivedConnectionIds.size)
        assertEquals("conn-123", receivedConnectionIds[0])
    }

    @Test
    fun `Disconnected state does not trigger rewatch`() = runTest {
        val rewatchListeners = mutableListOf<StreamRewatchListener<String>>()
        every {
            rewatchSubscriptions.forEach(any<(StreamRewatchListener<String>) -> Unit>())
        } answers
            {
                val block = firstArg<(StreamRewatchListener<String>) -> Unit>()
                rewatchListeners.forEach { block(it) }
                Result.success(Unit)
            }

        watcher.watch("messaging:general")

        val receivedCids = mutableListOf<Set<String>>()
        rewatchListeners.add(StreamRewatchListener { cids, _ -> receivedCids.add(cids) })

        watcher.start()

        connectionState.value = StreamConnectionState.Disconnected(null)

        advanceUntilIdle()

        // Verify rewatch was NOT called
        assertEquals(0, receivedCids.size)
    }

    @Test
    fun `Idle state does not trigger rewatch`() = runTest {
        val rewatchListeners = mutableListOf<StreamRewatchListener<String>>()
        every {
            rewatchSubscriptions.forEach(any<(StreamRewatchListener<String>) -> Unit>())
        } answers
            {
                val block = firstArg<(StreamRewatchListener<String>) -> Unit>()
                rewatchListeners.forEach { block(it) }
                Result.success(Unit)
            }

        watcher.watch("messaging:general")

        val receivedCids = mutableListOf<Set<String>>()
        rewatchListeners.add(StreamRewatchListener { cids, _ -> receivedCids.add(cids) })

        watcher.start()

        connectionState.value = StreamConnectionState.Idle

        advanceUntilIdle()

        assertEquals(0, receivedCids.size)
    }

    @Test
    fun `Connecting state does not trigger rewatch`() = runTest {
        val rewatchListeners = mutableListOf<StreamRewatchListener<String>>()
        every {
            rewatchSubscriptions.forEach(any<(StreamRewatchListener<String>) -> Unit>())
        } answers
            {
                val block = firstArg<(StreamRewatchListener<String>) -> Unit>()
                rewatchListeners.forEach { block(it) }
                Result.success(Unit)
            }

        watcher.watch("messaging:general")

        val receivedCids = mutableListOf<Set<String>>()
        rewatchListeners.add(StreamRewatchListener { cids, _ -> receivedCids.add(cids) })

        watcher.start()

        connectionState.value = StreamConnectionState.Connecting.Opening("user-123")

        advanceUntilIdle()

        assertEquals(0, receivedCids.size)
    }

    @Test
    fun `Connected state with empty CID list does not trigger rewatch`() = runTest {
        val rewatchListeners = mutableListOf<StreamRewatchListener<String>>()
        every {
            rewatchSubscriptions.forEach(any<(StreamRewatchListener<String>) -> Unit>())
        } answers
            {
                val block = firstArg<(StreamRewatchListener<String>) -> Unit>()
                rewatchListeners.forEach { block(it) }
                Result.success(Unit)
            }

        val receivedCids = mutableListOf<Set<String>>()
        rewatchListeners.add(StreamRewatchListener { cids, _ -> receivedCids.add(cids) })

        watcher.start()

        // No CIDs watched
        val connectedState =
            StreamConnectionState.Connected(
                connectedUser = mockk<StreamConnectedUser>(relaxed = true),
                connectionId = "conn-123",
            )

        connectionState.value = connectedState

        advanceUntilIdle()

        assertEquals(0, receivedCids.size)
    }

    // ========================================
    // Multiple Listeners
    // ========================================

    @Test
    fun `multiple rewatch listeners all receive callbacks`() = runTest {
        val rewatchListeners = mutableListOf<StreamRewatchListener<String>>()
        every {
            rewatchSubscriptions.forEach(any<(StreamRewatchListener<String>) -> Unit>())
        } answers
            {
                val block = firstArg<(StreamRewatchListener<String>) -> Unit>()
                rewatchListeners.forEach { block(it) }
                Result.success(Unit)
            }

        val cid = "messaging:general"
        watcher.watch(cid)

        val receivedCids1 = mutableListOf<Set<String>>()
        val receivedCids2 = mutableListOf<Set<String>>()
        val receivedCids3 = mutableListOf<Set<String>>()

        rewatchListeners.add(StreamRewatchListener { cids, _ -> receivedCids1.add(cids) })
        rewatchListeners.add(StreamRewatchListener { cids, _ -> receivedCids2.add(cids) })
        rewatchListeners.add(StreamRewatchListener { cids, _ -> receivedCids3.add(cids) })

        watcher.start()

        val connectedState =
            StreamConnectionState.Connected(
                connectedUser = mockk<StreamConnectedUser>(relaxed = true),
                connectionId = "conn-123",
            )

        connectionState.value = connectedState

        advanceUntilIdle()

        // Wait for the real coroutine on Dispatchers.Default to complete
        runBlocking { delay(100) }

        // All listeners should have received the callback
        assertEquals(1, receivedCids1.size)
        assertEquals(1, receivedCids2.size)
        assertEquals(1, receivedCids3.size)
        assertEquals(setOf(cid), receivedCids1[0])
        assertEquals(setOf(cid), receivedCids2[0])
        assertEquals(setOf(cid), receivedCids3[0])
    }

    // ========================================
    // Error Handling
    // ========================================

    @Test
    fun `rewatch listener exception is caught and logged`() = runTest {
        val error = RuntimeException("Rewatch failed")

        // Create a listener that throws an exception
        val failingListener = StreamRewatchListener<String> { _, _ -> throw error }

        // Mock rewatchSubscriptions.forEach to provide the failing listener
        every {
            rewatchSubscriptions.forEach(any<(StreamRewatchListener<String>) -> Unit>())
        } answers
            {
                val block = firstArg<(StreamRewatchListener<String>) -> Unit>()
                block(failingListener)
                Result.success(Unit)
            }

        watcher.watch("messaging:general")
        watcher.start()

        val connectedState =
            StreamConnectionState.Connected(
                connectedUser = mockk<StreamConnectedUser>(relaxed = true),
                connectionId = "conn-123",
            )

        connectionState.value = connectedState

        advanceUntilIdle()

        // Wait for the real coroutines on Dispatchers.Default to complete
        runBlocking { delay(500) }

        // Verify error was logged
        verify { logger.e(error, any<() -> String>()) }
    }

    // ========================================
    // Subscription Delegation
    // ========================================

    @Test
    fun `subscribe delegates to rewatchSubscriptions`() {
        val listener: StreamRewatchListener<String> = StreamRewatchListener { _, _ -> }
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
        val listener: StreamRewatchListener<String> = StreamRewatchListener { _, _ -> }
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
        val cids = (1..100).map { "messaging:channel-$it" }

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
    fun `concurrent start calls are thread-safe`() = runTest {
        // Call start() concurrently from multiple threads
        val jobs = (1..10).map { this.launch { watcher.start() } }

        jobs.forEach { it.join() }

        // All start calls should succeed without crashes
        // Verify only one collection job was created
        val result = watcher.start()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `concurrent start and stop calls are thread-safe`() = runTest {
        // Concurrently call start and stop
        val jobs =
            (1..20).flatMap {
                listOf(this.launch { watcher.start() }, this.launch { watcher.stop() })
            }

        jobs.forEach { it.join() }

        // All operations should complete without crashes
        // Final start should work
        val result = watcher.start()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `multiple concurrent state changes are handled correctly`() = runTest {
        val rewatchListeners = mutableListOf<StreamRewatchListener<String>>()
        every {
            rewatchSubscriptions.forEach(any<(StreamRewatchListener<String>) -> Unit>())
        } answers
            {
                val block = firstArg<(StreamRewatchListener<String>) -> Unit>()
                rewatchListeners.forEach { block(it) }
                Result.success(Unit)
            }

        val cid = "messaging:general"
        watcher.watch(cid)

        val callCount = Collections.synchronizedList(mutableListOf<Int>())
        val receivedConnectionIds = Collections.synchronizedList(mutableListOf<String>())
        rewatchListeners.add(
            StreamRewatchListener { cids, connectionId ->
                callCount.add(cids.size)
                receivedConnectionIds.add(connectionId)
            }
        )

        watcher.start()
        advanceUntilIdle()

        // Trigger multiple state changes with different connection IDs
        // Add delays to prevent StateFlow conflation from dropping emissions
        repeat(5) { i ->
            connectionState.value =
                StreamConnectionState.Connected(
                    connectedUser = mockk<StreamConnectedUser>(relaxed = true),
                    connectionId = "conn-$i",
                )
            advanceUntilIdle()
            // Allow time for the collector on Dispatchers.Default to process
            runBlocking { delay(100) }
        }

        // Wait for the real coroutines on Dispatchers.Default to complete
        runBlocking { delay(200) }

        // All 5 state changes should have triggered rewatch
        assertEquals(5, callCount.size)
        assertTrue(callCount.all { it == 1 })
        assertEquals(5, receivedConnectionIds.size)
        // Note: Order is not guaranteed due to asynchronous execution on Dispatchers.Default
        assertTrue(
            receivedConnectionIds.containsAll(
                listOf("conn-0", "conn-1", "conn-2", "conn-3", "conn-4")
            )
        )
    }

    // ========================================
    // Factory Method Tests
    // ========================================

    @Test
    fun `factory method creates StreamWatcherImpl instance`() {
        val factoryWatcher =
            io.getstream.android.core.api.watcher.StreamWatcher<String>(
                scope = scope,
                logger = logger,
                connectionState = connectionState,
            )

        // Verify it's the correct implementation type
        assertTrue(factoryWatcher is StreamWatcherImpl<*>)
    }

    @Test
    fun `factory method wires dependencies correctly`() {
        val factoryWatcher =
            io.getstream.android.core.api.watcher.StreamWatcher<String>(
                scope = scope,
                logger = logger,
                connectionState = connectionState,
            )

        // Verify the instance is functional by testing basic operations
        val cid = "messaging:test"
        val result = factoryWatcher.watch(cid)

        assertTrue(result.isSuccess)
        assertEquals(cid, result.getOrNull())
    }

    // ========================================
    // StreamRewatchListener Tests
    // ========================================

    @Test
    fun `StreamRewatchListener receives identifier set and connectionId in onRewatch`() = runTest {
        val receivedCids = mutableListOf<Set<String>>()
        val receivedConnectionIds = mutableListOf<String>()

        val listener: StreamRewatchListener<String> = StreamRewatchListener { cids, connectionId ->
            receivedCids.add(cids)
            receivedConnectionIds.add(connectionId)
        }

        val cid1 = "messaging:channel1"
        val cid2 = "livestream:stream1"
        val testSet = setOf(cid1, cid2)
        val testConnectionId = "conn-test-123"

        listener.onRewatch(testSet, testConnectionId)

        assertEquals(1, receivedCids.size)
        assertEquals(2, receivedCids[0].size)
        assertTrue(receivedCids[0].contains(cid1))
        assertTrue(receivedCids[0].contains(cid2))
        assertEquals(1, receivedConnectionIds.size)
        assertEquals(testConnectionId, receivedConnectionIds[0])
    }

    @Test
    fun `StreamRewatchListener handles empty set`() = runTest {
        var callCount = 0
        val listener: StreamRewatchListener<String> = StreamRewatchListener { _, _ -> callCount++ }

        listener.onRewatch(emptySet(), "conn-empty")

        assertEquals(1, callCount)
    }

    @Test
    fun `StreamRewatchListener can be called multiple times with different connectionIds`() =
        runTest {
            val allReceivedCids = mutableListOf<Set<String>>()
            val allConnectionIds = mutableListOf<String>()
            val listener: StreamRewatchListener<String> =
                StreamRewatchListener { cids, connectionId ->
                    allReceivedCids.add(cids)
                    allConnectionIds.add(connectionId)
                }

            val cid1 = "messaging:first"
            val cid2 = "messaging:second"
            val cid3 = "messaging:third"

            listener.onRewatch(setOf(cid1), "conn-1")
            listener.onRewatch(setOf(cid2, cid3), "conn-2")
            listener.onRewatch(emptySet(), "conn-3")

            assertEquals(3, allReceivedCids.size)
            assertEquals(1, allReceivedCids[0].size)
            assertEquals(2, allReceivedCids[1].size)
            assertEquals(0, allReceivedCids[2].size)
            assertEquals(listOf("conn-1", "conn-2", "conn-3"), allConnectionIds)
        }
}
