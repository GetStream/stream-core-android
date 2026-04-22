/*
 * Copyright (c) 2014-2026 Stream.io Inc. All rights reserved.
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

import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.processing.StreamAggregatedEvent
import io.getstream.android.core.api.processing.StreamEventAggregationPolicy
import io.getstream.android.core.api.processing.StreamEventAggregator
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamEventAggregatorImplTest {

    /** Simple type extractor: pulls the first token after "type:" prefix. */
    private val typeExtractor: (String) -> String? = { raw ->
        val idx = raw.indexOf("type:")
        if (idx >= 0) raw.substring(idx + 5).trim().substringBefore(' ') else null
    }

    /** Simple deserializer: wraps raw string in a TestEvent. */
    private val deserializer: (String) -> Result<TestEvent> = { raw ->
        if (raw.startsWith("BAD")) Result.failure(IllegalStateException("bad event"))
        else Result.success(TestEvent(raw))
    }

    data class TestEvent(val raw: String)

    private fun testScope() = CoroutineScope(SupervisorJob() + StandardTestDispatcher())

    // ── Factory validation ───────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `factory rejects aggregationThreshold = 0`() {
        StreamEventAggregationPolicy.from<TestEvent>(
            typeExtractor = typeExtractor,
            deserializer = deserializer,
            aggregationThreshold = 0,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `factory rejects negative aggregationThreshold`() {
        StreamEventAggregationPolicy.from<TestEvent>(
            typeExtractor = typeExtractor,
            deserializer = deserializer,
            aggregationThreshold = -1,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `factory rejects maxWindowMs = 0`() {
        StreamEventAggregationPolicy.from<TestEvent>(
            typeExtractor = typeExtractor,
            deserializer = deserializer,
            maxWindowMs = 0,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `factory rejects negative maxWindowMs`() {
        StreamEventAggregationPolicy.from<TestEvent>(
            typeExtractor = typeExtractor,
            deserializer = deserializer,
            maxWindowMs = -100,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `factory rejects dispatchQueueCapacity = 0`() {
        StreamEventAggregationPolicy.from<TestEvent>(
            typeExtractor = typeExtractor,
            deserializer = deserializer,
            dispatchQueueCapacity = 0,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `factory rejects negative dispatchQueueCapacity`() {
        StreamEventAggregationPolicy.from<TestEvent>(
            typeExtractor = typeExtractor,
            deserializer = deserializer,
            dispatchQueueCapacity = -1,
        )
    }

    // ── Behavior tests ───────────────────────────────────────────────────────

    @Test
    fun `low traffic events are dispatched individually`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val received = CopyOnWriteArrayList<Any>()

        val aggregator =
            StreamEventAggregator<TestEvent>(
                scope = scope,
                policy =
                    StreamEventAggregationPolicy.from(
                        typeExtractor = typeExtractor,
                        deserializer = deserializer,
                        aggregationThreshold = 50,
                        maxWindowMs = 200,
                    ),
            )
        aggregator.onEvent { received += it }
        aggregator.start()

        // Send 3 events — well below threshold
        aggregator.offer("type:channel.updated event1")
        aggregator.offer("type:message.new event2")
        aggregator.offer("type:channel.updated event3")

        advanceTimeBy(500)
        advanceUntilIdle()

        // Should receive individual TestEvent instances, not aggregated
        assertEquals(3, received.size)
        assertTrue(received.all { it is TestEvent })
        assertEquals("type:channel.updated event1", (received[0] as TestEvent).raw)
        assertEquals("type:message.new event2", (received[1] as TestEvent).raw)
        assertEquals("type:channel.updated event3", (received[2] as TestEvent).raw)

        aggregator.stop()
    }

    @Test
    fun `spike triggers aggregated delivery grouped by type`() {
        // Use real dispatchers — TestDispatcher doesn't play well with Channel + withTimeoutOrNull
        val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        val received = CopyOnWriteArrayList<Any>()
        val latch = java.util.concurrent.CountDownLatch(1)

        val aggregator =
            StreamEventAggregator<TestEvent>(
                scope = scope,
                policy =
                    StreamEventAggregationPolicy.from(
                        typeExtractor = typeExtractor,
                        deserializer = deserializer,
                        aggregationThreshold = 5, // low threshold for testing
                        maxWindowMs = 500,
                    ),
            )
        aggregator.onEvent { event ->
            received += event
            // Signal once we've received all events (individual or aggregated, total >= 10)
            var count = 0
            for (item in received) {
                when (item) {
                    is StreamAggregatedEvent<*> -> count += item.events.values.sumOf { it.size }
                    is TestEvent -> count++
                }
            }
            if (count >= 10) latch.countDown()
        }
        aggregator.start()
        Thread.sleep(50) // let collector suspend on inbox.receive()

        // Buffer all 10 events into the inbox at once
        repeat(10) { i ->
            val type = if (i % 2 == 0) "channel.updated" else "message.new"
            assertTrue(aggregator.offer("type:$type event$i"))
        }
        Thread.sleep(50) // let collector process

        // Wait for delivery (max 5s)
        assertTrue(
            "Events not delivered in time",
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS),
        )

        // Count total events delivered (individual + aggregated)
        var totalEvents = 0
        val aggregated = mutableListOf<StreamAggregatedEvent<*>>()
        for (item in received) {
            when (item) {
                is StreamAggregatedEvent<*> -> {
                    aggregated += item
                    totalEvents += item.events.values.sumOf { it.size }
                }
                is TestEvent -> totalEvents++
            }
        }

        // All 10 events should be delivered
        assertEquals("All 10 events should be delivered", 10, totalEvents)

        // At least one aggregated event should exist (threshold = 5, we sent 10)
        assertTrue(
            "Expected aggregated event but got ${received.size} individual. " +
                "Types: ${received.map { it::class.simpleName }}",
            aggregated.isNotEmpty(),
        )

        // Both types should appear somewhere across all deliveries
        val allTypes = aggregated.flatMap { it.events.keys }
        assertTrue("channel.updated" in allTypes)
        assertTrue("message.new" in allTypes)

        aggregator.stop()
        scope.cancel()
    }

    @Test
    fun `maxWindow caps collection time`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val received = CopyOnWriteArrayList<Any>()

        val aggregator =
            StreamEventAggregator<TestEvent>(
                scope = scope,
                policy =
                    StreamEventAggregationPolicy.from(
                        typeExtractor = typeExtractor,
                        deserializer = deserializer,
                        aggregationThreshold = 1000, // high threshold — won't be reached
                        maxWindowMs = 100, // short window
                    ),
            )
        aggregator.onEvent { received += it }
        aggregator.start()

        // Send a few events
        aggregator.offer("type:a ev1")
        aggregator.offer("type:a ev2")

        // After maxWindow, events should be delivered even though threshold not reached
        advanceTimeBy(300)
        advanceUntilIdle()

        assertTrue("Events should be delivered after maxWindow", received.isNotEmpty())

        aggregator.stop()
    }

    @Test
    fun `deserialization failures are skipped and logged`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val received = CopyOnWriteArrayList<Any>()

        val aggregator =
            StreamEventAggregator<TestEvent>(
                scope = scope,
                policy =
                    StreamEventAggregationPolicy.from(
                        typeExtractor = typeExtractor,
                        deserializer = deserializer,
                        aggregationThreshold = 50,
                        maxWindowMs = 200,
                    ),
            )
        aggregator.onEvent { received += it }
        aggregator.start()

        aggregator.offer("type:ok good_event")
        aggregator.offer("BAD_EVENT") // will fail deserialization
        aggregator.offer("type:ok another_good")

        advanceTimeBy(500)
        advanceUntilIdle()

        // Only the 2 good events should be delivered
        assertEquals(2, received.size)
        assertTrue(received.all { it is TestEvent })

        aggregator.stop()
    }

    @Test
    fun `stop is idempotent`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

        val aggregator =
            StreamEventAggregator<TestEvent>(
                scope = scope,
                policy =
                    StreamEventAggregationPolicy.from(
                        typeExtractor = typeExtractor,
                        deserializer = deserializer,
                    ),
            )
        aggregator.onEvent {}
        aggregator.start()

        val first = aggregator.stop()
        val second = aggregator.stop()

        assertTrue(first.isSuccess)
        assertTrue(second.isSuccess)
    }

    @Test
    fun `stop then start resumes processing (restartable)`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val received = CopyOnWriteArrayList<Any>()

        val aggregator =
            StreamEventAggregator<TestEvent>(
                scope = scope,
                policy =
                    StreamEventAggregationPolicy.from(
                        typeExtractor = typeExtractor,
                        deserializer = deserializer,
                        aggregationThreshold = 50,
                        maxWindowMs = 200,
                    ),
            )
        aggregator.onEvent { received += it }
        aggregator.start()

        aggregator.offer("type:a first")
        advanceTimeBy(500)
        advanceUntilIdle()
        assertEquals(1, received.size)

        // Stop and restart
        aggregator.stop()
        aggregator.start()

        aggregator.offer("type:a second")
        advanceTimeBy(500)
        advanceUntilIdle()

        // Second event delivered after restart
        assertEquals(2, received.size)
        assertEquals("type:a second", (received[1] as TestEvent).raw)

        aggregator.stop()
    }

    @Test
    fun `all events delivered even when many arrive at once`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val received = CopyOnWriteArrayList<Any>()

        val aggregator =
            StreamEventAggregator<TestEvent>(
                scope = scope,
                policy =
                    StreamEventAggregationPolicy.from(
                        typeExtractor = typeExtractor,
                        deserializer = deserializer,
                        aggregationThreshold = 5,
                        maxWindowMs = 100,
                    ),
            )
        aggregator.onEvent { event -> received += event }
        aggregator.start()

        // Pump 11 events rapidly
        aggregator.offer("type:a first")
        repeat(10) { i -> aggregator.offer("type:b spike$i") }

        // Let everything settle
        advanceTimeBy(2000)
        advanceUntilIdle()

        // Count total events delivered (individual + aggregated)
        var totalEvents = 0
        for (item in received) {
            when (item) {
                is StreamAggregatedEvent<*> -> totalEvents += item.events.values.sumOf { it.size }
                is TestEvent -> totalEvents++
            }
        }
        assertEquals("All 11 events should be delivered", 11, totalEvents)

        aggregator.stop()
    }

    @Test
    fun `null type from extractor uses empty string key`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val received = CopyOnWriteArrayList<Any>()

        val aggregator =
            StreamEventAggregator<TestEvent>(
                scope = scope,
                policy =
                    StreamEventAggregationPolicy.from(
                        typeExtractor = { null }, // always returns null
                        deserializer = deserializer,
                        aggregationThreshold = 3,
                        maxWindowMs = 200,
                    ),
            )
        aggregator.onEvent { received += it }
        aggregator.start()

        repeat(5) { aggregator.offer("event$it") }

        advanceTimeBy(500)
        advanceUntilIdle()

        val aggregated = received.filterIsInstance<StreamAggregatedEvent<*>>()
        assertTrue(aggregated.isNotEmpty())
        // All events grouped under empty key
        assertTrue(aggregated.first().events.containsKey(""))

        aggregator.stop()
    }

    // ── Edge cases: error resilience ─────────────────────────────────────────

    @Test
    fun `typeExtractor that throws does not kill collector`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val received = CopyOnWriteArrayList<Any>()
        var callCount = 0

        val throwingExtractor: (String) -> String? = {
            callCount++
            if (callCount == 2) throw RuntimeException("extractor boom")
            "safe"
        }

        val aggregator =
            StreamEventAggregator<TestEvent>(
                scope = scope,
                policy =
                    StreamEventAggregationPolicy.from(
                        typeExtractor = throwingExtractor,
                        deserializer = deserializer,
                        aggregationThreshold = 3,
                        maxWindowMs = 200,
                    ),
            )
        aggregator.onEvent { received += it }
        aggregator.start()

        // Send enough to trigger aggregation — extractor throws on 2nd call
        repeat(5) { aggregator.offer("type:a event$it") }

        advanceTimeBy(500)
        advanceUntilIdle()

        // All 5 events should still be delivered (throwing one grouped under "")
        var total = 0
        for (item in received) {
            when (item) {
                is StreamAggregatedEvent<*> -> total += item.events.values.sumOf { it.size }
                is TestEvent -> total++
            }
        }
        assertEquals("All 5 events should be delivered despite extractor throw", 5, total)

        aggregator.stop()
    }

    @Test
    fun `deserializer that throws (not Result failure) does not kill collector`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val received = CopyOnWriteArrayList<Any>()

        val throwingDeserializer: (String) -> Result<TestEvent> = { raw ->
            if (raw.contains("THROW")) throw RuntimeException("deserializer exploded")
            Result.success(TestEvent(raw))
        }

        val aggregator =
            StreamEventAggregator<TestEvent>(
                scope = scope,
                policy =
                    StreamEventAggregationPolicy.from(
                        typeExtractor = typeExtractor,
                        deserializer = throwingDeserializer,
                        aggregationThreshold = 50,
                        maxWindowMs = 200,
                    ),
            )
        aggregator.onEvent { received += it }
        aggregator.start()

        aggregator.offer("type:a good1")
        aggregator.offer("type:a THROW") // throws, not Result.failure
        aggregator.offer("type:a good2")

        advanceTimeBy(500)
        advanceUntilIdle()

        // 2 good events delivered, throwing one skipped
        assertEquals(2, received.size)
        assertTrue(received.all { it is TestEvent })

        // Verify collector still alive — send more events
        aggregator.offer("type:a good3")
        advanceTimeBy(500)
        advanceUntilIdle()

        assertEquals(3, received.size)

        aggregator.stop()
    }

    @Test
    fun `handler exception does not break subsequent event delivery`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val received = CopyOnWriteArrayList<Any>()

        val aggregator =
            StreamEventAggregator<TestEvent>(
                scope = scope,
                policy =
                    StreamEventAggregationPolicy.from(
                        typeExtractor = typeExtractor,
                        deserializer = deserializer,
                        aggregationThreshold = 50,
                        maxWindowMs = 100,
                    ),
            )
        aggregator.onEvent { event ->
            if (event is TestEvent && event.raw.contains("EXPLODE")) {
                throw RuntimeException("handler boom")
            }
            received += event
        }
        aggregator.start()

        aggregator.offer("type:a good1")
        advanceTimeBy(200)
        advanceUntilIdle()

        aggregator.offer("type:a EXPLODE") // handler throws
        advanceTimeBy(200)
        advanceUntilIdle()

        aggregator.offer("type:a good2") // should still be delivered
        advanceTimeBy(200)
        advanceUntilIdle()

        assertEquals(2, received.size)
        assertEquals("type:a good1", (received[0] as TestEvent).raw)
        assertEquals("type:a good2", (received[1] as TestEvent).raw)

        aggregator.stop()
    }

    // ── Edge cases: boundary conditions ──────────────────────────────────────

    @Test
    fun `exactly threshold events triggers aggregation not individual`() {
        val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        val received = CopyOnWriteArrayList<Any>()
        val latch = java.util.concurrent.CountDownLatch(1)

        val aggregator =
            StreamEventAggregator<TestEvent>(
                scope = scope,
                policy =
                    StreamEventAggregationPolicy.from(
                        typeExtractor = typeExtractor,
                        deserializer = deserializer,
                        aggregationThreshold = 5,
                        maxWindowMs = 500,
                    ),
            )
        aggregator.onEvent { event ->
            received += event
            var count = 0
            for (item in received) {
                when (item) {
                    is StreamAggregatedEvent<*> -> count += item.events.values.sumOf { it.size }
                    is TestEvent -> count++
                }
            }
            if (count >= 5) latch.countDown()
        }
        aggregator.start()
        Thread.sleep(50)

        // Exactly 5 events = threshold
        repeat(5) { aggregator.offer("type:a event$it") }

        assertTrue(
            "Events not delivered in time",
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS),
        )

        // With exactly threshold items, buffer.size == threshold, condition is
        // `rawMessages.size < aggregationThreshold` = false → aggregated path
        val aggregated = received.filterIsInstance<StreamAggregatedEvent<*>>()
        assertTrue(
            "Exactly threshold should trigger aggregation, got: ${received.map { it::class.simpleName }}",
            aggregated.isNotEmpty(),
        )

        aggregator.stop()
        scope.cancel()
    }

    @Test
    fun `all events fail deserialization - batch silently dropped`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val received = CopyOnWriteArrayList<Any>()

        val aggregator =
            StreamEventAggregator<TestEvent>(
                scope = scope,
                policy =
                    StreamEventAggregationPolicy.from(
                        typeExtractor = typeExtractor,
                        deserializer = { Result.failure(IllegalStateException("always fails")) },
                        aggregationThreshold = 50,
                        maxWindowMs = 200,
                    ),
            )
        aggregator.onEvent { received += it }
        aggregator.start()

        repeat(5) { aggregator.offer("type:a event$it") }

        advanceTimeBy(500)
        advanceUntilIdle()

        // Nothing delivered — all failed
        assertTrue("No events should be delivered", received.isEmpty())

        // Collector should still be alive — send a good event via a new aggregator
        // (can't change deserializer on existing one, so just verify no crash)
        aggregator.stop()
    }

    @Test
    fun `dispatch queue full logs warning and drops events`() {
        val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        val received = CopyOnWriteArrayList<Any>()
        val latch = java.util.concurrent.CountDownLatch(1)

        val warnings = CopyOnWriteArrayList<String>()
        val testLogger =
            object : StreamLogger {
                override fun log(
                    level: StreamLogger.LogLevel,
                    throwable: Throwable?,
                    message: () -> String,
                ) {
                    if (level is StreamLogger.LogLevel.Warning) {
                        warnings.add(message())
                    }
                }
            }

        val aggregator =
            StreamEventAggregator<TestEvent>(
                scope = scope,
                policy =
                    StreamEventAggregationPolicy.from(
                        typeExtractor = typeExtractor,
                        deserializer = deserializer,
                        aggregationThreshold = 50,
                        maxWindowMs = 50,
                        dispatchQueueCapacity = 1, // tiny queue
                    ),
            )
        (aggregator as StreamEventAggregatorImpl<*>).logger = testLogger
        aggregator.onEvent { event ->
            // Slow handler — holds dispatch queue slot
            Thread.sleep(200)
            received += event
            latch.countDown()
        }
        aggregator.start()
        Thread.sleep(50)

        // Flood events in rapid bursts — dispatcher is slow, queue will fill
        repeat(5) { burst ->
            repeat(3) { aggregator.offer("type:a burst${burst}_event$it") }
            Thread.sleep(100) // let maxWindow fire between bursts
        }

        // Wait for at least one delivery
        assertTrue(
            "At least one event should be delivered",
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS),
        )

        // Some events were delivered, some may have been dropped (queue full)
        // The key assertion: no crash, aggregator survived
        assertTrue("At least one event delivered", received.isNotEmpty())

        // Verify that a warning was logged when the dispatch queue was full
        assertTrue("Expected warning about full dispatch queue", warnings.isNotEmpty())

        aggregator.stop()
        scope.cancel()
    }

    // ── Stress test ──────────────────────────────────────────────────────────

    @Test
    fun `stress - 10K events with realistic type distribution`() {
        val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        val totalEvents = 10_000
        val received = CopyOnWriteArrayList<Any>()
        val latch = java.util.concurrent.CountDownLatch(1)
        val eventTypes =
            listOf(
                "channel.updated",
                "message.new",
                "user.watching.start",
                "user.presence_changed",
                "typing.start",
            )

        val threshold = 50
        val aggregator =
            StreamEventAggregator<TestEvent>(
                scope = scope,
                policy =
                    StreamEventAggregationPolicy.from(
                        typeExtractor = typeExtractor,
                        deserializer = deserializer,
                        aggregationThreshold = threshold,
                        maxWindowMs = 500,
                    ),
            )

        val deliveredCount = java.util.concurrent.atomic.AtomicInteger(0)
        aggregator.onEvent { event ->
            received += event
            val count =
                when (event) {
                    is StreamAggregatedEvent<*> -> event.events.values.sumOf { it.size }
                    is TestEvent -> 1
                    else -> 0
                }
            if (deliveredCount.addAndGet(count) >= totalEvents) {
                latch.countDown()
            }
        }

        val startNs = System.nanoTime()
        aggregator.start()
        Thread.sleep(20) // let collector suspend on receive

        // Flood 10K events with realistic type distribution
        repeat(totalEvents) { i ->
            val type = eventTypes[i % eventTypes.size]
            aggregator.offer("type:$type event$i")
        }

        assertTrue(
            "10K events not delivered in time",
            latch.await(30, java.util.concurrent.TimeUnit.SECONDS),
        )
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L

        // Count batches and individual events
        var individualCount = 0
        var aggregatedBatches = 0
        var aggregatedEventCount = 0
        for (item in received) {
            when (item) {
                is StreamAggregatedEvent<*> -> {
                    aggregatedBatches++
                    aggregatedEventCount += item.events.values.sumOf { it.size }
                }
                is TestEvent -> individualCount++
            }
        }
        val totalDelivered = individualCount + aggregatedEventCount

        // Print results for visibility
        println("=== 10K Event Stress Test Results ===")
        println("Total events offered:    $totalEvents")
        println("Total events delivered:  $totalDelivered")
        println("Individual dispatches:   $individualCount")
        println("Aggregated batches:      $aggregatedBatches")
        println("Events in aggregated:    $aggregatedEventCount")
        println("Total handler calls:     ${received.size}")
        println("Elapsed time:            ${elapsedMs}ms")
        println("====================================")

        // Guarantee 1: all events delivered
        assertEquals("All events must be delivered", totalEvents, totalDelivered)

        // Guarantee 2: each aggregated batch has at most `threshold` events
        for ((i, item) in received.withIndex()) {
            if (item is StreamAggregatedEvent<*>) {
                val batchSize = item.events.values.sumOf { it.size }
                assertTrue(
                    "Batch $i has $batchSize events, exceeds threshold $threshold",
                    batchSize <= threshold,
                )
            }
        }

        // Guarantee 3: fewer handler calls than raw events (aggregation happened)
        assertTrue(
            "Expected fewer handler calls (${received.size}) than raw events ($totalEvents)",
            received.size < totalEvents,
        )

        aggregator.stop()
        scope.cancel()
    }
}
