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

package io.getstream.android.core.internal.processing

import io.getstream.android.core.api.model.StreamTypedKey.Companion.asStreamTypedKey
import io.getstream.android.core.api.processing.StreamSingleFlightProcessor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executors
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamSingleFlightProcessorImplTest {
    class RecordingMap<K, V>(private val delegate: ConcurrentMap<K, V> = ConcurrentHashMap()) :
        ConcurrentMap<K, V> by delegate {
        val installedNonNull = java.util.concurrent.atomic.AtomicBoolean(false)

        override fun putIfAbsent(key: K, value: V): V? {
            val existing = delegate.putIfAbsent(key, value)
            if (existing != null) installedNonNull.set(true)
            return existing
        }
    }

    // Simple collaborator with a suspend function we can verify
    private interface Worker {
        suspend fun workInt(): Int

        suspend fun workFail(): Int

        suspend fun workSlow(): Int
    }

    @Test
    fun `coalesces same-key calls into one execution`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val singleFlight: StreamSingleFlightProcessor = StreamSingleFlightProcessorImpl(scope)

        val worker = mockk<Worker>()
        coEvery { worker.workInt() } coAnswers
            {
                delay(1_000) // virtual time
                42
            }

        val k = "connect".asStreamTypedKey<Int>()

        val a = async { singleFlight.run(k) { worker.workInt() } }
        val b = async { singleFlight.run(k) { worker.workInt() } }
        val c = async { singleFlight.run(k) { worker.workInt() } }

        // Nothing completes yet until we advance time
        advanceUntilIdle() // runs the delay(1000) virtually

        val ra = a.await()
        val rb = b.await()
        val rc = c.await()

        assertTrue(ra.isSuccess)
        assertTrue(rb.isSuccess)
        assertTrue(rc.isSuccess)
        assertEquals(42, ra.getOrThrow())
        assertEquals(42, rb.getOrThrow())
        assertEquals(42, rc.getOrThrow())

        // Verify the underlying block executed only once
        coVerify(exactly = 1) { worker.workInt() }
    }

    @Test
    fun `different keys do not coalesce`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val singleFlight: StreamSingleFlightProcessor = StreamSingleFlightProcessorImpl(scope)

        val worker = mockk<Worker>()
        coEvery { worker.workInt() } coAnswers
            {
                delay(500)
                7
            }

        val a = async { singleFlight.run("A".asStreamTypedKey()) { worker.workInt() } }
        val b = async { singleFlight.run("B".asStreamTypedKey()) { worker.workInt() } }

        advanceUntilIdle()

        val ra = a.await()
        val rb = b.await()
        assertTrue(ra.isSuccess)
        assertTrue(rb.isSuccess)
        assertEquals(7, ra.getOrThrow())
        assertEquals(7, rb.getOrThrow())

        coVerify(exactly = 2) { worker.workInt() }
    }

    @Test
    fun `failure is shared as Result failure`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val singleFlight: StreamSingleFlightProcessor = StreamSingleFlightProcessorImpl(scope)

        val worker = mockk<Worker>()
        val boom = IllegalStateException("boom")
        coEvery { worker.workFail() } coAnswers
            {
                delay(200)
                throw boom
            }

        val k = "same".asStreamTypedKey<Int>()
        val a = async { singleFlight.run(k) { worker.workFail() } }
        val b = async { singleFlight.run(k) { worker.workFail() } }

        advanceUntilIdle()

        val ra = a.await()
        val rb = b.await()

        assertTrue(ra.isFailure)
        assertTrue(rb.isFailure)
        assertEquals(boom, ra.exceptionOrNull())
        assertEquals(boom, rb.exceptionOrNull())

        coVerify(exactly = 1) { worker.workFail() }
    }

    @Test
    fun `caller cancellation does not cancel shared execution`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val singleFlight: StreamSingleFlightProcessor = StreamSingleFlightProcessorImpl(scope)

        val worker = mockk<Worker>()
        coEvery { worker.workSlow() } coAnswers
            {
                delay(1_000)
                99
            }

        val key = "token".asStreamTypedKey<Int>()

        val winner = async { singleFlight.run(key) { worker.workSlow() } }
        val waiter = async { singleFlight.run(key) { worker.workSlow() } }

        // Cancel the waiter before the shared work completes
        waiter.cancel(CancellationException("test-cancel"))

        // Move time so the shared block completes
        advanceUntilIdle()

        // Winner sees success
        val res = winner.await()
        assertTrue(res.isSuccess)
        assertEquals(99, res.getOrThrow())

        // Awaiting the cancelled caller throws CancellationException
        assertFailsWith<CancellationException> { waiter.await() }

        // Underlying work ran only once
        coVerify(exactly = 1) { worker.workSlow() }
    }

    @Test
    fun `cancel(key) cancels in-flight and joiners see failure`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val singleFlight = StreamSingleFlightProcessorImpl(scope)

        val worker = mockk<Worker>()
        coEvery { worker.workSlow() } coAnswers
            {
                delay(10_000) // never completes in test time unless we cancel
                1
            }

        val key = "k".asStreamTypedKey<Int>()

        val a = async { singleFlight.run(key) { worker.workSlow() } }
        val b = async { singleFlight.run(key) { worker.workSlow() } }

        testScheduler.runCurrent()

        // Trigger cancellation of the shared deferred
        singleFlight.cancel(key)

        // Let cancellations propagate
        advanceUntilIdle()

        val ra = a.await()
        val rb = b.await()
        assertTrue(ra.isFailure)
        assertTrue(rb.isFailure)
        assertTrue(ra.exceptionOrNull() is CancellationException)
        assertTrue(rb.exceptionOrNull() is CancellationException)

        // The block started at most once
        coVerify(atMost = 1) { worker.workSlow() }
    }

    @Test
    fun `cancel(missingKey) returns success for a key that does not exist`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val singleFlight = StreamSingleFlightProcessorImpl(scope)

        // Trigger cancellation
        val res = singleFlight.cancel("missingKey".asStreamTypedKey<Long>())

        assertTrue(res.isSuccess)
    }

    @Test
    fun `clear(true) does cancel running job but allows a new run to start`() = runTest {
        // You can keep StandardTestDispatcher; gates make it deterministic.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val singleFlight = StreamSingleFlightProcessorImpl(scope)

        val worker = mockk<Worker>()

        // Gates to detect that each underlying call actually executed
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        var callIndex = 0

        coEvery { worker.workSlow() } coAnswers
            {
                callIndex++
                if (callIndex == 1) {
                    firstStarted.complete(Unit)
                } else {
                    secondStarted.complete(Unit)
                }
                // Keep the job running a bit so we can interleave clear() and the second call
                delay(1_000)
                5
            }

        val key = "dup".asStreamTypedKey<Int>()

        // 1) Launch first run
        val first = async { singleFlight.run(key) { worker.workSlow() } }

        // 2) Let the body run enough to enter workSlow() and record "started"
        //    runCurrent() executes tasks already queued without advancing virtual time.
        testScheduler.runCurrent()
        firstStarted.await() // ensure the first underlying call really started

        // 3) Clear the map (cancels running job)
        singleFlight.clear()

        // 4) Launch second run for the same key
        val second = async { singleFlight.run(key) { worker.workSlow() } }

        // 5) Ensure the second underlying call also started
        testScheduler.runCurrent()
        secondStarted.await()

        // 6) Let the virtual delays finish and collect results
        advanceUntilIdle()

        val r1 = first.await()
        val r2 = second.await()
        assertTrue(r1.isFailure)
        assertTrue(r2.isSuccess)
        assertTrue(r1.exceptionOrNull() is CancellationException)
        assertEquals(5, r2.getOrThrow())

        // Now we can deterministically assert exactly two invocations
        coVerify(exactly = 2) { worker.workSlow() }
    }

    @Test
    fun `clear(false) does not cancel running job and allows a new run to start`() = runTest {
        // You can keep StandardTestDispatcher; gates make it deterministic.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val singleFlight = StreamSingleFlightProcessorImpl(scope)

        val worker = mockk<Worker>()

        // Gates to detect that each underlying call actually executed
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        var callIndex = 0

        coEvery { worker.workSlow() } coAnswers
            {
                callIndex++
                if (callIndex == 1) {
                    firstStarted.complete(Unit)
                } else {
                    secondStarted.complete(Unit)
                }
                // Keep the job running a bit so we can interleave clear() and the second call
                delay(1_000)
                5
            }

        val key = "dup".asStreamTypedKey<Int>()

        // 1) Launch first run
        val first = async { singleFlight.run(key) { worker.workSlow() } }

        // 2) Let the body run enough to enter workSlow() and record "started"
        //    runCurrent() executes tasks already queued without advancing virtual time.
        testScheduler.runCurrent()
        firstStarted.await() // ensure the first underlying call really started

        // 3) Clear the map (cancels running job)
        singleFlight.clear(false)

        // 4) Launch second run for the same key
        val second = async { singleFlight.run(key) { worker.workSlow() } }

        // 5) Ensure the second underlying call also started
        testScheduler.runCurrent()
        secondStarted.await()

        // 6) Let the virtual delays finish and collect results
        advanceUntilIdle()

        val r1 = first.await()
        val r2 = second.await()
        assertTrue(r1.isSuccess)
        assertTrue(r2.isSuccess)
        assertEquals(5, r1.getOrThrow())
        assertEquals(5, r2.getOrThrow())

        // Now we can deterministically assert exactly two invocations
        coVerify(exactly = 2) { worker.workSlow() }
    }

    @Test
    fun `stop cancels in-flight and prevents new runs`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val singleFlight = StreamSingleFlightProcessorImpl(scope)

        val worker = mockk<Worker>()

        val firstStarted = CompletableDeferred<Unit>()
        coEvery { worker.workSlow() } coAnswers
            {
                firstStarted.complete(Unit) // signal that we entered the block
                delay(10_000) // keep it pending until stop()
                123
            }

        val key = "k".asStreamTypedKey<Int>()

        // Start one in-flight run
        val a = async { singleFlight.run(key) { worker.workSlow() } }

        // Let the run() register and start the deferred, and enter workSlow()
        testScheduler.runCurrent()
        firstStarted.await()

        // Call stop(): cancels in-flight and closes for new runs
        val stopRes1 = singleFlight.stop()
        assertTrue(stopRes1.isSuccess)

        // Await the cancelled result from the in-flight caller
        val ra = a.await()
        assertTrue(ra.isFailure)
        assertTrue(ra.exceptionOrNull() is CancellationException)

        // New run should fail fast with ClosedSendChannelException
        val newRes = singleFlight.run(key) { worker.workSlow() }
        assertTrue(newRes.isFailure)
        assertTrue(newRes.exceptionOrNull() is ClosedSendChannelException)

        // Idempotent stop
        val stopRes2 = singleFlight.stop()
        assertTrue(stopRes2.isSuccess)

        // Underlying work invoked only once (the first one)
        coVerify(exactly = 1) { worker.workSlow() }

        advanceUntilIdle()
    }

    @Test
    fun `run with racing callers where loser joins existing flight`() = runTest {
        val pool = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
        try {
            val map = RecordingMap<Any, Deferred<Result<*>>>()
            val sf =
                StreamSingleFlightProcessorImpl(
                    scope = CoroutineScope(SupervisorJob() + pool),
                    flights = map,
                )
            val key = "k".asStreamTypedKey<Int>()

            repeat(200) { // more attempts to guarantee at least one race
                val gate = java.util.concurrent.CountDownLatch(1)
                val a =
                    async(pool) {
                        gate.await()
                        sf.run(key) {
                            delay(100) // keep the winner running long enough
                            1
                        }
                    }
                val b =
                    async(pool) {
                        gate.await()
                        sf.run(key) {
                            delay(100)
                            1
                        }
                    }
                gate.countDown()
                a.await()
                b.await()
            }

            assertTrue(
                "Expected putIfAbsent to return existing at least once",
                map.installedNonNull.get(),
            )
        } finally {
            pool.close() // or pool.executor.shutdown()
        }
    }

    @Test
    fun `has(key) returns true if there is an in-flight execution for key`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val singleFlight: StreamSingleFlightProcessor = StreamSingleFlightProcessorImpl(scope)

        val worker = mockk<Worker>()
        coEvery { worker.workInt() } coAnswers
            {
                delay(1500)
                7
            }

        val key = "A".asStreamTypedKey<Int>()
        val a = async { singleFlight.run(key) { worker.workInt() } }
        advanceTimeBy(500)

        val hasKey = singleFlight.has(key)
        assertTrue(hasKey)
        advanceUntilIdle()

        val ra = a.await()
        assertTrue(ra.isSuccess)
        assertEquals(7, ra.getOrThrow())

        coVerify(exactly = 1) { worker.workInt() }
    }

    @Test
    fun `has(key) returns false if theflight was over`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val singleFlight: StreamSingleFlightProcessor = StreamSingleFlightProcessorImpl(scope)

        val worker = mockk<Worker>()
        coEvery { worker.workInt() } coAnswers
            {
                delay(1500)
                7
            }

        val key = "A".asStreamTypedKey<Int>()
        val a = async { singleFlight.run(key) { worker.workInt() } }
        advanceTimeBy(2500)

        val hasKey = singleFlight.has(key)
        assertFalse(hasKey)
        advanceUntilIdle()

        val ra = a.await()
        assertTrue(ra.isSuccess)
        assertEquals(7, ra.getOrThrow())

        coVerify(exactly = 1) { worker.workInt() }
    }

    @Test
    fun `has(key) returns false if there is no flight added`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val singleFlight: StreamSingleFlightProcessor = StreamSingleFlightProcessorImpl(scope)

        val key = "A".asStreamTypedKey<Int>()
        val hasKey = singleFlight.has(key)
        advanceUntilIdle()

        assertFalse(hasKey)
    }
}
