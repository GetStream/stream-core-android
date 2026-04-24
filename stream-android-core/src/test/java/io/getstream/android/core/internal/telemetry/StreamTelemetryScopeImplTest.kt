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

package io.getstream.android.core.internal.telemetry

import io.getstream.android.core.api.model.telemetry.StreamSignal
import io.getstream.android.core.api.telemetry.StreamSignalRedactor
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@Suppress("LargeClass")
class StreamTelemetryScopeImplTest {

    @get:Rule val tempDir = TemporaryFolder()

    private lateinit var spillDir: File
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        spillDir = tempDir.newFolder("spill")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun createScope(
        name: String = "test",
        memoryCapacity: Int = 500,
        diskCapacity: Long = 1_000_000L,
        redactor: StreamSignalRedactor? = null,
        dir: File = spillDir,
        cs: CoroutineScope = scope,
    ): StreamTelemetryScopeImpl =
        StreamTelemetryScopeImpl(
            name = name,
            memoryCapacity = memoryCapacity,
            diskCapacity = diskCapacity,
            spillDir = dir,
            redactor = redactor,
            scope = cs,
        )

    private fun waitForSpill() = Thread.sleep(500)

    // ========================================
    // Basic emit + drain
    // ========================================

    @Test
    fun `emit single signal and drain returns it`() = runBlocking {
        val sut = createScope()
        sut.emit("connected", "user-123")

        val result = sut.drain()
        assertTrue(result.isSuccess)
        val signals = result.getOrThrow()
        assertEquals(1, signals.size)
        assertEquals("connected", signals[0].tag)
        assertEquals("user-123", signals[0].data)
    }

    @Test
    fun `emit multiple signals preserves order`() = runBlocking {
        val sut = createScope()
        sut.emit("first", null)
        sut.emit("second", null)
        sut.emit("third", null)

        val signals = sut.drain().getOrThrow()
        assertEquals(3, signals.size)
        assertEquals("first", signals[0].tag)
        assertEquals("second", signals[1].tag)
        assertEquals("third", signals[2].tag)
    }

    @Test
    fun `emit with null data`() = runBlocking {
        val sut = createScope()
        sut.emit("event", null)

        val signals = sut.drain().getOrThrow()
        assertEquals(1, signals.size)
        assertEquals(null, signals[0].data)
    }

    @Test
    fun `emit with complex data`() = runBlocking {
        val sut = createScope()
        val data = mapOf("key" to "value", "count" to 42)
        sut.emit("event", data)

        val signals = sut.drain().getOrThrow()
        assertEquals(1, signals.size)
        assertEquals(data, signals[0].data)
    }

    @Test
    fun `signal has non-zero timestamp`() = runBlocking {
        val before = System.currentTimeMillis()
        val sut = createScope()
        sut.emit("event", null)
        val after = System.currentTimeMillis()

        val signal = sut.drain().getOrThrow().single()
        assertTrue(signal.timestamp >= before)
        assertTrue(signal.timestamp <= after)
    }

    // ========================================
    // Drain semantics
    // ========================================

