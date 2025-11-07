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

@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.getstream.android.core.internal.client

import io.getstream.android.core.api.authentication.StreamTokenManager
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.connection.StreamConnectedUser
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo
import io.getstream.android.core.api.model.connection.network.StreamNetworkState
import io.getstream.android.core.api.model.event.StreamClientWsEvent
import io.getstream.android.core.api.model.value.StreamToken
import io.getstream.android.core.api.model.value.StreamUserId
import io.getstream.android.core.api.observers.network.StreamNetworkMonitor
import io.getstream.android.core.api.observers.network.StreamNetworkMonitorListener
import io.getstream.android.core.api.processing.StreamSerialProcessingQueue
import io.getstream.android.core.api.processing.StreamSingleFlightProcessor
import io.getstream.android.core.api.socket.StreamConnectionIdHolder
import io.getstream.android.core.api.socket.listeners.StreamClientListener
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.internal.socket.StreamSocketSession
import io.mockk.*
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.bouncycastle.util.test.SimpleTest.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalTime::class)
class StreamClientIImplTest {

    private var userId: StreamUserId = StreamUserId.fromString("u1")
    private lateinit var tokenManager: StreamTokenManager
    private lateinit var singleFlight: StreamSingleFlightProcessor
    private lateinit var serialQueue: StreamSerialProcessingQueue
    private lateinit var connectionIdHolder: StreamConnectionIdHolder
    private lateinit var socketSession: StreamSocketSession<Unit>
    private lateinit var logger: StreamLogger

    private lateinit var subscriptionManager: StreamSubscriptionManager<StreamClientListener>

