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

package io.getstream.android.core.internal.processing

import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class StreamRestartableChannelTest {

    // ---------- Helpers ----------

    /** Replace the private `ref` (AtomicReference<Channel<T>>) with a spy/wrapper channel. */
    @Suppress("UNCHECKED_CAST")
    private fun <T> replaceDelegate(target: StreamRestartableChannel<T>, newDelegate: Channel<T>) {
        val f: Field = StreamRestartableChannel::class.java.getDeclaredField("ref")
        f.isAccessible = true
        val ref = f.get(target) as AtomicReference<Channel<T>>
        ref.set(newDelegate)
    }

    /** Channel spy that forwards to a backing channel and counts calls/records causes. */
    private class SpyChannel<T>(private val backing: Channel<T>) : Channel<T> {
        var sendCount = 0
        var trySendCount = 0
        var receiveCount = 0
        var tryReceiveCount = 0
        var receiveCatchingCount = 0
        var closeCount = 0
        var lastCloseCause: Throwable? = null
        var invokeOnCloseCount = 0
        var cancelCount = 0
        var lastCancelCE: CancellationException? = null

        override suspend fun send(element: T) {
            sendCount++
            backing.send(element)
        }

        override fun trySend(element: T): ChannelResult<Unit> {
            trySendCount++
            return backing.trySend(element)
        }

        override suspend fun receive(): T {
            receiveCount++
            return backing.receive()
        }

        override fun tryReceive(): ChannelResult<T> {
            tryReceiveCount++
            return backing.tryReceive()
        }

        override suspend fun receiveCatching(): ChannelResult<T> {
            receiveCatchingCount++
            return backing.receiveCatching()
        }

        override val onSend = backing.onSend
        override val onReceive = backing.onReceive
        override val onReceiveCatching = backing.onReceiveCatching

        override val isClosedForSend
            get() = backing.isClosedForSend

        override val isClosedForReceive
            get() = backing.isClosedForReceive

        override val isEmpty
            get() = backing.isEmpty

        override fun iterator(): ChannelIterator<T> = backing.iterator()

        override fun invokeOnClose(handler: (cause: Throwable?) -> Unit) {
            invokeOnCloseCount++
            backing.invokeOnClose(handler)
        }

        override fun close(cause: Throwable?): Boolean {
            closeCount++
            lastCloseCause = cause
            return backing.close(cause)
        }

        override fun cancel(cause: CancellationException?) {
            cancelCount++
            lastCancelCE = cause
            backing.cancel(cause)
        }

        override fun cancel(cause: Throwable?): Boolean = true
    }

    // ---------- Core behavior ----------

    @Test
    fun `send and receive on buffered channel`() = runTest {
        val ch = StreamRestartableChannel<Int>() // buffered
        ch.send(1)
        ch.send(2)
        assertEquals(1, ch.receive())
        assertEquals(2, ch.receive())
        assertTrue(ch.isEmpty)
    }

    @Test
    fun `trySend respects capacity and resumes after receive`() = runTest {
        val ch = StreamRestartableChannel<Int>(capacity = 1)
        assertTrue(ch.trySend(10).isSuccess)
        assertTrue(ch.trySend(20).isFailure) // full
        assertEquals(10, ch.receive())
        assertTrue(ch.trySend(20).isSuccess)
        assertEquals(20, ch.receive())
    }

    @Test
    fun `rendezvous receive suspends until send arrives`() = runTest {
        val ch = StreamRestartableChannel<Int>(capacity = 0)
        val rcv = async { ch.receive() }
        assertFalse(rcv.isCompleted)
        ch.send(7)
        assertEquals(7, rcv.await())
    }

    @Test
    fun `rendezvous send suspends until receiver arrives`() = runTest {
        val ch = StreamRestartableChannel<Int>(capacity = 0)
        val snd = async { ch.send(5) }
        assertFalse(snd.isCompleted)
        val rcv = async { ch.receive() }
        assertEquals(5, rcv.await())
        snd.await()
    }

    // ---------- Select clauses (exercises onSend, onReceive, onReceiveCatching getters) ----------

    @Test
    fun `onSend select clause completes when a receiver arrives`() = runTest {
        val ch = StreamRestartableChannel<Int>(capacity = 0)
        val s = async { select<Boolean> { ch.onSend(42) { true } } }
        assertFalse(s.isCompleted)
        val r = async { ch.receive() }
        assertTrue(s.await())
        assertEquals(42, r.await())
    }

    @Test
    fun `onReceive select clause picks up a value`() = runTest {
        val ch = StreamRestartableChannel<Int>(capacity = 0)
        val recv = async { select<Int> { ch.onReceive { it } } }
        assertFalse(recv.isCompleted)
        val snd = async { ch.send(9) }
        assertEquals(9, recv.await())
        snd.await()
    }

    @Test
    fun `onReceiveCatching select observes close`() = runTest {
        val ch = StreamRestartableChannel<Int>()
        val observedClosed = async { select<Boolean> { ch.onReceiveCatching { it.isClosed } } }
        assertFalse(observedClosed.isCompleted)
        ch.close(null)
        assertTrue(observedClosed.await())
    }

    // ---------- Close & flags ----------

    @Test
    fun `close without items - receiveCatching reports closed and flags set`() = runTest {
        val ch = StreamRestartableChannel<Int>()
        assertTrue(ch.close(null)) // first close returns true
        val rc = ch.receiveCatching()
        assertTrue(rc.isClosed)
        assertTrue(ch.isClosedForSend)
        assertTrue(ch.isClosedForReceive)
        // second close returns false
        assertFalse(ch.close(null))
    }

    @Test
    fun `close with queued items - drain then closed for receive`() = runTest {
        val ch = StreamRestartableChannel<Int>()
        ch.send(1)
        ch.send(2)
        ch.close(null)

        assertEquals(1, ch.receive())
        assertFalse(ch.isClosedForReceive)
        assertEquals(2, ch.receive())
        assertTrue(ch.isClosedForReceive)
        assertTrue(ch.receiveCatching().isClosed)
    }

    @Test
    fun `send and trySend fail after close`() = runTest {
        val ch = StreamRestartableChannel<Int>()
        ch.close(null)
        val thrown = runCatching { ch.send(1) }.exceptionOrNull()
        assertNotNull(thrown)
        assertTrue(ch.trySend(2).isFailure)
    }

    // ---------- start() semantics ----------

    @Test
    fun `start is a no-op when channel is open`() = runTest {
        val ch = StreamRestartableChannel<Int>()
        ch.start() // no-op
        ch.send(1)
        assertEquals(1, ch.receive())
    }

    @Test
    fun `start after close replaces channel and allows sending again`() = runTest {
        val ch = StreamRestartableChannel<Int>()
        ch.close(null)
        assertTrue(ch.isClosedForSend)
        ch.start()
        assertFalse(ch.isClosedForSend)
        ch.send(3)
        assertEquals(3, ch.receive())
    }

    @Test
    fun `cancel with CancellationException closes channel`() = runTest {
        val ch = StreamRestartableChannel<Int>()
        val spy = SpyChannel(Channel<Int>(Channel.BUFFERED))
        replaceDelegate(ch, spy)

        val ce = CancellationException("stop")
        ch.cancel(ce)
        assertEquals(1, spy.cancelCount)
        assertSame(ce, spy.lastCancelCE)
    }

    // ---------- invokeOnClose & iterator ----------

    @Test
    fun `invokeOnClose is forwarded and receives cause`() = runTest {
        val ch = StreamRestartableChannel<Int>()
        var observed: Throwable? = null
        ch.invokeOnClose { observed = it }
        val cause = IllegalArgumentException("x")
        ch.close(cause)
        assertSame(cause, observed)
    }

    @Test
    fun `iterator consumes items then completes after close`() = runTest {
        val ch = StreamRestartableChannel<Int>()
        ch.send(1)
        ch.send(2)
        ch.close(null)

        val collected = mutableListOf<Int>()
        for (x in ch) {
            collected.add(x)
        }
        assertEquals(listOf(1, 2), collected)
    }

    @Test
    fun `all operations are delegated to underlying channel`() = runTest {
        val backing = Channel<Int>(capacity = 1)
        val spy = SpyChannel(backing)
        val ch = StreamRestartableChannel<Int>()
        replaceDelegate(ch, spy)

        // trySend & isEmpty change
        assertTrue(ch.isEmpty)
        assertTrue(ch.trySend(10).isSuccess)
        assertFalse(ch.isEmpty)

        // tryReceive (consumes), then send & receive
        val r1 = ch.tryReceive()
        assertTrue(r1.isSuccess && r1.getOrNull() == 10)

        ch.send(20)
        assertEquals(20, ch.receive())

        // Channel is open & empty now — use tryReceive (non-suspending)
        assertTrue(ch.tryReceive().isFailure)
        assertTrue(ch.close())
        // After close, receiveCatching completes immediately with closed
        assertTrue(ch.receiveCatching().isClosed)

        assertFalse(ch.close(null)) // second time

        // cancel
        ch.cancel(CancellationException("ce"))

        // Assertions on spy counters
        assertEquals(1, spy.sendCount) // ch.send(20)
        assertEquals(1, spy.trySendCount) // trySend(10)
        assertEquals(1, spy.receiveCount) // receive() once (for 20)
        assertEquals(2, spy.tryReceiveCount) // tryReceive() for 10, then failure after draining
        assertEquals(1, spy.receiveCatchingCount) // after close
        assertEquals(2, spy.closeCount)
        assertEquals(1, spy.cancelCount)
    }

    @Test
    fun `capacity=2 buffers two items then backpressure`() = runTest {
        val ch = StreamRestartableChannel<Int>(capacity = 2)

        // Fill buffer
        assertTrue(ch.trySend(1).isSuccess)
        assertTrue(ch.trySend(2).isSuccess)

        // Next trySend should fail (buffer full)
        assertTrue(ch.trySend(3).isFailure)

        // Drain one -> space available again
        assertEquals(1, ch.receive())
        assertTrue(ch.trySend(3).isSuccess)

        // FIFO preserved
        assertEquals(2, ch.receive())
        assertEquals(3, ch.receive())
    }

    @Test
    fun `capacity preserved across restart (capacity=2)`() = runTest {
        val ch = StreamRestartableChannel<Int>(capacity = 2)

        // Close & restart -> new delegate with SAME capacity
        ch.close(null)
        ch.start()

        // Should again allow 2 items, then backpressure
        assertTrue(ch.trySend(10).isSuccess)
        assertTrue(ch.trySend(20).isSuccess)
        assertTrue(ch.trySend(30).isFailure)

        assertEquals(10, ch.receive())
        assertTrue(ch.trySend(30).isSuccess)
        assertEquals(20, ch.receive())
        assertEquals(30, ch.receive())
    }

    @Test
    fun `rendezvous capacity is preserved across restart (capacity=0)`() = runTest {
        val ch = StreamRestartableChannel<Int>(capacity = 0)

        // send suspends until a receiver arrives
        val sender = async {
            // returns after receiver consumes
            ch.send(7)
            7
        }
        assertFalse(sender.isCompleted)

        val recv = async { ch.receive() }
        assertEquals(7, recv.await())
        assertEquals(7, sender.await())

        // Close & restart -> still rendezvous
        ch.close(null)
        ch.start()

        val sender2 = async {
            ch.send(8)
            8
        }
        assertFalse(sender2.isCompleted)
        val recv2 = async { ch.receive() }
        assertEquals(8, recv2.await())
        assertEquals(8, sender2.await())
    }

    @Test
    fun `conflated capacity keeps only latest and is preserved across restart`() = runTest {
        val ch = StreamRestartableChannel<Int>(capacity = Channel.CONFLATED)

        // Multiple sends before a receive -> only the last is kept
        assertTrue(ch.trySend(1).isSuccess)
        assertTrue(ch.trySend(2).isSuccess)
        assertTrue(ch.trySend(3).isSuccess)
        assertEquals(3, ch.receive())

        // Close & restart -> still conflated
        ch.close(null)
        ch.start()

        assertTrue(ch.trySend(4).isSuccess)
        assertTrue(ch.trySend(5).isSuccess)
        assertEquals(5, ch.receive())
    }

    @Test
    fun `unlimited capacity accepts many items and preserves order`() = runTest {
        val ch = StreamRestartableChannel<Int>(capacity = Channel.UNLIMITED)

        // Push a bunch; all should succeed
        for (i in 0 until 100) {
            assertTrue(ch.trySend(i).isSuccess)
        }
        // Order preserved
        for (i in 0 until 100) {
            assertEquals(i, ch.receive())
        }
    }
}