    @Test
    fun `drain on empty scope returns success with empty list`() = runBlocking {
        val sut = createScope()

        val result = sut.drain()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `drain clears the buffer`() = runBlocking {
        val sut = createScope()
        sut.emit("first", null)
        sut.drain()

        val result = sut.drain()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `drain twice without emit returns empty second time`() = runBlocking {
        val sut = createScope()
        sut.emit("event", null)

        val first = sut.drain().getOrThrow()
        assertEquals(1, first.size)

        val second = sut.drain().getOrThrow()
        assertTrue(second.isEmpty())
    }

    @Test
    fun `emit after drain goes into new buffer`() = runBlocking {
        val sut = createScope()
        sut.emit("before-drain", null)
        sut.drain()

        sut.emit("after-drain", null)
        val signals = sut.drain().getOrThrow()
        assertEquals(1, signals.size)
        assertEquals("after-drain", signals[0].tag)
    }

    // ========================================
    // Scope name
    // ========================================

    @Test
    fun `name is set correctly`() {
        val sut = createScope(name = "connection")
        assertEquals("connection", sut.name)
    }

    // ========================================
    // Redactor
    // ========================================

    @Test
    fun `redactor transforms signal data`() = runBlocking {
        val redactor = StreamSignalRedactor { signal ->
            if (signal.tag == "auth.token") {
                signal.copy(data = "[REDACTED]")
            } else {
                signal
            }
        }
        val sut = createScope(redactor = redactor)

        sut.emit("auth.token", "secret-jwt-token")
        sut.emit("connected", "user-123")

        val signals = sut.drain().getOrThrow()
        assertEquals(2, signals.size)
        assertEquals("[REDACTED]", signals[0].data)
        assertEquals("user-123", signals[1].data)
    }

    @Test
    fun `redactor returning null falls back to raw signal`() = runBlocking {
        // val signal = redactor?.redact(raw) ?: raw — null falls back to raw
        val redactor = StreamSignalRedactor { null }
        val sut = createScope(redactor = redactor)

        sut.emit("event", "data")

        val signals = sut.drain().getOrThrow()
        assertEquals(1, signals.size)
        assertEquals("event", signals[0].tag)
    }

    @Test
    fun `redactor can modify tag`() = runBlocking {
        val redactor = StreamSignalRedactor { signal -> signal.copy(tag = "prefix.${signal.tag}") }
        val sut = createScope(redactor = redactor)
        sut.emit("event", null)

        val signals = sut.drain().getOrThrow()
        assertEquals("prefix.event", signals[0].tag)
    }

    @Test
    fun `redactor exception does not crash emit`() {
        val redactor = StreamSignalRedactor { throw RuntimeException("redactor boom") }
        val sut = createScope(redactor = redactor)

        sut.emit("event", null)
        // No exception thrown
    }

    @Test
    fun `no redactor leaves signal unchanged`() = runBlocking {
        val sut = createScope(redactor = null)
        sut.emit("event", "data")

        val signal = sut.drain().getOrThrow().single()
        assertEquals("event", signal.tag)
        assertEquals("data", signal.data)
    }

    // ========================================
    // Memory capacity + spill
    // ========================================

    @Test
    fun `signals within memory capacity stay in memory only`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 10, dir = dir)
        repeat(10) { sut.emit("event-$it", null) }

        waitForSpill()
        val spillFile = File(dir, "spill.bin")
        assertTrue(!spillFile.exists() || spillFile.length() == 0L)

        val signals = sut.drain().getOrThrow()
        assertEquals(10, signals.size)
    }

