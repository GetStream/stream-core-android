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

package io.getstream.android.core.internal.processing

import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.processing.StreamSerialProcessingQueue
import io.mockk.mockk
import junit.framework.Assert.assertEquals
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSerialProcessingQueueImplTest {
    // Helper to construct the queue with the new signature.
    private fun makeQueue(
        scope: CoroutineScope,
        autoStart: Boolean = true,
        startMode: CoroutineStart = CoroutineStart.DEFAULT,
        capacity: Int = Channel.BUFFERED,
    ): StreamSerialProcessingQueueImpl =
        StreamSerialProcessingQueueImpl(
            logger = mockk<StreamLogger>(relaxed = true),
            scope = scope,
            autoStart = autoStart,
            startMode = startMode,
            capacity = capacity,
        )

    @Test
    fun `runs tasks strictly in submission order`() = runTest {
        val queue: StreamSerialProcessingQueue = makeQueue(backgroundScope)

        val seen = mutableListOf<Int>()

        val a = async {
            queue.submit {
                seen.add(1)
                yield()
                1
            }
        }
        val b = async {
            queue.submit {
                seen.add(2)
                2
            }
        }
        val c = async {
            queue.submit {
                seen.add(3)
                3
            }
        }

        assertEquals(1, a.await().getOrThrow())
        assertEquals(2, b.await().getOrThrow())
        assertEquals(3, c.await().getOrThrow())

        assertEquals(listOf(1, 2, 3), seen)
    }

    @Test
    fun `exceptions are captured into Result failure`() = runTest {
        val queue: StreamSerialProcessingQueue = makeQueue(backgroundScope)

        val ok = queue.submit { 100 }
        val bad = queue.submit<Int> { error("boom") }
        val ok2 = queue.submit { 200 }

        advanceUntilIdle()

        assertTrue(ok.isSuccess)
        assertTrue(bad.isFailure)
        assertTrue(ok2.isSuccess)
        assertEquals(100, ok.getOrThrow())
        assertEquals(200, ok2.getOrThrow())
    }

    @Test
    fun `backpressure - third submit suspends until worker drains`() = runTest {
        val queue =
            makeQueue(scope = backgroundScope, startMode = CoroutineStart.DEFAULT, capacity = 1)

        val events = mutableListOf<String>()

        val a = async {
            queue
                .submit {
                    events += "A-start"
                    delay(1000)
                    events += "A-end"
                    "A"
                }
                .getOrThrow()
        }

        val b = async {
            queue
                .submit {
                    events += "B"
                    "B"
                }
                .getOrThrow()
        }

        val thirdStarted = CompletableDeferred<Unit>()
        val thirdCompleted = CompletableDeferred<Unit>()

        val c = async {
            thirdStarted.complete(Unit)
            val res =
                queue.submit {
                    events += "C"
                    "C"
                }
            thirdCompleted.complete(Unit)
            res.getOrThrow()
        }

        thirdStarted.await()
        testScheduler.runCurrent()
        assertFalse(thirdCompleted.isCompleted)

        advanceUntilIdle()

        assertEquals("A", a.await())
        assertEquals("B", b.await())
        assertEquals("C", c.await())
        assertEquals(listOf("A-start", "A-end", "B", "C"), events)
    }

    @Test
    fun `stop cancels running job and fails queued jobs`() = runTest {
        val queue =
            makeQueue(
                scope = backgroundScope,
                startMode = CoroutineStart.DEFAULT,
                capacity = Channel.BUFFERED,
            )

        val running = async {
            queue.submit {
                delay(10_000)
                "RUN"
            }
        }

        val queued = async { queue.submit { "QUEUED" } }

        testScheduler.runCurrent()

        val stopRes = queue.stop()
        assertTrue(stopRes.isSuccess)

        val runRes = running.await()
        val queuedRes = queued.await()

        assertTrue(runRes.isFailure)
        assertTrue(runRes.exceptionOrNull() is CancellationException)

        assertTrue(queuedRes.isFailure)
        assertTrue(queuedRes.exceptionOrNull() is ClosedSendChannelException)
    }

    @Test
    fun `stop is idempotent`() = runTest {
        val queue = makeQueue(scope = backgroundScope, startMode = CoroutineStart.DEFAULT)

        val first = async { queue.submit { 42 } }
        assertEquals(42, first.await().getOrThrow())

        val s1 = queue.stop()
        val s2 = queue.stop()

        assertTrue(s1.isSuccess)
        assertTrue(s2.isSuccess)
    }

    @Test
    fun `worker failure - pending jobs are failed`() = runTest {
        val queue = makeQueue(scope = backgroundScope, startMode = CoroutineStart.DEFAULT)

        val boom = RuntimeException("boom")

        val j1 = async { queue.submit<Unit> { throw boom } }
        val j2 = async { queue.submit { "second" } }

        advanceUntilIdle()

        val r1 = j1.await()
        val r2 = j2.await()

        assertTrue(r1.isFailure)
        assertEquals(boom, r1.exceptionOrNull())
        assertTrue(r2.isSuccess)
    }

    @Test
    fun `success and failure propagate as Result`() = runTest {
        val queue = makeQueue(scope = backgroundScope, startMode = CoroutineStart.DEFAULT)

        val ok = queue.submit { 7 }
        assertTrue(ok.isSuccess)
        assertEquals(7, ok.getOrThrow())

        val ex = IllegalStateException("x")
        val fail = queue.submit<Unit> { throw ex }
        assertTrue(fail.isFailure)
        assertEquals(ex, fail.exceptionOrNull())
    }

    @Test
    fun `preserves FIFO ordering across many tasks`() = runTest {
        val queue = makeQueue(scope = backgroundScope, startMode = CoroutineStart.DEFAULT)

        val seen = mutableListOf<Int>()
        val jobs =
            (1..20).map { i ->
                async {
                    queue
                        .submit {
                            seen.add(i)
                            i
                        }
                        .getOrThrow()
                }
            }

        jobs.awaitAll()
        assertEquals((1..20).toList(), seen)
    }

    @Test
    fun `submit on a closed channel returns failed result`() = runTest {
        val queue = makeQueue(scope = backgroundScope, startMode = CoroutineStart.DEFAULT)

        queue.stop()

        val re = queue.submit { "result" }

        assertTrue(re.isFailure)
        kotlin.test.assertEquals(ClosedSendChannelException::class, re.exceptionOrNull()!!::class)
    }

    @Test
    fun `stop fails jobs after timeout for the running job to complete`() = runTest {
        val queue = makeQueue(scope = backgroundScope, startMode = CoroutineStart.DEFAULT)

        val j1 = async {
            queue.submit<Unit> {
                delay(500)
                "will fail"
            }
        }

        val j2 = async {
            queue.submit {
                delay(30_000)
                "will succeed"
            }
        }

        advanceTimeBy(1000)
        queue.stop()
        advanceUntilIdle()

        val r1 = j1.await()
        val r2 = j2.await()

        assertTrue(r1.isSuccess)
        assertTrue(r2.isFailure)
    }

    @Test
    fun `canceled scope returns failed result`() = runTest {
        val scope = CoroutineScope(backgroundScope.coroutineContext)
        scope.cancel()

        val queue = makeQueue(scope = scope, startMode = CoroutineStart.DEFAULT)

        val j1 = async {
            queue.submit<Unit> {
                delay(500)
                "will fail"
            }
        }
        val r1 = j1.await()
        assertTrue(r1.isFailure)
    }

    @Test
    fun `second submit does NOT spin up another worker`() = runTest {
        val q = makeQueue(scope = backgroundScope, startMode = CoroutineStart.UNDISPATCHED)

        assertEquals(Result.success(1), q.submit { 1 })
        advanceUntilIdle()

        val workerField = q.javaClass.getDeclaredField("worker").apply { isAccessible = true }
        val firstWorker = workerField.get(q) as Job

        assertEquals(Result.success(2), q.submit { 2 })
        advanceUntilIdle()

        val secondWorker = workerField.get(q) as Job
        assertSame(firstWorker, secondWorker)
    }

    @Test
    fun `second submit sees worker != null inside withLock`() = runTest {
        val queue = makeQueue(scope = backgroundScope, startMode = CoroutineStart.UNDISPATCHED)

        val insideLock = CompletableDeferred<Unit>()
        val mayContinueA = CompletableDeferred<Unit>()

        val startMutexField =
            queue.javaClass.getDeclaredField("startMutex").apply { isAccessible = true }
        val realMutex = startMutexField.get(queue) as Mutex

        val proxyMutex =
            object : Mutex by realMutex {
                override suspend fun lock(owner: Any?) {
                    insideLock.complete(Unit)
                    mayContinueA.await()
                    return realMutex.lock(owner)
                }
            }
        startMutexField.set(queue, proxyMutex)

        val a = async { queue.submit { 1 } }

        insideLock.await()

        val b = async { queue.submit { 2 } }

        mayContinueA.complete(Unit)

        advanceUntilIdle()

        assertEquals(Result.success(1), a.await())
        assertEquals(Result.success(2), b.await())
    }

    @Test
    fun `stop is graceful if worker == null`() = runTest {
        val q = makeQueue(scope = backgroundScope, startMode = CoroutineStart.UNDISPATCHED)

        assertEquals(Result.success(1), q.submit { 1 })
        advanceUntilIdle()

        val workerField = q.javaClass.getDeclaredField("worker").apply { isAccessible = true }

        assertEquals(Result.success(2), q.submit { 2 })
        advanceUntilIdle()

        workerField.set(q, null)

        val stopResult = q.stop(1000)
        assertTrue(stopResult.isSuccess)
    }

    @Test
    fun `stop() hard-cancels when worker ignores cancellation`() = runTest {
        val queue = makeQueue(scope = this, startMode = CoroutineStart.UNDISPATCHED)

        launch { queue.submit { delay(500) } }

        advanceTimeBy(1)
        val stopDeferred = async { queue.stop(1) }
        advanceTimeBy(1)

        assertTrue(stopDeferred.await().isSuccess)
    }

    @Test
    fun `stop() waits for the job to finish if its within timeout`() = runTest {
        val queue = makeQueue(scope = this, startMode = CoroutineStart.UNDISPATCHED)

        val job = async { queue.submit { delay(500) } }

        advanceTimeBy(550)
        val stopDeferred = async { queue.stop(600) }
        advanceTimeBy(60)

        assertTrue(job.await().isSuccess)
        assertTrue(stopDeferred.await().isSuccess)
    }

    @Test
    fun `stop() does not waits for the job to finish if its outside timeout`() = runTest {
        val queue = makeQueue(scope = this, startMode = CoroutineStart.UNDISPATCHED)

        val job = async { queue.submit { delay(1500) } }

        advanceTimeBy(550)
        val stopDeferred = async { queue.stop(600) }
        advanceTimeBy(60)

        assertTrue(job.await().isFailure)
        assertTrue(stopDeferred.await().isSuccess)
    }

    @Test
    fun `stop(actual) actual timeout is used when supplied`() = runTest {
        val queue = makeQueue(scope = this, startMode = CoroutineStart.UNDISPATCHED)

        val job = async { queue.submit { delay(500) } }

        advanceTimeBy(550)
        val stopDeferred = async { queue.stop(600) }
        advanceTimeBy(60)

        assertTrue(job.await().isSuccess)
        assertTrue(stopDeferred.await().isSuccess)
    }

    @Test
    fun `stop(null) cancels running job at once`() = runTest {
        val queue = makeQueue(scope = this, startMode = CoroutineStart.UNDISPATCHED)

        val job = async { queue.submit { delay(1500) } }

        advanceTimeBy(550)
        val stopDeferred = async { queue.stop(null) }
        advanceTimeBy(60)

        assertTrue(job.await().isFailure)
        assertTrue(stopDeferred.await().isSuccess)
    }

    @Test
    fun `fresh channel isClosed is false`() = runTest {
        val ch = StreamRestartableChannel<Int>()
        assertFalse(ch.isClosed())
        assertFalse(ch.isClosedForSend)
        assertFalse(ch.isClosedForReceive)
    }

    @Test
    fun `close empty channel - isClosed becomes true`() = runTest {
        val ch = StreamRestartableChannel<Int>()
        ch.close(null)

        // Immediately closed for send; receive end will be closed (no buffered items)
        assertTrue(ch.isClosed())
        assertTrue(ch.isClosedForSend)

        // Observe closure to make the receive-side state concrete
        val rc = ch.receiveCatching()
        assertTrue(rc.isClosed)
        assertTrue(ch.isClosedForReceive)
    }

    @Test
    fun `close with buffered items - isClosed true before and after draining`() = runTest {
        val ch = StreamRestartableChannel<Int>()
        ch.send(1) // buffer one item
        ch.close(null) // close while item present

        // Send side is closed; receive side still open until drained
        assertTrue(ch.isClosed()) // our convenience OR should already be true
        assertTrue(ch.isClosedForSend)
        assertFalse(ch.isClosedForReceive)

        // Drain the remaining item
        assertEquals(1, ch.receive())

        // Now both ends are closed
        assertTrue(ch.isClosed())
        assertTrue(ch.isClosedForReceive)
        assertTrue(ch.receiveCatching().isClosed)
    }

    @Test
    fun `cancel with CancellationException - isClosed true`() = runTest {
        val ch = StreamRestartableChannel<Int>()
        ch.cancel(CancellationException("boom"))

        assertTrue(ch.isClosed())
        assertTrue(ch.isClosedForSend)
        assertTrue(ch.isClosedForReceive)
    }

    @Test
    fun `start after close reopens channel - isClosed becomes false`() = runTest {
        val ch = StreamRestartableChannel<Int>()
        ch.close(null)
        assertTrue(ch.isClosed())

        ch.start()
        assertFalse(ch.isClosed())
        assertFalse(ch.isClosedForSend)
        assertFalse(ch.isClosedForReceive)

        // Channel works again
        ch.send(42)
        assertEquals(42, ch.receive())
    }

    @Test
    fun `start while already open is a no-op - isClosed stays false`() = runTest {
        val ch = StreamRestartableChannel<Int>()
        assertFalse(ch.isClosed())
        ch.start()
        assertFalse(ch.isClosed())

        ch.send(7)
        assertEquals(7, ch.receive())
        assertFalse(ch.isClosed())
    }

    @Test
    fun `submit does not autostart when autoStart=false, then succeeds after explicit start`() =
        runTest {
            val queue =
                StreamSerialProcessingQueueImpl(
                    logger = mockk<StreamLogger>(relaxed = true),
                    scope = backgroundScope,
                    autoStart = false, // <- target branch
                    startMode = CoroutineStart.DEFAULT,
                    capacity = Channel.RENDEZVOUS,
                )

            // worker should be null before explicit start
            val workerField =
                queue.javaClass.getDeclaredField("worker").apply { isAccessible = true }
            assertNull(workerField.get(queue))

            // Submitting will suspend (no worker to receive from rendezvous inbox)
            val res = async { queue.submit { 42 } }

            assertTrue(res.isActive)
            advanceTimeBy(100)

            queue.start()
            assertEquals(42, res.await().getOrThrow())
        }
}
