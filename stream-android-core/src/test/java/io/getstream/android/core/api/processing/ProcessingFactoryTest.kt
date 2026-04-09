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

package io.getstream.android.core.api.processing

import io.getstream.android.core.api.model.StreamTypedKey.Companion.asStreamTypedKey
import io.getstream.android.core.api.model.retry.StreamRetryPolicy
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smoke tests for the public factory functions in `api/processing/`. These verify that each factory
 * returns a working instance — the detailed behaviour tests live alongside the implementations in
 * `internal/processing/`.
 */
class ProcessingFactoryTest {

    @Test
    fun `StreamSingleFlightProcessor factory returns working instance`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)

        val sf = StreamSingleFlightProcessor(scope)

        val result = sf.run("key".asStreamTypedKey()) { 42 }
        testScheduler.runCurrent()

        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrThrow())
    }

    @Test
    fun `StreamRetryProcessor factory returns working instance`() = runTest {
        val rp = StreamRetryProcessor(logger = mockk(relaxed = true))

        val result = rp.retry(StreamRetryPolicy.fixed(maxRetries = 1)) { "ok" }

        assertTrue(result.isSuccess)
        assertEquals("ok", result.getOrThrow())
    }

    @Test
    fun `StreamSerialProcessingQueue factory returns working instance`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)

        val queue = StreamSerialProcessingQueue(logger = mockk(relaxed = true), scope = scope)
        queue.start()
        testScheduler.runCurrent()

        val result = queue.submit { "done" }
        testScheduler.runCurrent()

        assertTrue(result.isSuccess)
        assertEquals("done", result.getOrThrow())
    }

    @Test
    fun `StreamBatcher factory returns working instance`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val batches = mutableListOf<List<String>>()

        val batcher = StreamBatcher<String>(scope = scope, batchSize = 2, initialDelayMs = 100)
        batcher.onBatch { items, _, _ -> batches.add(items) }
        batcher.start()
        testScheduler.runCurrent()

        batcher.enqueue("a")
        batcher.enqueue("b")
        advanceTimeBy(200)
        testScheduler.runCurrent()

        assertTrue(batches.isNotEmpty())
        assertTrue(batches.first().contains("a"))
    }

    @Test
    fun `StreamDebouncer factory returns working instance`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()

        val debouncer =
            StreamDebouncer<String>(scope = scope, logger = mockk(relaxed = true), delayMs = 100)
        debouncer.onValue { delivered.add(it) }

        debouncer.submit("value")
        advanceTimeBy(101)
        testScheduler.runCurrent()

        assertEquals(listOf("value"), delivered)
    }

    @Test
    fun `StreamThrottler factory returns working instance`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()

        val throttler = StreamThrottler<String>(scope = scope, logger = mockk(relaxed = true))
        throttler.onValue { delivered.add(it) }

        assertTrue(throttler.submit("first"))
        testScheduler.runCurrent()

        assertEquals(listOf("first"), delivered)
    }

    @Test
    fun `StreamThrottler factory with trailing policy returns working instance`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()

        val throttler =
            StreamThrottler<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.trailing(windowMs = 500),
            )
        throttler.onValue { delivered.add(it) }

        throttler.submit("pending")
        testScheduler.runCurrent()
        assertTrue(delivered.isEmpty())

        advanceTimeBy(501)
        testScheduler.runCurrent()
        assertEquals(listOf("pending"), delivered)
    }

    @Test
    fun `StreamThrottler factory with leadingAndTrailing policy returns working instance`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = CoroutineScope(SupervisorJob() + dispatcher)
            val delivered = mutableListOf<String>()

            val throttler =
                StreamThrottler<String>(
                    scope = scope,
                    logger = mockk(relaxed = true),
                    policy = StreamThrottlePolicy.leadingAndTrailing(windowMs = 500),
                )
            throttler.onValue { delivered.add(it) }

            throttler.submit("leading")
            testScheduler.runCurrent()
            throttler.submit("trailing")

            advanceTimeBy(501)
            testScheduler.runCurrent()

            assertEquals(listOf("leading", "trailing"), delivered)
        }
}