    @Test
    fun `exceeding memory capacity triggers spill`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 5, dir = dir)
        repeat(6) { sut.emit("event-$it", null) }

        waitForSpill()

        val signals = sut.drain().getOrThrow()
        assertTrue(signals.isNotEmpty())
    }

    @Test
    fun `spill preserves FIFO order - disk signals come before memory`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 3, dir = dir)

        sut.emit("a", null)
        sut.emit("b", null)
        sut.emit("c", null)
        sut.emit("d", null)

        waitForSpill()

        val signals = sut.drain().getOrThrow()
        val tags = signals.map { it.tag }
        assertTrue(
            "Expected disk signals before memory, got: $tags",
            tags.indexOf("a") < tags.indexOf("d"),
        )
    }

    @Test
    fun `multiple spills accumulate on disk`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 2, dir = dir)

        repeat(3) { sut.emit("s$it", null) }
        waitForSpill()
        repeat(2) { sut.emit("s${it + 3}", null) }
        waitForSpill()

        val signals = sut.drain().getOrThrow()
        assertTrue("Expected at least 4 signals, got ${signals.size}", signals.size >= 4)
    }

    // ========================================
    // Disk capacity + rotation
    // ========================================

    @Test
    fun `disk rotation drops oldest when capacity exceeded`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 2, diskCapacity = 100, dir = dir)

        repeat(20) { sut.emit("event-$it", "some-payload-data") }
        waitForSpill()

        val spillFile = File(dir, "spill.bin")
        if (spillFile.exists()) {
            assertTrue(
                "Spill file should be around disk capacity, was ${spillFile.length()}",
                spillFile.length() <= 150,
            )
        }
    }

    // ========================================
    // Drop oldest under pressure
    // ========================================

    @Test
    fun `when spill in progress, oldest signal is dropped from buffer`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 2, dir = dir)

        repeat(100) { sut.emit("event-$it", null) }
        waitForSpill()

        val signals = sut.drain().getOrThrow()
        assertTrue("Expected some signals, got ${signals.size}", signals.isNotEmpty())
        assertTrue("Expected some drops, got ${signals.size}", signals.size <= 100)
    }

    // ========================================
    // Thread safety
    // ========================================

    @Test
    fun `concurrent emit from multiple threads does not crash`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 50, dir = dir)
        val threads = 10
        val emitsPerThread = 100
        val barrier = CyclicBarrier(threads)
        val latch = CountDownLatch(threads)

        repeat(threads) { threadIdx ->
            Thread {
                    barrier.await()
                    repeat(emitsPerThread) { i -> sut.emit("thread-$threadIdx-event-$i", null) }
                    latch.countDown()
                }
                .start()
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        waitForSpill()

        val signals = sut.drain().getOrThrow()
        assertTrue(
            "Expected signals from concurrent emit, got ${signals.size}",
            signals.isNotEmpty(),
        )
    }

    @Test
    fun `concurrent emit and drain does not crash`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 10, dir = dir)
        val emitting = AtomicInteger(1)
        val drainResults = mutableListOf<Result<List<StreamSignal>>>()

        val emitJob =
            scope.launch {
                var i = 0
                while (emitting.get() == 1) {
                    sut.emit("event-${i++}", null)
                    delay(1)
                }
            }

        repeat(20) {
            Thread.sleep(10)
            drainResults.add(sut.drain())
        }

        emitting.set(0)
        emitJob.cancel()

        drainResults.forEach { result ->
            assertTrue("drain() should not fail during concurrent access", result.isSuccess)
        }
    }

    // ========================================
    // Disk Mutex — concurrent spill + drain
    // ========================================

    @Test
    fun `drain while spill is in flight returns consistent data`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 3, dir = dir)

        // Emit enough to trigger spill
        repeat(4) { sut.emit("event-$it", null) }

        // Immediately drain — spill may or may not have finished
        // With the disk Mutex, drain waits for spill to complete before reading
        Thread.sleep(100)
        val result = sut.drain()

        assertTrue("drain should succeed", result.isSuccess)
        val signals = result.getOrThrow()
        // All 4 signals should be accounted for (either from disk or memory)
        assertEquals(4, signals.size)
    }

    @Test
    fun `rapid spill cycles with tiny memory capacity`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 1, dir = dir)

        // Rapid-fire 200 emits — each pair triggers a spill
        repeat(200) { sut.emit("event-$it", null) }
        waitForSpill()

        val result = sut.drain()
        assertTrue("drain should succeed after many spill cycles", result.isSuccess)
        val signals = result.getOrThrow()
        assertTrue("Expected signals, got ${signals.size}", signals.isNotEmpty())

        // All signals should have valid structure
        signals.forEach { signal ->
            assertTrue("Tag should start with event-", signal.tag.startsWith("event-"))
            assertTrue("Timestamp should be positive", signal.timestamp > 0)
        }
    }

    @Test
    fun `concurrent drain and emit with disk overflow`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 2, diskCapacity = 200, dir = dir)
        val emitting = AtomicInteger(1)
        val allDrainedSignals = mutableListOf<StreamSignal>()
        val drainFailures = AtomicInteger(0)

        // Emit rapidly in background
        val emitJob =
            scope.launch {
                var i = 0
                while (emitting.get() == 1) {
                    sut.emit("event-${i++}", "payload")
                    delay(1)
                }
            }

        // Drain 30 times concurrently with emit
        repeat(30) {
            Thread.sleep(10)
            val result = sut.drain()
            if (result.isSuccess) {
                synchronized(allDrainedSignals) { allDrainedSignals.addAll(result.getOrThrow()) }
            } else {
                drainFailures.incrementAndGet()
            }
        }

        emitting.set(0)
        emitJob.cancel()
        waitForSpill()

        // Final drain to get remaining signals
        sut.drain().onSuccess { remaining ->
            synchronized(allDrainedSignals) { allDrainedSignals.addAll(remaining) }
        }

        assertTrue("Should have collected some signals", allDrainedSignals.isNotEmpty())
        assertEquals("No drain should have failed", 0, drainFailures.get())

        // No duplicates — each drain clears the buffer
        val uniqueTimestampTagPairs = allDrainedSignals.map { "${it.timestamp}-${it.tag}" }.toSet()
        assertEquals(
            "No duplicate signals expected",
            allDrainedSignals.size,
            uniqueTimestampTagPairs.size,
        )
    }

    @Test
    fun `multiple coroutines draining same scope concurrently`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 5, dir = dir)

        // Pre-fill with signals that will spill
        repeat(20) { sut.emit("event-$it", null) }
        waitForSpill()

        // Launch 10 concurrent drains
        val results = (1..10).map { scope.launch { sut.drain() } }
        results.forEach { it.join() }

        // After all drains, scope should be empty
        val remaining = sut.drain().getOrThrow()
        assertTrue("Scope should be empty after concurrent drains", remaining.isEmpty())
    }

    @Test
    fun `spill and drain interleaved rapidly`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 2, dir = dir)
        val allSignals = mutableListOf<StreamSignal>()

        // 50 cycles of: emit enough to spill, then drain
        repeat(50) { cycle ->
            repeat(3) { sut.emit("cycle-$cycle-event-$it", null) }
            Thread.sleep(50) // let spill run
            sut.drain().onSuccess { signals ->
                synchronized(allSignals) { allSignals.addAll(signals) }
            }
        }

        // Wait for any remaining spills
        waitForSpill()
        sut.drain().onSuccess { signals -> synchronized(allSignals) { allSignals.addAll(signals) } }

        // We emitted 150 signals total (50 * 3)
        // Some may be lost due to drop-oldest under pressure, but most should survive
        assertTrue("Expected most of 150 signals, got ${allSignals.size}", allSignals.size >= 100)
    }

    // ========================================
    // Disk I/O failures
    // ========================================

    @Test
    fun `drain handles corrupt spill file gracefully`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(dir = dir)

        dir.mkdirs()
        File(dir, "spill.bin").writeText("corrupt\u0000garbage\u0000not\u0000valid")

        sut.emit("memory-signal", null)
        val result = sut.drain()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `emit does not throw when spill directory is read-only`() = runBlocking {
        val readOnlyDir = tempDir.newFolder("readonly")
        readOnlyDir.setReadOnly()

        val sut = createScope(memoryCapacity = 2, dir = File(readOnlyDir, "nested"))

        repeat(5) { sut.emit("event-$it", null) }
        waitForSpill()

        val result = sut.drain()
        assertTrue("drain should succeed even after spill failure", result.isSuccess)
    }

    @Test
    fun `drain with non-existent spill directory returns memory signals only`() = runBlocking {
        val nonExistent = File(tempDir.root, "does-not-exist")
        val sut = createScope(dir = nonExistent)

        sut.emit("event", "data")
        val signals = sut.drain().getOrThrow()
        assertEquals(1, signals.size)
    }

    @Test
    fun `drain with empty spill file returns memory signals only`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(dir = dir)

        dir.mkdirs()
        File(dir, "spill.bin").createNewFile()

        sut.emit("event", "data")
        val signals = sut.drain().getOrThrow()
        assertEquals(1, signals.size)
        assertEquals("event", signals[0].tag)
    }

    // ========================================
    // Serialization edge cases
    // ========================================

    @Test
    fun `signal with tab in tag survives spill roundtrip`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 2, dir = dir)

        sut.emit("tag\twith\ttabs", "data")
        sut.emit("trigger-spill", null)
        sut.emit("trigger-spill-2", null)
        waitForSpill()

        val signals = sut.drain().getOrThrow()
        val tabSignal = signals.find { it.tag == "tag\twith\ttabs" }
        assertNotNull("Signal with tabs in tag should survive roundtrip", tabSignal)
    }

    @Test
    fun `signal with tab in data survives spill roundtrip`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 2, dir = dir)

        sut.emit("event", "data\twith\ttabs")
        sut.emit("trigger-spill", null)
        sut.emit("trigger-spill-2", null)
        waitForSpill()

        val signals = sut.drain().getOrThrow()
        val found = signals.find { it.tag == "event" }
        assertNotNull("Signal should survive", found)
        assertEquals("data\twith\ttabs", found?.data)
    }

    @Test
    fun `signal with newline in data - known limitation`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 2, dir = dir)

        sut.emit("event", "line1\nline2")
        sut.emit("trigger-spill", null)
        sut.emit("trigger-spill-2", null)
        waitForSpill()

        val signals = sut.drain().getOrThrow()
        assertTrue(signals.isNotEmpty())
    }

    @Test
    fun `signal with empty string data`() = runBlocking {
        val sut = createScope()
        sut.emit("event", "")

        val signal = sut.drain().getOrThrow().single()
        assertEquals("event", signal.tag)
        assertEquals("", signal.data)
    }

    @Test
    fun `signal with very long data`() = runBlocking {
        val sut = createScope()
        val longData = "x".repeat(10_000)
        sut.emit("event", longData)

        val signal = sut.drain().getOrThrow().single()
        assertEquals(longData, signal.data)
    }

    @Test
    fun `signal with unicode data`() = runBlocking {
        val sut = createScope()
        sut.emit("event", "données télémétrie 日本語")

        val signal = sut.drain().getOrThrow().single()
        assertEquals("données télémétrie 日本語", signal.data)
    }

    @Test
    fun `signal with empty tag`() = runBlocking {
        val sut = createScope()
        sut.emit("", "data")

        val signal = sut.drain().getOrThrow().single()
        assertEquals("", signal.tag)
    }

    // ========================================
    // Memory capacity boundary values
    // ========================================

    @Test
    fun `exactly at memory capacity does not spill`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 5, dir = dir)
        repeat(5) { sut.emit("event-$it", null) }

        waitForSpill()
        val spillFile = File(dir, "spill.bin")
        assertTrue(
            "Should not spill at exactly capacity",
            !spillFile.exists() || spillFile.length() == 0L,
        )

        val signals = sut.drain().getOrThrow()
        assertEquals(5, signals.size)
    }

    @Test
    fun `one over memory capacity triggers spill`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 5, dir = dir)
        repeat(6) { sut.emit("event-$it", null) }

        waitForSpill()
        val signals = sut.drain().getOrThrow()
        assertTrue(signals.isNotEmpty())
    }

    @Test
    fun `memory capacity of 1 spills on every second emit`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 1, dir = dir)

        sut.emit("a", null)
        sut.emit("b", null)
        waitForSpill()

        val signals = sut.drain().getOrThrow()
        assertEquals(2, signals.size)
    }

    // ========================================
    // Interleaved emit/drain cycles
    // ========================================

    @Test
    fun `multiple emit-drain cycles work correctly`() = runBlocking {
        val sut = createScope()

        repeat(5) { cycle ->
            sut.emit("cycle-$cycle", null)
            val signals = sut.drain().getOrThrow()
            assertEquals(1, signals.size)
            assertEquals("cycle-$cycle", signals[0].tag)
        }
    }

    @Test
    fun `drain between spill cycles returns accumulated signals`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 2, dir = dir)

        repeat(3) { sut.emit("batch1-$it", null) }
        waitForSpill()
        val first = sut.drain().getOrThrow()
        assertTrue(first.isNotEmpty())

        repeat(3) { sut.emit("batch2-$it", null) }
        waitForSpill()
        val second = sut.drain().getOrThrow()
        assertTrue(second.isNotEmpty())

        val third = sut.drain().getOrThrow()
        assertTrue(third.isEmpty())
    }

    // ========================================
    // Emit never throws
    // ========================================

    @Test
    fun `emit never throws regardless of internal failure`() {
        val sut =
            StreamTelemetryScopeImpl(
                name = "test",
                memoryCapacity = 0,
                diskCapacity = 0,
                spillDir = File("/nonexistent/path/that/cannot/exist"),
                redactor = null,
                scope = scope,
            )

        repeat(10) { sut.emit("event-$it", null) }
    }

    // ========================================
    // Boundary value: disk capacity
    // ========================================

    @Test
    fun `disk capacity of 0 means spilled signals are trimmed away`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 2, diskCapacity = 0, dir = dir)

        repeat(5) { sut.emit("event-$it", null) }
        waitForSpill()

        val signals = sut.drain().getOrThrow()
        // Spilled signals are trimmed to 0 bytes, but memory buffer survives.
        // With rapid emits and spill resets, count varies — key assertion is that
        // we get fewer than total emitted (some were lost to disk trim).
        assertTrue("Expected fewer than 5 due to disk trim, got ${signals.size}", signals.size < 5)
    }

    @Test
    fun `disk capacity of 1 byte trims aggressively`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 2, diskCapacity = 1, dir = dir)

        repeat(5) { sut.emit("event-$it", null) }
        waitForSpill()

        val spillFile = File(dir, "spill.bin")
        if (spillFile.exists()) {
            assertTrue(
                "Spill file should be trimmed, was ${spillFile.length()} bytes",
                spillFile.length() <= 50,
            )
        }
    }

    @Test
    fun `disk capacity exactly matches one signal line`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 2, diskCapacity = 50, dir = dir)

        repeat(10) { sut.emit("evt", "d") }
        waitForSpill()

        val spillFile = File(dir, "spill.bin")
        if (spillFile.exists()) {
            assertTrue("Spill file should stay around capacity", spillFile.length() <= 100)
        }
    }

    // ========================================
    // Boundary value: memory capacity
    // ========================================

    @Test
    fun `memory capacity of 0 spills every signal`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 0, dir = dir)

        sut.emit("event", null)
        waitForSpill()

        val spillFile = File(dir, "spill.bin")
        val signals = sut.drain().getOrThrow()
        assertTrue(signals.isNotEmpty() || spillFile.exists())
    }

    @Test
    fun `large memory capacity holds everything in memory`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 100_000, dir = dir)

        repeat(10_000) { sut.emit("event-$it", null) }

        val spillFile = File(dir, "spill.bin")
        assertTrue(
            "No spill expected with large capacity",
            !spillFile.exists() || spillFile.length() == 0L,
        )

        val signals = sut.drain().getOrThrow()
        assertEquals(10_000, signals.size)
    }

    // ========================================
    // Disk full / permission failures
    // ========================================

    @Test
    fun `emit survives when spill dir is read-only`() = runBlocking {
        val readOnlySpill = tempDir.newFolder("readonly-spill")
        readOnlySpill.setReadOnly()

        val sut =
            StreamTelemetryScopeImpl(
                name = "test",
                memoryCapacity = 2,
                diskCapacity = 1_000_000L,
                spillDir = File(readOnlySpill, "nested"),
                redactor = null,
                scope = scope,
            )

        repeat(10) { sut.emit("event-$it", null) }
        waitForSpill()

        val result = sut.drain()
        assertTrue("drain should succeed even after spill failure", result.isSuccess)
    }

    @Test
    fun `drain succeeds when spill file was deleted externally`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 2, dir = dir)

        repeat(3) { sut.emit("event-$it", null) }
        waitForSpill()

        File(dir, "spill.bin").delete()

        val result = sut.drain()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `drain succeeds when spill directory was deleted externally`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 2, dir = dir)

        repeat(3) { sut.emit("event-$it", null) }
        waitForSpill()

        dir.deleteRecursively()

        sut.emit("after-delete", null)
        val result = sut.drain()
        assertTrue(result.isSuccess)
        val signals = result.getOrThrow()
        assertTrue(signals.any { it.tag == "after-delete" })
    }

    // ========================================
    // Corrupt spill file
    // ========================================

    @Test
    fun `drain handles spill file with only blank lines`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(dir = dir)
        dir.mkdirs()
        File(dir, "spill.bin").writeText("\n\n\n\n")

        sut.emit("memory-signal", null)
        val signals = sut.drain().getOrThrow()
        assertEquals(1, signals.size)
        assertEquals("memory-signal", signals[0].tag)
    }

    @Test
    fun `drain handles spill file with mixed valid and invalid lines`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(dir = dir)
        dir.mkdirs()
        val content =
            listOf(
                    "${System.currentTimeMillis()}\tvalid-signal\tdata",
                    "not-a-timestamp\tbad",
                    "",
                    "${System.currentTimeMillis()}\tanother-valid\t",
                )
                .joinToString("\n")
        File(dir, "spill.bin").writeText(content + "\n")

        val signals = sut.drain().getOrThrow()
        val diskSignals = signals.filter { it.tag == "valid-signal" || it.tag == "another-valid" }
        assertEquals(2, diskSignals.size)
    }

    @Test
    fun `drain handles spill file with only one field per line`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(dir = dir)
        dir.mkdirs()
        File(dir, "spill.bin").writeText("justOneField\nandAnother\n")

        sut.emit("memory", null)
        val signals = sut.drain().getOrThrow()
        assertEquals(1, signals.size)
        assertEquals("memory", signals[0].tag)
    }

    @Test
    fun `drain handles binary garbage in spill file`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(dir = dir)
        dir.mkdirs()
        File(dir, "spill.bin")
            .writeBytes(byteArrayOf(0, 1, 2, 3, 0xFF.toByte(), 0xFE.toByte(), 10, 0, 0, 10))

        sut.emit("memory", null)
        val result = sut.drain()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `drain handles very large spill file`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(dir = dir, diskCapacity = 10_000_000)
        dir.mkdirs()

        val lines =
            (1..10_000).joinToString("\n") { i ->
                "${System.currentTimeMillis()}\tevent-$i\tpayload-$i"
            }
        File(dir, "spill.bin").writeText(lines + "\n")

        val signals = sut.drain().getOrThrow()
        assertEquals(10_000, signals.size)

        assertFalse(File(dir, "spill.bin").exists())
    }

    // ========================================
    // Spill file lifecycle
    // ========================================

    @Test
    fun `spill file is deleted after successful drain`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 2, dir = dir)

        repeat(3) { sut.emit("event-$it", null) }
        waitForSpill()

        val spillFile = File(dir, "spill.bin")
        assertTrue("Spill file should exist before drain", spillFile.exists())

        sut.drain()
        assertFalse("Spill file should be deleted after drain", spillFile.exists())
    }

    @Test
    fun `spill file is not created when signals fit in memory`() = runBlocking {
        val dir = tempDir.newFolder()
        val sut = createScope(memoryCapacity = 100, dir = dir)

        repeat(50) { sut.emit("event-$it", null) }
        waitForSpill()

        assertFalse("No spill file expected", File(dir, "spill.bin").exists())
    }

    // ========================================
    // Encode/decode edge cases
    // ========================================

    @Test
    fun `signal with literal backslash-t in tag - known limitation after spill roundtrip`() =
        runBlocking {
            val dir = tempDir.newFolder()
            val sut = createScope(memoryCapacity = 1, dir = dir)

            // The literal string "\t" (backslash + t) collides with the delimiter escape sequence.
            // After a spill roundtrip, decode converts "\\t" back to a real tab character.
            // This documents the known limitation.
            sut.emit("tag\\twith\\tescape", "data")
            sut.emit("trigger", null)
            waitForSpill()

            val signals = sut.drain().getOrThrow()
            // After roundtrip, \\t becomes \t (real tab) — the escape is ambiguous
            assertTrue(signals.any { it.tag == "tag\twith\tescape" })
        }

    @Test
    fun `signal with only whitespace tag and data`() = runBlocking {
        val sut = createScope()
        sut.emit("   ", "   ")

        val signal = sut.drain().getOrThrow().single()
        assertEquals("   ", signal.tag)
        assertEquals("   ", signal.data)
    }

    @Test
    fun `timestamps are monotonically non-decreasing`() = runBlocking {
        val sut = createScope()
        repeat(100) { sut.emit("event-$it", null) }

        val signals = sut.drain().getOrThrow()
        for (i in 1 until signals.size) {
            assertTrue(
                "Timestamps should be monotonically non-decreasing",
                signals[i].timestamp >= signals[i - 1].timestamp,
            )
        }
    }
}