    // Backing state flow for MutableStreamClientState.connectionState
    private lateinit var connFlow: MutableStateFlow<StreamConnectionState>
    private lateinit var networkFlow: MutableStateFlow<StreamNetworkState>

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)

        tokenManager = mockk(relaxed = true)
        serialQueue = mockk(relaxed = true)
        connectionIdHolder = mockk(relaxed = true)
        socketSession = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        subscriptionManager = mockk(relaxed = true)

        // SingleFlight: execute the lambda and wrap into Result
        singleFlight = mockk(relaxed = true)
        coEvery { singleFlight.clear(any()) } just Awaits
        coEvery { singleFlight.run(any(), any<suspend () -> Any>()) } coAnswers
            {
                val block = secondArg<suspend () -> Any>()
                Result.success(block())
            }

        // Mutable client state: expose real StateFlows that update() mutates
        connFlow = MutableStateFlow(StreamConnectionState.Disconnected())
        networkFlow = MutableStateFlow(StreamNetworkState.Unknown)

        every { connectionIdHolder.clear() } returns Result.success(Unit)
    }

    private fun createClient(
        scope: CoroutineScope,
        networkMonitor: StreamNetworkMonitor = mockNetworkMonitor(),
    ) =
        StreamClientImpl(
            userId = userId,
            tokenManager = tokenManager,
            singleFlight = singleFlight,
            serialQueue = serialQueue,
            connectionIdHolder = connectionIdHolder,
            socketSession = socketSession,
            logger = logger,
            mutableNetworkState = networkFlow,
            mutableConnectionState = connFlow,
            scope = scope,
            subscriptionManager = subscriptionManager,
            networkMonitor = networkMonitor,
        )

    private fun mockNetworkMonitor(): StreamNetworkMonitor =
        mockk(relaxed = true) {
            every { start() } returns Result.success(Unit)
            every { stop() } returns Result.success(Unit)
            every { subscribe(any(), any()) } returns Result.success(mockk(relaxed = true))
        }

    @Test
    fun `connect short-circuits when already connected`() = runTest {
        backgroundScope
        val connectedUser = mockk<StreamConnectedUser>(relaxed = true)
        connFlow.value = StreamConnectionState.Connected(connectedUser, "cid-123")
        val client = createClient(backgroundScope)

        val res = client.connect()

        assertTrue(res.isSuccess)
        assertSame(connectedUser, res.getOrThrow())

        // No socket session subscribe/connect or token fetch when already connected
        verify(exactly = 0) { socketSession.subscribe(any(), any()) }
        coVerify(exactly = 0) { socketSession.connect(any()) }
        coVerify(exactly = 0) { tokenManager.loadIfAbsent() }
    }

    @Test
    fun `disconnect performs cleanup - updates state, clears ids, cancels handle, stops processors`() =
        runTest {
            val networkMonitor = mockNetworkMonitor()
            val client = createClient(backgroundScope, networkMonitor)
            // Make singleFlight actually run the provided block and return success
            coEvery { singleFlight.run(any(), any<suspend () -> Any>()) } coAnswers
                {
                    val block = secondArg<suspend () -> Any>()
                    block() // execute disconnect body
                    Result.success(Unit)
                }
            coJustRun { singleFlight.clear(true) }

            // Pretend we already have a live subscription handle inside the client
            val fakeHandle = mockk<StreamSubscription>(relaxed = true)
            val handleField =
                client.javaClass.getDeclaredField("handle").apply { isAccessible = true }
            handleField.set(client, fakeHandle)

            val networkHandle = mockk<StreamSubscription>(relaxed = true)
            val networkHandleField =
                client.javaClass.getDeclaredField("networkMonitorHandle").apply {
                    isAccessible = true
                }
            networkHandleField.set(client, networkHandle)

            every { connectionIdHolder.clear() } returns Result.success(Unit)
            every { socketSession.disconnect() } returns Result.success(Unit)
            coEvery { serialQueue.stop(any()) } returns Result.success(Unit) // default-arg path
            justRun { tokenManager.invalidate() }

            val result = client.disconnect()

            assertTrue(result.isSuccess)

            // State moved to Manual
            assertTrue(connFlow.value is StreamConnectionState.Disconnected)

            verify { connectionIdHolder.clear() }
            verify { socketSession.disconnect() }
            verify { fakeHandle.cancel() } // the previous handle is cancelled
            verify { tokenManager.invalidate() }
            coVerify { serialQueue.stop(any()) }
            coVerify { singleFlight.clear(true) }
            verify { networkMonitor.stop() }
            verify { networkHandle.cancel() }

            // Handle is nulled
            assertNull(handleField.get(client))
            assertNull(networkHandleField.get(client))
        }

    @Test
    fun `network monitor updates state and notifies subscribers`() = runTest {
        val forwardedStates = mutableListOf<StreamNetworkState>()
        every { subscriptionManager.forEach(any()) } answers
            {
                val block = firstArg<(StreamClientListener) -> Unit>()
                val external = mockk<StreamClientListener>(relaxed = true)
                every { external.onNetworkState(any()) } answers
                    {
                        forwardedStates += firstArg<StreamNetworkState>()
                    }
                block(external)
                Result.success(Unit)
            }

        val networkHandle = mockk<StreamSubscription>(relaxed = true)
        var capturedListener: StreamNetworkMonitorListener? = null
        val networkMonitor = mockk<StreamNetworkMonitor>()
        every { networkMonitor.start() } returns Result.success(Unit)
        every { networkMonitor.stop() } returns Result.success(Unit)
        every { networkMonitor.subscribe(any(), any()) } answers
            {
                capturedListener = firstArg()
                Result.success(networkHandle)
            }

        val client = createClient(backgroundScope, networkMonitor)

        val socketHandle = mockk<StreamSubscription>(relaxed = true)
        every { socketSession.subscribe(any<StreamClientListener>(), any()) } returns
            Result.success(socketHandle)
        val token = StreamToken.fromString("tok")
        coEvery { tokenManager.loadIfAbsent() } returns Result.success(token)
        val connectedUser = mockk<StreamConnectedUser>(relaxed = true)
        val connectedState = StreamConnectionState.Connected(connectedUser, "conn-1")
        coEvery { socketSession.connect(any()) } returns Result.success(connectedState)
        every { connectionIdHolder.setConnectionId("conn-1") } returns Result.success("conn-1")

        val result = client.connect()

        assertTrue(result.isSuccess)
        verify(exactly = 1) { networkMonitor.subscribe(any(), any()) }
        verify(exactly = 1) { networkMonitor.start() }
        val listener = capturedListener ?: error("Expected network monitor listener")

        val connectedSnapshot = StreamNetworkInfo.Snapshot(transports = emptySet())
        listener.onNetworkConnected(connectedSnapshot)
        assertEquals(StreamNetworkState.Available(connectedSnapshot), networkFlow.value)

        listener.onNetworkLost(permanent = false)
        assertEquals(StreamNetworkState.Disconnected, networkFlow.value)

        listener.onNetworkLost(permanent = true)
        assertEquals(StreamNetworkState.Unavailable, networkFlow.value)

        val updatedSnapshot =
            connectedSnapshot.copy(priority = StreamNetworkInfo.PriorityHint.LATENCY)
        listener.onNetworkPropertiesChanged(updatedSnapshot)
        assertEquals(StreamNetworkState.Available(updatedSnapshot), networkFlow.value)

        val expectedStates =
            listOf(
                StreamNetworkState.Available(connectedSnapshot),
                StreamNetworkState.Disconnected,
                StreamNetworkState.Unavailable,
                StreamNetworkState.Available(updatedSnapshot),
            )
        assertTrue(forwardedStates.containsAll(expectedStates))
    }

    @Test
    fun `subscribe delegates to subscriptionManager`() = runTest {
        val listener = mockk<StreamClientListener>(relaxed = true)
        val sub = mockk<StreamSubscription>(relaxed = true)
        every { subscriptionManager.subscribe(listener) } returns Result.success(sub)
        val client = createClient(backgroundScope)

        val res = client.subscribe(listener)

        assertTrue(res.isSuccess)
        assertSame(sub, res.getOrThrow())
        verify { subscriptionManager.subscribe(listener) }
    }

    @Test
    fun `connect success - subscribes once, calls session connect, updates state and connectionId, returns user`() =
        runTest {
            val client = createClient(backgroundScope)
            // single-flight executes block and returns its result
            coEvery { singleFlight.run(any(), any<suspend () -> StreamConnectedUser>()) } coAnswers
                {
                    val block = secondArg<suspend () -> StreamConnectedUser>()
                    Result.success(block.invoke())
                }

            // subscribe once to the socket session
            val fakeHandle = mockk<StreamSubscription>(relaxed = true)
            every { socketSession.subscribe(any<StreamClientListener>(), any()) } returns
                Result.success(fakeHandle)

            // token resolves
            val token = StreamToken.fromString("tok")
            coEvery { tokenManager.loadIfAbsent() } returns Result.success(token)

            // session connect succeeds
            val connectedUser = mockk<StreamConnectedUser>(relaxed = true)
            val connectedState = StreamConnectionState.Connected(connectedUser, "conn-1")
            coEvery { socketSession.connect(any()) } returns Result.success(connectedState)

            every { connectionIdHolder.setConnectionId("conn-1") } returns Result.success("Unit")

            val result = client.connect()

            // result is the connected user
            assertTrue(result.isSuccess)
            assertSame(connectedUser, result.getOrNull())

            // state is updated to Connected
            assertTrue(connFlow.value is StreamConnectionState.Connected)

            // interactions
            verify(exactly = 1) { socketSession.subscribe(any<StreamClientListener>(), any()) }
            coVerify(exactly = 1) { tokenManager.loadIfAbsent() }
            coVerify(exactly = 1) { socketSession.connect(match { it.token == "tok" }) }
            verify(exactly = 1) { connectionIdHolder.setConnectionId("conn-1") }
        }

    @Test
    fun `connect early-exit when already connected - returns existing user and does not hit session or token`() =
        runTest {
            val client = createClient(backgroundScope)
            // Make single-flight run the block
            coEvery { singleFlight.run(any(), any<suspend () -> StreamConnectedUser>()) } coAnswers
                {
                    val block = secondArg<suspend () -> StreamConnectedUser>()
                    Result.success(block.invoke())
                }

            // Pretend we are already connected
            val existingUser = mockk<StreamConnectedUser>(relaxed = true)
            connFlow.update { StreamConnectionState.Connected(existingUser, "existing-conn") }

            val result = client.connect()

            assertTrue(result.isSuccess)
            assertSame(existingUser, result.getOrNull())

            // No new subscribe, no token load, no session connect, no connectionId set
            verify(exactly = 0) { socketSession.subscribe(any<StreamClientListener>(), any()) }
            coVerify(exactly = 0) { tokenManager.loadIfAbsent() }
            coVerify(exactly = 0) { socketSession.connect(any()) }
            verify(exactly = 0) { connectionIdHolder.setConnectionId(any()) }
        }

    @Test
    fun `connect fails when token manager fails - emits Disconnected state and returns failure`() =
        runTest {
            val client = createClient(backgroundScope)
            // single-flight executes block
            coEvery { singleFlight.run(any(), any<suspend () -> StreamConnectedUser>()) } coAnswers
                {
                    val block = secondArg<suspend () -> StreamConnectedUser>()
                    try {
                        Result.success(block.invoke())
                    } catch (t: Throwable) {
                        Result.failure(t)
                    }
                }

            // subscribe is attempted before token load
            every { socketSession.subscribe(any<StreamClientListener>(), any()) } returns
                Result.success(mockk(relaxed = true))

            val boom = RuntimeException("no token")
            coEvery { tokenManager.loadIfAbsent() } returns Result.failure(boom)

            val result = client.connect()

            assertTrue(result.isFailure)
            // state should be Disconnected (error case)
            val state = connFlow.value
            assertTrue(state is StreamConnectionState.Disconnected)

            // verify no session connect or connection id set
            verify(exactly = 1) { socketSession.subscribe(any<StreamClientListener>(), any()) }
            coVerify(exactly = 1) { tokenManager.loadIfAbsent() }
            coVerify(exactly = 0) { socketSession.connect(any()) }
            verify(exactly = 0) { connectionIdHolder.setConnectionId(any()) }
        }

    @Test
    fun `connect fails when socket session connect fails - emits Disconnected state and returns failure`() =
        runTest {
            val client = createClient(backgroundScope)
            // single-flight executes block
            coEvery { singleFlight.run(any(), any<suspend () -> StreamConnectedUser>()) } coAnswers
                {
                    val block = secondArg<suspend () -> StreamConnectedUser>()
                    try {
                        Result.success(block.invoke())
                    } catch (t: Throwable) {
                        Result.failure(t)
                    }
                }

            // subscribe created
            every { socketSession.subscribe(any<StreamClientListener>(), any()) } returns
                Result.success(mockk(relaxed = true))

            // token ok
            val token = StreamToken.fromString("tok")
            coEvery { tokenManager.loadIfAbsent() } returns Result.success(token)

            // session connect fails
            val boom = IllegalStateException("connect failed")
            coEvery { socketSession.connect(any()) } returns Result.failure(boom)

            val result = client.connect()

            assertTrue(result.isFailure)
            // state should be Disconnected (error case)
            val state = connFlow.value
            assertTrue(state is StreamConnectionState.Disconnected)

            // verify interactions
            verify(exactly = 1) { socketSession.subscribe(any<StreamClientListener>(), any()) }
            coVerify(exactly = 1) { tokenManager.loadIfAbsent() }
            coVerify(exactly = 1) { socketSession.connect(match { it.token == "tok" }) }
            verify(exactly = 0) { connectionIdHolder.setConnectionId(any()) }
        }

    @Test
    fun `subscription onState updates client state and forwards to subscribers`() = runTest {
        val client = createClient(backgroundScope)
        // Make single-flight execute the block
        coEvery { singleFlight.run(any(), any<suspend () -> StreamConnectedUser>()) } coAnswers
            {
                val block = secondArg<suspend () -> StreamConnectedUser>()
                try {
                    Result.success(block.invoke())
                } catch (t: Throwable) {
                    Result.failure(t)
                }
            }

        // Capture the internal listener the client registers with the socket session
        var capturedListener: StreamClientListener? = null
        every { socketSession.subscribe(any<StreamClientListener>(), any()) } answers
            {
                capturedListener = firstArg()
                Result.success(mockk(relaxed = true))
            }

        // Record what the external subscribers receive via subscriptionManager.forEach {
        // it.onState(state) }
        val receivedStates = mutableListOf<StreamConnectionState>()
        every { subscriptionManager.forEach(any()) } answers
            {
                val block = firstArg<(StreamClientListener) -> Unit>()
                val external = mockk<StreamClientListener>(relaxed = true)
                every { external.onState(any()) } answers
                    {
                        receivedStates += firstArg<StreamConnectionState>()
                    }
                block(external)
                Result.success(Unit)
            }

        // Force connect() to bail early after subscribe (so we don't need a real socket connect)
        coEvery { tokenManager.loadIfAbsent() } returns
            Result.failure(RuntimeException("stop here"))

        // Trigger subscription installation
        client.connect()
        advanceUntilIdle()

        // Act: invoke onState on the captured internal listener
        val state = StreamConnectionState.Disconnected()
        capturedListener!!.onState(state)

        // Assert client state updated and callback forwarded
        assertTrue(client.connectionState.value is StreamConnectionState.Disconnected)
        assertTrue(receivedStates.contains(state))
        verify(atLeast = 1) { subscriptionManager.forEach(any()) }
    }

    @Test
    fun `subscription onEvent forwards to subscribers`() = runTest {
        val client = createClient(backgroundScope)
        // Make single-flight execute the block
        coEvery { singleFlight.run(any(), any<suspend () -> StreamConnectedUser>()) } coAnswers
            {
                val block = secondArg<suspend () -> StreamConnectedUser>()
                try {
                    Result.success(block.invoke())
                } catch (t: Throwable) {
                    Result.failure(t)
                }
            }

        // Capture internal listener
        var capturedListener: StreamClientListener? = null
        every { socketSession.subscribe(any<StreamClientListener>(), any()) } answers
            {
                capturedListener = firstArg()
                Result.success(mockk(relaxed = true))
            }

        // Record forwarded events
        val forwardedEvents = mutableListOf<StreamClientWsEvent>()
        every { subscriptionManager.forEach(any()) } answers
            {
                val block = firstArg<(StreamClientListener) -> Unit>()
                val external = mockk<StreamClientListener>(relaxed = true)
                every { external.onEvent(any<StreamClientWsEvent>()) } answers
                    {
                        forwardedEvents += firstArg<StreamClientWsEvent>()
                    }
                block(external)
                Result.success(Unit)
            }

        // Stop connect after subscribe
        coEvery { tokenManager.loadIfAbsent() } returns
            Result.failure(RuntimeException("stop here"))

        // Trigger subscription installation
        client.connect()
        advanceUntilIdle()

        // Act: invoke onEvent on the captured internal listener
        val event = mockk<StreamClientWsEvent>(relaxed = true)
        capturedListener!!.onEvent(event)

        // Assert it was forwarded to subscribers
        assertTrue(forwardedEvents.contains(event))
        verify(atLeast = 1) { subscriptionManager.forEach(any()) }
    }
}
