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
package io.getstream.android.core.internal.processing

import java.lang.reflect.Field
import kotlin.test.DefaultAsserter.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamBatcherImplTest {

    @Test
    fun `collects burst into one batch`() = runTest {
        // given a debounce processor with tiny window so test runs fast
        val processor =
            StreamBatcherImpl<Int>(
                scope = backgroundScope,
                batchSize = 10, // big enough so size threshold won’t trigger
                initialDelayMs = 100, // 100-ms debounce window
                maxDelayMs = 1_000,
            )

        val batches = mutableListOf<List<Int>>()
        val windows = mutableListOf<Long>()
        val sizes = mutableListOf<Int>()

        processor.onBatch { batch, win, sz ->
            batches += batch
            windows += win
            sizes += sz
        }

        // when -- enqueue 3 items “quickly” (< window)
        processor.enqueue(1)
        processor.enqueue(2)
        processor.enqueue(3)

        // advance virtual time far beyond the 100-ms window so emission happens
        advanceTimeBy(1000)

        // then -- exactly one batch emitted, in order, with correct meta
        assertEquals(1, batches.size)
        assertEquals(listOf(1, 2, 3), batches.first())
        assertEquals(listOf(100L), windows) // windowAppliedMs == initialDelayMs
        assertEquals(listOf(3), sizes) // emittedCount == 3
    }

    @Test
    fun `lazy start launches worker only once despite concurrent enqueue`() = runTest {
        // Use an explicit parent scope so we can later inspect its children
        val parent = TestScope(StandardTestDispatcher())

        val processor =
            StreamBatcherImpl<Int>(
                    scope = parent,
                    batchSize = 10,
                    initialDelayMs = 100,
                    maxDelayMs = 1_000,
                )
                .apply {
                    // no-op handler, we only care about worker creation
                    onBatch { _, _, _ -> }
                }

        val n = 20
        // Concurrently enqueue `n` items
        CoroutineScope(Dispatchers.Default)
            .launch { repeat(n) { i -> launch { processor.enqueue(i) } } }
            .join() // wait for all enqueues to finish

        // Run the scheduler so the processor can start its worker
        parent.testScheduler.runCurrent()

        // verify exactly ONE child coroutine (the worker) is alive ────
        val children = parent.coroutineContext[Job]!!.children.filter { it.isActive }

        assertEquals("Only the debounce-worker should remain active", 1, children.count())
    }

    @Test
    fun `batch emitted on size threshold and next window is doubled`() = runTest {
        val processor =
            StreamBatcherImpl<Int>(
                scope = backgroundScope,
                batchSize = 3, // small threshold so we can hit it easily
                initialDelayMs = 100,
                maxDelayMs = 1_000,
            )

        val windows = mutableListOf<Long>()
        val batches = mutableListOf<List<Int>>()

        processor.onBatch { batch, win, _ ->
            batches += batch
            windows += win
        }

        // Fill the first batch fast (hit size threshold)
        processor.enqueue(1)
        processor.enqueue(2)
        processor.enqueue(3) // reaches batchSize == 3

        // No need to advance time: the worker will emit immediately after
        // collecting the 3rd item and before hitting its 100-ms window.
        advanceTimeBy(1) // let worker run one cycle

        // Start the second batch; we won't fill it yet
        processor.enqueue(4)

        // Advance **just** past the *doubled* window (200 ms) so the
        // second batch is emitted due to the timer, not size.
        advanceTimeBy(400)

        assertEquals(listOf(listOf(1, 2, 3), listOf(4)), batches)
        assertEquals(listOf(100L, 200L), windows)
    }

    @Test
    fun `window doubles once after full batch then resets`() = runTest {
        val processor =
            StreamBatcherImpl<Int>(
                scope = backgroundScope,
                batchSize = 3,
                initialDelayMs = 100,
                maxDelayMs = 1_000,
                channelCapacity = 16,
            )

        val batches = mutableListOf<List<Int>>()
        val windows = mutableListOf<Long>()

        processor.onBatch { batch, win, _ ->
            batches += batch
            windows += win
        }

        processor.enqueue(1)
        processor.enqueue(2)
        processor.enqueue(3)

        advanceTimeBy(1) // let the worker finish emission

        /* ---------- 2. SECOND (NOT-FULL) BATCH ---------- */
        processor.enqueue(4) // start collecting next batch

        // Need to pass the doubled window (200 ms) PLUS the cool-off delay (200 ms)
        //   1) first   200-ms “cool-off” delay after the full batch
        //   2) then up to 200-ms collection window for the new batch
        advanceTimeBy(400) // 200 + 200

        /* ---------- 3. THIRD BATCH (WINDOW BACK TO 100 ms) ---------- */
        processor.enqueue(5) // starts after reset to 100 ms

        // Again: 100-ms cool-off  + 100-ms collection window
        advanceTimeBy(250)

        /* ---------- Assertions ---------- */
        assertEquals(
            listOf(
                listOf(1, 2, 3), // full
                listOf(4), // not full
                listOf(5),
            ), // not full
            batches,
        )
        assertEquals(
            listOf(100L, 200L, 100L), // 100 → 200 → back to 100
            windows,
        )
    }

    @Test
    fun `stop() cancels the worker, drains pending items, and rejects new enqueues`() = runTest {
        val processor =
            StreamBatcherImpl<Int>(
                scope = backgroundScope,
                batchSize = 5, // anything
                initialDelayMs = 100,
                maxDelayMs = 1_000,
                autoStart = false,
            )

        processor.start()

        val delivered = mutableListOf<List<Int>>()
        processor.onBatch { batch, _, _ -> delivered += batch }

        processor.enqueue(1)
        processor.enqueue(2)

        advanceTimeBy(150)
        val stopResult = processor.stop()

        assertTrue(stopResult.isSuccess)

        // The two items that were already in the inbox must be surfaced exactly once.
        assertEquals(listOf(listOf(1, 2)), delivered)

        val res = processor.enqueue(42)
        assertTrue(res.isFailure)
    }

    @Test
    fun `stop() finishes gracefully if worker is already null`() = runTest {
        val processor =
            StreamBatcherImpl<Int>(
                scope = backgroundScope,
                batchSize = 5, // anything
                initialDelayMs = 100,
                maxDelayMs = 1_000,
            )

        val workerField =
            processor.javaClass.getDeclaredField("worker").apply { isAccessible = true }

        // At least one enqueue to start the worker
        processor.enqueue(1)

        advanceTimeBy(150)

        workerField.set(processor, null)
        val stopResult = processor.stop()

        assertTrue(stopResult.isSuccess)
    }

    @Test
    fun `worker sees isActive == false before first iteration`() = runTest {
        val parent = Job()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(parent + dispatcher)

        val processor =
            StreamBatcherImpl<Int>(
                scope = scope,
                batchSize = 3,
                initialDelayMs = 100,
                maxDelayMs = 1_000,
            )
        processor.onBatch { _, _, _ -> }

        parent.cancel()

        processor.enqueue(1)

        // Let the worker start & exit.
        advanceUntilIdle()

        // Channel is closed; enqueue now fails
        val res = processor.enqueue(2)
        assertTrue(res.isFailure)
    }

    @Test
    fun `offer backpressures when autoStart=false and capacity is 1`() = runTest {
        val processor =
            StreamBatcherImpl<Int>(
                scope = backgroundScope,
                batchSize = 5,
                initialDelayMs = 100,
                maxDelayMs = 1_000,
                autoStart = false,
                channelCapacity = 1,
            )

        val batches = mutableListOf<List<Int>>()
        processor.onBatch { batch, _, _ -> batches += batch }

        // No worker running; capacity=1 allows first item only
        assertTrue(processor.offer(1))
        assertFalse("buffer full without worker", processor.offer(2))

        // No worker => nothing emitted
        advanceUntilIdle()
        assertTrue(batches.isEmpty())
    }

    @Test
    fun `offer with cancelled scope buffers on UNLIMITED but never emits`() = runTest {
        // Independent scope we can cancel
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)

        val processor =
            StreamBatcherImpl<Int>(
                scope = scope,
                batchSize = 4,
                initialDelayMs = 50,
                maxDelayMs = 1_000,
                autoStart = true,
                channelCapacity = Channel.UNLIMITED,
            )

        val batches = mutableListOf<List<Int>>()
        processor.onBatch { batch, _, _ -> batches += batch }

        // Cancel scope before offering
        scope.cancel()

        // trySend to UNLIMITED succeeds even if worker cannot run
        assertTrue(processor.offer(99))

        // No emissions since worker can't start on a cancelled scope
        advanceTimeBy(5_000)
        advanceUntilIdle()
        assertTrue(batches.isEmpty())
    }

    private fun testScope() = CoroutineScope(SupervisorJob() + StandardTestDispatcher())

    @Test
    fun `offer - locked false branch (tryLock fails) does NOT start worker`() = runTest {
        val scope = testScope()
        val proc =
            StreamBatcherImpl<Int>(
                scope = scope,
                batchSize = 10,
                initialDelayMs = 100,
                maxDelayMs = 1_000,
                autoStart = true,
                channelCapacity = Channel.UNLIMITED,
            )
        proc.onBatch { _, _, _ -> }

        // Manually pre-lock the internal mutex with a DIFFERENT owner so tryLock(owner=this) fails.
        val mutex = startMutexOf(proc)
        val foreignOwner = Any()
        mutex.lock(foreignOwner)

        // When tryLock() returns false, the 'locked && worker == null' if-branch is skipped.
        assertTrue(proc.offer(1)) // still buffered because UNLIMITED
        advanceUntilIdle()

        // No worker should have been created on this call
        assertNull("worker must be null when locked=false path is taken", workerOf(proc))

        // Cleanup for the rest of the test
        mutex.unlock(foreignOwner)

        // Subsequent offer (with mutex free) should start the worker normally
        assertTrue(proc.offer(2))
        advanceTimeBy(100) // let debounce window elapse
        advanceUntilIdle()
        assertNotNull(workerOf(proc))

        scope.cancel()
    }

    @Test
    fun `offer - locked true AND worker already non-null (worker==null false branch) does NOT create new worker`() =
        runTest {
            val scope = testScope()
            val proc =
                StreamBatcherImpl<Int>(
                    scope = scope,
                    batchSize = 3,
                    initialDelayMs = 50,
                    maxDelayMs = 1_000,
                    autoStart = true,
                    channelCapacity = Channel.UNLIMITED,
                )
            proc.onBatch { _, _, _ -> }

            // First offer starts the worker (locked=true && worker==null -> true branch)
            assertTrue(proc.offer(10))
            advanceUntilIdle()
            val firstWorker = workerOf(proc)
            assertNotNull(firstWorker)

            // Second offer hits locked=true && worker==null == false  (the missing branch)
            assertTrue(proc.offer(20))
            advanceUntilIdle()
            val secondWorker = workerOf(proc)

            // Ensure we did NOT create another worker; same instance is reused
            assertSame(firstWorker, secondWorker)

            scope.cancel()
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T> workerOf(p: StreamBatcherImpl<T>): Any? {
        val f: Field = StreamBatcherImpl::class.java.getDeclaredField("worker")
        f.isAccessible = true
        return f.get(p)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> startMutexOf(p: StreamBatcherImpl<T>): Mutex {
        val f: Field = StreamBatcherImpl::class.java.getDeclaredField("startMutex")
        f.isAccessible = true
        return f.get(p) as Mutex
    }
}
