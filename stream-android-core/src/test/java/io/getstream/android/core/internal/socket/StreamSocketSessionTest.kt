/*
 * Copyright (c) 2014-2025 Stream.io Inc. All rights reserved.
 *
 * Licensed under the Stream License;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://github.com/GetStream/stream-android-base/blob/main/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.getstream.android.core.internal.socket

import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.config.StreamSocketConfig
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.model.event.StreamClientWsEvent
import io.getstream.android.core.api.model.exceptions.StreamEndpointErrorData
import io.getstream.android.core.api.processing.StreamBatcher
import io.getstream.android.core.api.serialization.StreamJsonSerialization
import io.getstream.android.core.api.socket.StreamWebSocket
import io.getstream.android.core.api.socket.listeners.StreamClientListener
import io.getstream.android.core.api.socket.listeners.StreamWebSocketListener
import io.getstream.android.core.api.socket.monitor.StreamHealthMonitor
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.internal.model.events.StreamClientConnectedEvent
import io.getstream.android.core.internal.model.events.StreamClientConnectionErrorEvent
import io.getstream.android.core.internal.model.events.StreamHealthCheckEvent
import io.getstream.android.core.internal.serialization.StreamClientEventSerializationImpl
import io.getstream.android.core.internal.socket.model.ConnectUserData
import io.mockk.*
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StreamSocketSessionTest {

    private lateinit var logger: StreamLogger
    private lateinit var socket: StreamWebSocket<StreamWebSocketListener>
    private lateinit var json: StreamJsonSerialization
    private lateinit var parser: StreamClientEventSerializationImpl
    private lateinit var health: StreamHealthMonitor
    private lateinit var debounce: StreamBatcher<String>
    private lateinit var subs: StreamSubscriptionManager<StreamClientListener>

    private lateinit var session: StreamSocketSession

    private val config =
        StreamSocketConfig.jwt(
            url = "wss://example.test/connect",
            apiKey = mockk(relaxed = true),
            clientInfoHeader = mockk(relaxed = true),
        )

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)

        logger = mockk(relaxed = true)
        socket = mockk(relaxed = true)
        json = mockk(relaxed = true)
        parser = mockk(relaxed = true)
        health = mockk(relaxed = true)
        debounce = mockk(relaxed = true)
        subs = mockk(relaxed = true)

        // default: route notifications to a listener so we can assert state
        val stateListener = mockk<StreamClientListener>(relaxed = true)
        every { subs.forEach(any()) } answers
            {
                val block = firstArg<(StreamClientListener) -> Unit>()
                block(stateListener)
                Result.success(Unit)
            }

        every { socket.close() } returns Result.success(Unit)
        every { debounce.stop() } returns Result.success(Unit)
        every { health.stop() } just Runs

        session =
            StreamSocketSession(
                logger = logger,
                config = config,
                internalSocket = socket,
                jsonSerialization = json,
                eventParser = parser,
                healthMonitor = health,
                batcher = debounce,
                subscriptionManager = subs,
                listOf("feeds"),
            )
    }

    @Test
    fun `disconnect() without error emits Disconnected_Manual and cleans up`() = runTest {
        val listener = slot<StreamConnectionState>()
        every { subs.forEach(any()) } answers
            {
                val block = firstArg<(StreamClientListener) -> Unit>()
                val l = mockk<StreamClientListener>(relaxed = true)
                every { l.onState(capture(listener)) } just Runs
                block(l)
                Result.success(Unit)
            }

        val res = session.disconnect()

        assertTrue(res.isSuccess)
        assertTrue(listener.isCaptured && listener.captured is StreamConnectionState.Disconnected)
        verify { socket.close() }
        verify { debounce.stop() }
        verify { health.stop() }
    }

    @Test
    fun `disconnect(error) emits Disconnected state and cleans up`() = runTest {
        val boom = IllegalStateException("boom")
        val listener = slot<StreamConnectionState>()
        every { subs.forEach(any()) } answers
            {
                val block = firstArg<(StreamClientListener) -> Unit>()
                val l = mockk<StreamClientListener>(relaxed = true)
                every { l.onState(capture(listener)) } just Runs
                block(l)
                Result.success(Unit)
            }

        val res = session.disconnect(boom)

        assertTrue(res.isSuccess)
        assertTrue(listener.isCaptured && listener.captured is StreamConnectionState.Disconnected)
        verify { socket.close() }
        verify { debounce.stop() }
        verify { health.stop() }
    }

    @Test
    fun `disconnect() remains idempotent - state notified once, close can be called twice`() =
        runTest {
            // Count state notifications
            val l = mockk<StreamClientListener>(relaxed = true)
            every { subs.forEach(any()) } answers
                {
                    val block = firstArg<(StreamClientListener) -> Unit>()
                    block(l)
                    Result.success(Unit)
                }

            session.disconnect() // first call
            session.disconnect() // second call

            // onState called only once due to closingByUs guard
            verify(exactly = 1) { l.onState(ofType(StreamConnectionState.Disconnected::class)) }
            // but socket.close still invoked on both calls
            verify(exactly = 2) { socket.close() }
            // cleanup is safe across both calls
            verify(atLeast = 1) { debounce.stop() }
            verify(atLeast = 1) { health.stop() }
        }

    @Test
    fun `when notifyState fails, disconnect still closes socket and logs error`() = runTest {
        // Simulate failure of subscription manager; do NOT invoke block
        val notifyFailure = RuntimeException("forEach failed")
        every { subs.forEach(any()) } returns Result.failure(notifyFailure)

        val res = session.disconnect()

        assertTrue(res.isSuccess)
        // Still closed & cleaned up
        verify { socket.close() }
        verify { debounce.stop() }
        verify { health.stop() }
        // Error was logged from notifyState onFailure
        verify { logger.e(notifyFailure, any()) }
    }

    @Test
    fun `onClosed(1000) emits Disconnected_Manual and performs cleanup when not closingByUs`() =
        runTest {
            // Arrange a listener that will receive notifyState() callbacks
            val clientListener = mockk<StreamClientListener>(relaxed = true)
            every { subs.forEach(any()) } answers
                {
                    val block = firstArg<(StreamClientListener) -> Unit>()
                    block(clientListener)
                    Result.success(Unit)
                }

            // Grab the internal lifecycle listener via reflection so we can call onClosed directly
            val f =
                StreamSocketSession::class.java.getDeclaredField("eventListener").apply {
                    isAccessible = true
                }
            val lifecycleListener = f.get(session) as StreamWebSocketListener

            // Act: simulate the socket closing normally (code 1000)
            lifecycleListener.onClosed(1000, "Normal Closure")

            // Assert: state -> Disconnected.Manual, and cleanup called
            verify { clientListener.onState(StreamConnectionState.Disconnected()) }
            verify { health.stop() }
            verify { debounce.stop() }
        }

    @Test
    fun `onClosed(1000) emits Disconnected_Manual and performs cleanup`() = runTest {
        // Route notifyState() to a real listener we can assert against
        val client = mockk<StreamClientListener>(relaxed = true)
        every { subs.forEach(any()) } answers
            {
                val block = firstArg<(StreamClientListener) -> Unit>()
                block(client)
                Result.success(Unit)
            }

        // Grab the internal lifecycle listener and simulate a normal close (1000)
        val f =
            StreamSocketSession::class.java.getDeclaredField("eventListener").apply {
                isAccessible = true
            }
        val lifecycleListener = f.get(session) as StreamWebSocketListener

        lifecycleListener.onClosed(1000, "Normal Closure")

        // Then: Manual disconnect is emitted and cleanup executed
        verify { client.onState(StreamConnectionState.Disconnected()) }
        verify { health.stop() }
        verify { debounce.stop() }
    }

    @Test
    fun `onMessage - when offer returns false, disconnects with Error and cleans up`() = runTest {
        // Route notifyState() into a concrete listener so we can assert emissions.
        val client = mockk<StreamClientListener>(relaxed = true)
        every { subs.forEach(any()) } answers
            {
                firstArg<(StreamClientListener) -> Unit>().invoke(client)
                Result.success(Unit)
            }

        // Make the debounce reject the message so the session calls disconnect(error)
        every { debounce.offer(any()) } returns false
        every { socket.close() } returns Result.success(Unit)

        // Grab the internal lifecycle listener and fire onMessage
        val f =
            StreamSocketSession::class.java.getDeclaredField("eventListener").apply {
                isAccessible = true
            }
        val lifecycleListener = f.get(session) as StreamWebSocketListener

        lifecycleListener.onMessage("""{"ignored":"payload"}""")

        // Heartbeat acknowledged, then we disconnect with an Error state and clean up
        verify { health.acknowledgeHeartbeat() }
        verify { client.onState(match { it is StreamConnectionState.Disconnected }) }
        verify { health.stop() }
        verify { debounce.stop() }
        verify { socket.close() }
    }

    @Test
    fun `lifecycle onMessage - accepted by debounce acknowledges heartbeat and does not disconnect`() {
        // route state calls to a concrete listener (we'll assert none happen)
        val client = mockk<StreamClientListener>(relaxed = true)
        every { subs.forEach(any()) } answers
            {
                firstArg<(StreamClientListener) -> Unit>().invoke(client)
                Result.success(Unit)
            }
        every { debounce.offer(any()) } returns true
        every { socket.close() } returns Result.success(Unit)

        val f =
            StreamSocketSession::class.java.getDeclaredField("eventListener").apply {
                isAccessible = true
            }
        val listener = f.get(session) as StreamWebSocketListener

        listener.onMessage("""{"whatever":"ok"}""")

        verify { health.acknowledgeHeartbeat() }
        verify(exactly = 0) { socket.close() }
        verify(exactly = 0) { client.onState(any()) }
    }

    @Test
    fun `lifecycle onMessage - drop by debounce triggers disconnect with error`() {
        val client = mockk<StreamClientListener>(relaxed = true)
        every { subs.forEach(any()) } answers
            {
                firstArg<(StreamClientListener) -> Unit>().invoke(client)
                Result.success(Unit)
            }
        every { debounce.offer(any()) } returns false
        every { socket.close() } returns Result.success(Unit)

        val f =
            StreamSocketSession::class.java.getDeclaredField("eventListener").apply {
                isAccessible = true
            }
        val listener = f.get(session) as StreamWebSocketListener

        listener.onMessage("""{"bad":"msg"}""")

        verify { socket.close() }
        verify { client.onState(any<StreamConnectionState.Disconnected>()) }
        verify { health.stop() }
        verify { debounce.stop() }
    }

    @Test
    fun `lifecycle onClosed 1000 emits Manual and cleans up`() {
        val client = mockk<StreamClientListener>(relaxed = true)
        every { subs.forEach(any()) } answers
            {
                firstArg<(StreamClientListener) -> Unit>().invoke(client)
                Result.success(Unit)
            }
        every { socket.close() } returns Result.success(Unit)

        val f =
            StreamSocketSession::class.java.getDeclaredField("eventListener").apply {
                isAccessible = true
            }
        val listener = f.get(session) as StreamWebSocketListener

        listener.onClosed(1000, "bye")

        verify { client.onState(StreamConnectionState.Disconnected()) }
        verify { health.stop() }
        verify { debounce.stop() }
    }

    @Test
    fun `lifecycle onClosed non-1000 emits Error and cleans up`() {
        val client = mockk<StreamClientListener>(relaxed = true)
        every { subs.forEach(any()) } answers
            {
                firstArg<(StreamClientListener) -> Unit>().invoke(client)
                Result.success(Unit)
            }

        val f =
            StreamSocketSession::class.java.getDeclaredField("eventListener").apply {
                isAccessible = true
            }
        val listener = f.get(session) as StreamWebSocketListener

        listener.onClosed(1006, "abnormal")

        verify { client.onState(ofType<StreamConnectionState.Disconnected>()) }
        verify { health.stop() }
        verify { debounce.stop() }
    }

    @Test
    fun `lifecycle onClosed suppressed when closingByUs is true`() {
        val client = mockk<StreamClientListener>(relaxed = true)
        every { subs.forEach(any()) } answers
            {
                firstArg<(StreamClientListener) -> Unit>().invoke(client)
                Result.success(Unit)
            }

        // Force the flag so branch is exercised
        val flag =
            StreamSocketSession::class.java.getDeclaredField("closingByUs").apply {
                isAccessible = true
            }
        (flag.get(session) as java.util.concurrent.atomic.AtomicBoolean).set(true)

        val f =
            StreamSocketSession::class.java.getDeclaredField("eventListener").apply {
                isAccessible = true
            }
        val listener = f.get(session) as StreamWebSocketListener

        listener.onClosed(1006, "server-closed")

        verify(exactly = 0) { client.onState(any()) }
        verify { health.stop() }
        verify { debounce.stop() }
    }

    @Test
    fun `disconnect with error emits Disconnected_Error and closes`() {
        val client = mockk<StreamClientListener>(relaxed = true)
        every { subs.forEach(any()) } answers
            {
                firstArg<(StreamClientListener) -> Unit>().invoke(client)
                Result.success(Unit)
            }
        every { socket.close() } returns Result.success(Unit)

        val boom = IllegalStateException("x")
        val res = session.disconnect(boom)

        assertTrue(res.isSuccess)
        verify { client.onState(ofType<StreamConnectionState.Disconnected>()) }
        verify { socket.close() }
        verify { health.stop() }
        verify { debounce.stop() }
    }

    @Test
    fun `disconnect returns failure if close fails but still cleans up`() {
        val client = mockk<StreamClientListener>(relaxed = true)
        every { subs.forEach(any()) } answers
            {
                firstArg<(StreamClientListener) -> Unit>().invoke(client)
                Result.success(Unit)
            }
        every { socket.close() } returns Result.failure(RuntimeException("close fail"))

        val res = session.disconnect()

        assertTrue(res.isFailure)
        verify { client.onState(StreamConnectionState.Disconnected()) }
        verify { health.stop() }
        verify { debounce.stop() }
    }

    @Test
    fun `connect fails when handshake subscribe fails - no open`() = runTest {
        val lifecycleSub = mockk<StreamSubscription>(relaxed = true)
        val boom = RuntimeException("handshake sub failed")

        every { socket.subscribe(any<StreamWebSocketListener>()) } returns
            Result.success(lifecycleSub)
        every { socket.subscribe(any<StreamWebSocketListener>(), any()) } returns
            Result.failure(boom)
        every { socket.open(config) } answers
            {
                error("open() must not be called when handshake subscribe fails")
            }

        val result = session.connect(connectUserData())

        assertTrue(result.isFailure)
        verify(exactly = 1) { socket.subscribe(any<StreamWebSocketListener>()) }
        verify(exactly = 1) { socket.subscribe(any<StreamWebSocketListener>(), any()) }
        verify(exactly = 0) { socket.open(any()) }
        verify { subs.forEach(any()) } // Opening + Disconnected.Error dispatched
    }

    @Test
    fun `connect returns failure when open fails - no health start, no auth send`() = runTest {
        val lifecycleSub = mockk<StreamSubscription>(relaxed = true)
        val handshakeSub = mockk<StreamSubscription>(relaxed = true)

        every { socket.subscribe(any<StreamWebSocketListener>()) } returns
            Result.success(lifecycleSub)
        every { socket.subscribe(any<StreamWebSocketListener>(), any()) } returns
            Result.success(handshakeSub)

        val boom = RuntimeException("open failed")
        every { socket.open(config) } returns Result.failure(boom)

        val result = session.connect(connectUserData())

        assertTrue(result.isFailure)
        verify(exactly = 2) { socket.subscribe(any<StreamWebSocketListener>(), any()) }
        verify(exactly = 1) { socket.open(config) }
        verify(exactly = 0) { socket.send(any<String>()) }
        verify(exactly = 0) { health.start() }
        verify { subs.forEach(any()) } // Opening + Disconnected.Error
    }

    @Test
    fun `handshake onOpen non-101 causes failure - no auth send`() = runTest {
        val lifecycleSub = mockk<StreamSubscription>(relaxed = true)
        val handshakeSub = mockk<StreamSubscription>(relaxed = true)

        var hsListener: StreamWebSocketListener? = null

        every { socket.subscribe(any<StreamWebSocketListener>()) } returns
            Result.success(lifecycleSub)
        every { socket.subscribe(any<StreamWebSocketListener>(), any()) } answers
            {
                hsListener = firstArg()
                Result.success(handshakeSub)
            }
        every { socket.open(config) } answers
            {
                // Trigger onOpen with a bad code AFTER subscribe has happened.
                val badResp =
                    Response.Builder()
                        .request(Request.Builder().url("https://x").build())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .build()
                hsListener!!.onOpen(badResp)
                Result.success(Unit)
            }

        val result = session.connect(connectUserData())

        assertTrue(result.isFailure)
        verify(exactly = 1) { socket.open(config) }
        verify(exactly = 0) { parser.serialize(any()) }
        verify(exactly = 0) { socket.send(any<String>()) }
        verify(exactly = 0) { health.start() }
        verify(atLeast = 1) { subs.forEach(any()) } // Opening + Disconnected.Error
    }

    @Test
    fun `handshake onOpen 101 but auth serialize fails - connect fails`() = runTest {
        val lifecycleSub = mockk<StreamSubscription>(relaxed = true)
        val handshakeSub = mockk<StreamSubscription>(relaxed = true)

        var hsListener: StreamWebSocketListener? = null

        every { socket.subscribe(any<StreamWebSocketListener>()) } returns
            Result.success(lifecycleSub)
        every { socket.subscribe(any<StreamWebSocketListener>(), any()) } answers
            {
                hsListener = firstArg()
                Result.success(handshakeSub)
            }
        every { socket.open(config) } answers
            {
                val okResp =
                    Response.Builder()
                        .request(Request.Builder().url("https://x").build())
                        .protocol(Protocol.HTTP_1_1)
                        .code(101)
                        .message("OK")
                        .build()
                hsListener!!.onOpen(okResp) // triggers auth flow inside session
                Result.success(Unit)
            }
        every { parser.serialize(any()) } returns Result.failure(IllegalStateException("ser fail"))

        val result = session.connect(connectUserData())

        assertTrue(result.isFailure)
        verify(exactly = 1) { socket.open(config) }
        verify(exactly = 1) { parser.serialize(any()) }
        verify(exactly = 0) { socket.send(any<String>()) }
        verify(exactly = 0) { health.start() }
        verify(atLeast = 1) { subs.forEach(any()) } // Opening + Authenticating + Disconnected.Error
    }

    @Test
    fun `connect fails when lifecycle subscribe fails - no handshake subscribe, no open`() =
        runTest {
            // Both subscribe overloads route to the 2-arg form; stub that to FAIL on the first
            // (lifecycle) call.
            every { socket.subscribe(any<StreamWebSocketListener>(), any()) } returns
                Result.failure(RuntimeException("lifecycle sub failed"))

            val result = session.connect(connectUserData())

            assertTrue(result.isFailure)

            // Only one subscribe attempt (the lifecycle one), then we bail out.
            verify(exactly = 1) { socket.subscribe(any<StreamWebSocketListener>(), any()) }
            verify(exactly = 0) { socket.open(any()) }
            verify(exactly = 0) { socket.send(any<String>()) }
            verify(exactly = 0) { health.start() }

            // Opening + Disconnected.Error are emitted, but we don't assert exact count
            verify(atLeast = 1) { subs.forEach(any()) }
        }

    @Test
    fun `connect fails when open fails - no health start, no auth send`() = runTest {
        // Both subscriptions succeed
        every { socket.subscribe(any<StreamWebSocketListener>(), any()) } returnsMany
            listOf(
                Result.success(mockk(relaxed = true)), // lifecycle
                Result.success(mockk(relaxed = true)), // handshake
            )
        // Open fails
        every { socket.open(config) } returns Result.failure(RuntimeException("open failed"))

        val result = session.connect(connectUserData())

        assertTrue(result.isFailure)

        verify(exactly = 2) { socket.subscribe(any<StreamWebSocketListener>(), any()) }
        verify(exactly = 1) { socket.open(config) }
        verify(exactly = 0) { socket.send(any<String>()) }
        verify(exactly = 0) { health.start() }
        verify(atLeast = 1) { subs.forEach(any()) }
    }

    @Test
    fun `connect cancellation invokes cleanup - cancels subs, closes socket, stops processors`() =
        runTest {
            val sub = mockk<StreamSubscription>(relaxed = true)

            every { socket.subscribe(any<StreamWebSocketListener>(), any()) } returns
                Result.success(sub)
            every { socket.open(config) } returns Result.success(Unit)
            every { socket.close() } returns Result.success(Unit)

            val job = async { session.connect(connectUserData()) }

            // Make sure connect() progressed far enough to set up subs & the cancel hook
            advanceUntilIdle()
            verify { socket.subscribe(any<StreamWebSocketListener>()) }
            verify { socket.subscribe(any<StreamWebSocketListener>(), any()) }

            // Cancel and wait for the cancellation handler to run
            job.cancelAndJoin()
            advanceUntilIdle()

            // Cleanup side-effects
            verify(atLeast = 2) { sub.cancel() }
            verify { socket.close() }
            verify { health.stop() }
            verify { debounce.stop() }
        }

    @Test
    fun `onHeartbeat sends serialized health-check when already connected`() = runTest {
        // Arrange: capture the heartbeat callback via answers (no slots)
        var heartbeatCb: (suspend () -> Unit)? = null
        every { health.onHeartbeat(any()) } answers { heartbeatCb = arg(0) }
        every { health.onUnhealthy(any()) } just Runs

        // Wire simple happy-path socket behavior so connect() can run until it suspends
        every { socket.subscribe(any<StreamWebSocketListener>()) } returns
            Result.success(mockk(relaxed = true))
        every { socket.subscribe(any<StreamWebSocketListener>(), any()) } returns
            Result.success(mockk(relaxed = true))
        every { socket.open(config) } returns Result.success(Unit)
        every { socket.close() } returns Result.success(Unit)

        // Kick off connect but don't complete the handshake (it will suspend)
        val job = async {
            session.connect(
                ConnectUserData(
                    userId = "user-1",
                    token = "tok",
                    image = null,
                    invisible = false,
                    language = null,
                    name = null,
                    custom = emptyMap(),
                )
            )
        }
        // Let the coroutine run enough to register the heartbeat callback
        advanceUntilIdle()
        val cb = requireNotNull(heartbeatCb) { "Heartbeat callback not registered" }

        // Pretend we're already connected: set the connected event the heartbeat uses
        val connectedEvt = mockk<StreamClientConnectedEvent>(relaxed = true)
        every { connectedEvt.copy() } returns connectedEvt
        session.javaClass.getDeclaredField("streamClientConnectedEvent").apply {
            isAccessible = true
            set(session, connectedEvt)
        }

        // Expect serialize + send on heartbeat
        every { parser.serialize(connectedEvt) } returns Result.success("HEALTH_JSON")
        every { socket.send("HEALTH_JSON") } returns Result.success("Unit")

        // Act: trigger the heartbeat
        cb.invoke()

        // Assert
        verify(exactly = 1) { parser.serialize(connectedEvt) }
        verify(exactly = 1) { socket.send("HEALTH_JSON") }

        // Cleanup the suspended connect
        job.cancelAndJoin()
    }

    @Test
    fun `onHeartbeat does nothing when connected event is null (no serialize, no send)`() =
        runTest {
            // Capture heartbeat callback via answers (no slots)
            var heartbeatCb: (suspend () -> Unit)? = null
            every { health.onHeartbeat(any()) } answers { heartbeatCb = arg(0) }
            every { health.onUnhealthy(any()) } just Runs

            // Let connect() run to the point where callbacks are registered
            every { socket.subscribe(any<StreamWebSocketListener>()) } returns
                Result.success(mockk(relaxed = true))
            every { socket.subscribe(any<StreamWebSocketListener>(), any()) } returns
                Result.success(mockk(relaxed = true))
            every { socket.open(config) } returns Result.success(Unit)
            every { socket.close() } returns Result.success(Unit)

            val job = async {
                session.connect(
                    ConnectUserData(
                        userId = "user-1",
                        token = "tok",
                        image = null,
                        invisible = false,
                        language = null,
                        name = null,
                        custom = emptyMap(),
                    )
                )
            }
            advanceUntilIdle()

            // Sanity: heartbeat registered
            val cb = requireNotNull(heartbeatCb) { "Heartbeat callback not registered" }

            // Act: trigger heartbeat with streamClientConnectedEvent still null
            cb.invoke()

            // Assert: no health serialize or send occurred
            verify(exactly = 0) { parser.serialize(any()) }
            verify(exactly = 0) { socket.send(any<String>()) }

            // Cleanup
            job.cancelAndJoin()
        }

    @Test
    fun `onUnhealthy triggers disconnect - notifies error, closes socket, cancels subs, stops processors`() =
        runTest {
            // Capture unhealthy callback via answers (no slots)
            var unhealthyCb: (suspend () -> Unit)? = null
            every { health.onHeartbeat(any()) } just Runs
            every { health.onUnhealthy(any()) } answers { unhealthyCb = arg(0) }

            // Collect emitted states from notifyState
            val emittedStates = mutableListOf<StreamConnectionState>()
            every { subs.forEach(any()) } answers
                {
                    val consumer = arg<(StreamClientListener) -> Unit>(0)
                    val listener =
                        object : StreamClientListener {
                            override fun onState(state: StreamConnectionState) {
                                emittedStates += state
                            }

                            override fun onEvent(event: StreamClientWsEvent) {}
                        }
                    consumer(listener)
                    Result.success(Unit)
                }

            val hsSub = mockk<StreamSubscription>(relaxed = true)
            every { socket.subscribe(any<StreamWebSocketListener>(), any()) } returns
                Result.success(hsSub)
            every { socket.open(config) } returns Result.success(Unit)
            every { socket.close() } returns Result.success(Unit)

            // Start connect to register unhealthy callback & create subscriptions
            val job = async {
                session.connect(
                    ConnectUserData(
                        userId = "user-1",
                        token = "tok",
                        image = null,
                        invisible = false,
                        language = null,
                        name = null,
                        custom = emptyMap(),
                    )
                )
            }
            advanceUntilIdle()
            val cb = requireNotNull(unhealthyCb) { "Unhealthy callback not registered" }

            // Act: trigger onUnhealthy
            cb.invoke()

            // Assert: disconnect path executed
            verify(exactly = 1) { socket.close() }
            // All subscriptions are mocked into the hsSub
            verify(exactly = 2) { hsSub.cancel() }
            verify(exactly = 1) { health.stop() }
            verify(exactly = 1) { debounce.stop() }

            assertTrue(emittedStates.any { it is StreamConnectionState.Disconnected })

            // Cleanup suspended connect
            job.cancelAndJoin()
        }

    @Test
    fun `onBatch forwards non-health events, ignores health, and emits Disconnected_Error on connection error`() =
        runTest {
            // Capture onBatch callback (no slots)
            var onBatchCb: (suspend (List<String>, Long, Int) -> Unit)? = null
            every { debounce.onBatch(any()) } answers { onBatchCb = arg(0) }
            every { health.onHeartbeat(any()) } just Runs
            every { health.onUnhealthy(any()) } just Runs

            // Collect events & states emitted via subscription manager
            val seenEvents = mutableListOf<Any>()
            val seenStates = mutableListOf<StreamConnectionState>()
            every { subs.forEach(any()) } answers
                {
                    val consumer = arg<(StreamClientListener) -> Unit>(0)
                    val listener =
                        object : StreamClientListener {
                            override fun onState(state: StreamConnectionState) {
                                seenStates += state
                            }

                            override fun onEvent(event: StreamClientWsEvent) {
                                seenEvents += event
                            }
                        }
                    consumer(listener)
                    Result.success(Unit)
                }

            // WebSocket plumbing to let connect() register callbacks
            every { socket.subscribe(any<StreamWebSocketListener>()) } returns
                Result.success(mockk(relaxed = true))
            every { socket.subscribe(any<StreamWebSocketListener>(), any()) } returns
                Result.success(mockk(relaxed = true))
            every { socket.open(config) } returns Result.success(Unit)
            every { socket.close() } returns Result.success(Unit)

            // Events returned by parser for each message in the batch
            val normalEvent = mockk<StreamClientWsEvent>(relaxed = true)
            val healthEvent = mockk<StreamHealthCheckEvent>(relaxed = true)
            val errorEvent =
                mockk<StreamClientConnectionErrorEvent>(relaxed = true).also {
                    every { it.error } returns mockk(relaxed = true)
                }
            every { parser.deserialize("E1") } returns Result.success(normalEvent)
            every { parser.deserialize("H") } returns Result.success(healthEvent)
            every { parser.deserialize("ERR") } returns Result.success(errorEvent)

            // Start connect so that onBatch is set
            val job = async {
                session.connect(
                    ConnectUserData(
                        userId = "user-1",
                        token = "tok",
                        image = null,
                        invisible = false,
                        language = null,
                        name = null,
                        custom = emptyMap(),
                    )
                )
            }
            advanceUntilIdle()

            val cb = requireNotNull(onBatchCb) { "onBatch not registered" }

            // Act: deliver a batch with a normal event, a health check, and a connection error
            cb.invoke(listOf("E1", "H", "ERR"), 100L, 3)
            advanceUntilIdle()

            // Assert: only non-health events forwarded to listeners
            assertEquals(2, seenEvents.size)
            assertTrue(seenEvents.contains(normalEvent))
            assertTrue(seenEvents.contains(errorEvent))

            // And a Disconnected.Error is emitted due to the connection error event
            assertTrue(seenStates.any { it is StreamConnectionState.Disconnected })

            // Cleanup suspended connect
            job.cancelAndJoin()
        }

    @Test
    fun `onBatch - deserialize fails then fallback parses api error and emits Disconnected_Error`() =
        runTest {
            // Capture the installed onBatch callback
            var onBatchCb: (suspend (List<String>, Long, Int) -> Unit)? = null
            every { debounce.onBatch(any()) } answers { onBatchCb = arg(0) }
            every { health.onHeartbeat(any()) } just Runs
            every { health.onUnhealthy(any()) } just Runs

            // Collect what the session emits to listeners
            val seenStates = mutableListOf<StreamConnectionState>()
            val seenEvents = mutableListOf<Any>()
            every { subs.forEach(any()) } answers
                {
                    val consumer = arg<(StreamClientListener) -> Unit>(0)
                    val listener =
                        object : StreamClientListener {
                            override fun onState(state: StreamConnectionState) {
                                seenStates += state
                            }

                            override fun onEvent(event: StreamClientWsEvent) {
                                seenEvents += event
                            }
                        }
                    consumer(listener)
                    Result.success(Unit)
                }

            // Let connect() run far enough to register callbacks
            every { socket.subscribe(any<StreamWebSocketListener>()) } returns
                Result.success(mockk(relaxed = true))
            every { socket.subscribe(any<StreamWebSocketListener>(), any()) } returns
                Result.success(mockk(relaxed = true))
            every { socket.open(config) } returns Result.success(Unit)
            every { socket.close() } returns Result.success(Unit)

            // Parser fails -> fallback JSON parse as API error succeeds
            val apiError = mockk<StreamEndpointErrorData>(relaxed = true)
            every { parser.deserialize("BAD_JSON") } returns
                Result.failure(IllegalStateException("boom"))
            every { json.fromJson("BAD_JSON", StreamEndpointErrorData::class.java) } returns
                Result.success(apiError)

            // Start connect to install the onBatch handler
            val job = async {
                session.connect(
                    ConnectUserData(
                        userId = "u",
                        token = "t",
                        image = null,
                        invisible = false,
                        language = null,
                        name = null,
                        custom = emptyMap(),
                    )
                )
            }
            advanceUntilIdle()

            // Invoke onBatch with a single failing payload
            val cb = requireNotNull(onBatchCb) { "onBatch not registered" }
            cb.invoke(listOf("BAD_JSON"), 100L, 1)
            advanceUntilIdle()

            // Assert: no events forwarded, but a Disconnected.Error was emitted from API error
            // fallback
            assertTrue(seenEvents.isEmpty())
            assertTrue(seenStates.any { it is StreamConnectionState.Disconnected })

            // And we did try to parse the payload as an API error
            verify { json.fromJson("BAD_JSON", StreamEndpointErrorData::class.java) }

            job.cancelAndJoin()
        }

    @Test
    fun `handshake onMessage Connected triggers success - starts health, emits Connected, cancels handshake sub`() =
        runTest {
            // Record states/events emitted to listeners
            val seenStates = mutableListOf<StreamConnectionState>()
            every { subs.forEach(any()) } answers
                {
                    val consumer = arg<(StreamClientListener) -> Unit>(0)
                    val listener =
                        object : StreamClientListener {
                            override fun onState(state: StreamConnectionState) {
                                seenStates += state
                            }

                            override fun onEvent(event: StreamClientWsEvent) {
                                /* not needed here */
                            }
                        }
                    consumer(listener)
                    Result.success(Unit)
                }

            // Mocks for subscriptions
            val lifeSub = mockk<StreamSubscription>(relaxed = true)
            val hsSub = mockk<StreamSubscription>(relaxed = true)

            // 1) Lifecycle subscribe succeeds
            every { socket.subscribe(any<StreamWebSocketListener>()) } returns
                Result.success(lifeSub)

            // 2) Handshake subscribe stores the listener so we can trigger onMessage later
            var hsListener: StreamWebSocketListener? = null
            every { socket.subscribe(any<StreamWebSocketListener>(), any()) } answers
                {
                    hsListener = firstArg()
                    Result.success(hsSub)
                }

            // 3) Open calls back into the handshake listener with a "connected" message
            every { socket.open(config) } answers
                {
                    // Simulate server sending a Connected event AFTER handshakeSub is set
                    val connectedEvent =
                        mockk<StreamClientConnectedEvent>(relaxed = true).also {
                            every { it.connectionId } returns "conn-xyz"
                        }
                    every { parser.deserialize("CONNECTED_JSON") } returns
                        Result.success(connectedEvent)
                    hsListener!!.onMessage("CONNECTED_JSON")
                    Result.success(Unit)
                }

            // Misc socket ops not used here
            every { socket.close() } returns Result.success(Unit)
            every { socket.send(any<String>()) } returns Result.success("Unit")

            // Act
            val res =
                async {
                        session.connect(
                            ConnectUserData(
                                userId = "user-123",
                                token = "tok",
                                image = null,
                                invisible = false,
                                language = null,
                                name = null,
                                custom = emptyMap(),
                            )
                        )
                    }
                    .await()

            // Assert: connect() succeeded
            assertTrue(res.isSuccess)

            // Health started upon success
            verify { health.start() }

            // Handshake subscription cancelled by success lambda
            verify { hsSub.cancel() }

            // A Connected state was emitted
            assertTrue(seenStates.any { it is StreamConnectionState.Connected })

            // And open() was called
            verify { socket.open(config) }
        }

    @Test
    fun `handshake onMessage ConnectionError triggers apiFailure - emits Disconnected_Error, cancels only handshake sub`() =
        runTest {
            // Capture emitted states
            val seenStates = mutableListOf<StreamConnectionState>()
            every { subs.forEach(any()) } answers
                {
                    val consumer = arg<(StreamClientListener) -> Unit>(0)
                    val listener =
                        object : StreamClientListener {
                            override fun onState(state: StreamConnectionState) {
                                seenStates += state
                            }

                            override fun onEvent(event: StreamClientWsEvent) {}
                        }
                    consumer(listener)
                    Result.success(Unit)
                }

            // Subscriptions
            val lifeSub = mockk<StreamSubscription>(relaxed = true)
            val hsSub = mockk<StreamSubscription>(relaxed = true)
            every { socket.subscribe(any<StreamWebSocketListener>()) } returns
                Result.success(lifeSub)

            var hsListener: StreamWebSocketListener? = null
            every { socket.subscribe(any<StreamWebSocketListener>(), any()) } answers
                {
                    hsListener = firstArg()
                    Result.success(hsSub)
                }

            // Open -> immediately deliver a ConnectionError event via the handshake listener
            val errEvent = mockk<StreamClientConnectionErrorEvent>(relaxed = true)
            every { errEvent.error } returns mockk(relaxed = true)
            every { parser.deserialize("ERR_JSON") } returns Result.success(errEvent)

            every { socket.open(config) } answers
                {
                    hsListener!!.onMessage("ERR_JSON")
                    Result.success(Unit)
                }

            // Not used here but keep stubs relaxed
            every { socket.close() } returns Result.success(Unit)
            every { socket.send(any<String>()) } returns Result.success("Unit")

            // Act
            val result =
                async {
                        session.connect(
                            ConnectUserData(
                                userId = "user-err",
                                token = "tok",
                                image = null,
                                invisible = false,
                                language = null,
                                name = null,
                                custom = emptyMap(),
                            )
                        )
                    }
                    .await()

            // Assert: connect() failed
            assertTrue(result.isFailure)

            // apiFailure cancels only the handshake subscription
            verify { hsSub.cancel() }
            verify(exactly = 0) { lifeSub.cancel() }

            // Health must NOT start on failure
            verify(exactly = 0) { health.start() }

            // States: Opening then Disconnected.Error at least once
            assertTrue(seenStates.first() is StreamConnectionState.Connecting.Opening)
            assertTrue(seenStates.any { it is StreamConnectionState.Disconnected })

            // open() was called (handshake path reached)
            verify { socket.open(config) }
        }

    @Test
    fun `connect fails when handshake subscribe fails - cancels lifecycle, no open`() = runTest {
        // Capture state emissions
        val states = mutableListOf<StreamConnectionState>()
        every { subs.forEach(any()) } answers
            {
                val consumer = arg<(StreamClientListener) -> Unit>(0)
                val listener =
                    object : StreamClientListener {
                        override fun onState(state: StreamConnectionState) {
                            states += state
                        }

                        override fun onEvent(event: StreamClientWsEvent) {}
                    }
                consumer(listener)
                Result.success(Unit)
            }

        // Handshake subscribe fails -> should enter hsRes.isFailure branch
        val boom = RuntimeException("hs-subscribe failed")
        every { socket.subscribe(any<StreamWebSocketListener>(), any()) } returns
            Result.failure(boom)

        // Ensure open is NOT called in this branch
        every { socket.open(any()) } answers
            {
                throw IllegalStateException(
                    "open() must not be called when handshake subscribe fails"
                )
            }

        val result =
            async {
                    session.connect(
                        ConnectUserData(
                            userId = "user-hs-fail",
                            token = "tok",
                            image = null,
                            invisible = false,
                            language = null,
                            name = null,
                            custom = emptyMap(),
                        )
                    )
                }
                .await()

        // Surfaces failure
        assertTrue(result.isFailure)

        // Handshake subscribe was attempted once and failed
        verify { socket.subscribe(any<StreamWebSocketListener>(), any()) }
        // Must NOT attempt to open the socket after handshake subscribe failure
        verify(exactly = 0) { socket.open(any()) }
        // Health must not start
        verify(exactly = 0) { health.start() }

        // States include Opening then Disconnected.Error
        assertTrue(states.first() is StreamConnectionState.Connecting.Opening)
        assertTrue(states.any { it is StreamConnectionState.Disconnected })
    }

    @Test
    fun `handshake onMessage deserialize failure triggers failure - cancels both subs, emits Disconnected_Error`() =
        runTest {
            // Capture state emissions
            val states = mutableListOf<StreamConnectionState>()
            every { subs.forEach(any()) } answers
                {
                    val consumer = arg<(StreamClientListener) -> Unit>(0)
                    val listener =
                        object : StreamClientListener {
                            override fun onState(state: StreamConnectionState) {
                                states += state
                            }

                            override fun onEvent(event: StreamClientWsEvent) {}
                        }
                    consumer(listener)
                    Result.success(Unit)
                }

            val hsSub = mockk<StreamSubscription>(relaxed = true)

            var hsListener: StreamWebSocketListener? = null
            every { socket.subscribe(any<StreamWebSocketListener>(), any()) } answers
                {
                    hsListener = firstArg()
                    Result.success(hsSub)
                }

            // When open() is called, simulate a bad handshake payload that fails to deserialize.
            every { parser.deserialize("BAD_JSON") } returns
                Result.failure(RuntimeException("parse fail"))
            every { socket.open(config) } answers
                {
                    // simulate handshake message arriving after open
                    hsListener!!.onMessage("BAD_JSON")
                    Result.success(Unit)
                }

            // Not used but keep relaxed
            every { socket.close() } returns Result.success(Unit)

            val result =
                async {
                        session.connect(
                            ConnectUserData(
                                userId = "user-bad-json",
                                token = "tok",
                                image = null,
                                invisible = false,
                                language = null,
                                name = null,
                                custom = emptyMap(),
                            )
                        )
                    }
                    .await()

            // connect() fails due to deserialize error -> recover { failure(it) }
            assertTrue(result.isFailure)

            // failure() cancels both subs here
            verify { hsSub.cancel() }

            // Health must NOT start on failure
            verify(exactly = 0) { health.start() }

            // States include Opening and Disconnected.Error
            assertTrue(states.first() is StreamConnectionState.Connecting.Opening)
            assertTrue(states.any { it is StreamConnectionState.Disconnected })

            // Open was invoked as part of the normal handshake path
            verify { socket.open(config) }
        }

    @Test
    fun `onOpen 101 serializes auth and sends it`() = runTest {
        val lifeSub = mockk<StreamSubscription>(relaxed = true)
        val hsSub = mockk<StreamSubscription>(relaxed = true)

        every { socket.subscribe(any<StreamWebSocketListener>()) } returns Result.success(lifeSub)

        var hsListener: StreamWebSocketListener? = null
        every { socket.subscribe(any<StreamWebSocketListener>(), any()) } answers
            {
                hsListener = firstArg()
                Result.success(hsSub)
            }

        // Serialize succeeds -> should call send with this payload
        every { parser.serialize(any()) } returns Result.success("AUTH_PAYLOAD")
        every { socket.send("AUTH_PAYLOAD") } returns Result.success("Unit")

        // Avoid leaks when we cancel at the end
        every { socket.close() } returns Result.success(Unit)

        // When open is called, trigger handshake onOpen(101)
        every { socket.open(config) } answers
            {
                val resp =
                    Response.Builder()
                        .request(Request.Builder().url("https://example.test").build())
                        .protocol(Protocol.HTTP_1_1)
                        .code(101)
                        .message("OK")
                        .build()
                hsListener?.onOpen(resp) ?: error("Handshake listener not installed")
                Result.success(Unit)
            }

        val job = async { session.connect(connectUserData()) }

        // Let the onOpen handler run
        advanceUntilIdle()

        // Verify we sent the serialized auth payload
        verify(exactly = 1) { parser.serialize(any()) }
        verify(exactly = 1) { socket.send("AUTH_PAYLOAD") }

        // Clean up the still-pending connect
        job.cancel()
    }

    private fun connectUserData(): ConnectUserData =
        ConnectUserData("u", "t", null, null, false, null, emptyMap())
}
