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

import io.getstream.android.core.api.processing.StreamThrottlePolicy
import io.mockk.mockk
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamThrottlerImplTest {

    // ---- Leading mode ----

    @Test
    fun `leading - first value is delivered immediately`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.leading(windowMs = 1_000),
            )
        throttler.onValue { delivered.add(it) }

        val accepted = throttler.submit("first")
        testScheduler.runCurrent()

        assertTrue(accepted)
        assertEquals(listOf("first"), delivered)
    }

    @Test
    fun `leading - second value within window is dropped`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.leading(windowMs = 1_000),
            )
        throttler.onValue { delivered.add(it) }

        assertTrue(throttler.submit("first"))
        testScheduler.runCurrent()
        assertFalse(throttler.submit("second"))
        testScheduler.runCurrent()

        assertEquals(listOf("first"), delivered)
    }

    @Test
    fun `leading - value after window expires is delivered`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.leading(windowMs = 1_000),
            )
        throttler.onValue { delivered.add(it) }

        throttler.submit("first")
        testScheduler.runCurrent()
        advanceTimeBy(1_001)
        testScheduler.runCurrent()

        assertTrue(throttler.submit("second"))
        testScheduler.runCurrent()

        assertEquals(listOf("first", "second"), delivered)
    }

    @Test
    fun `leading - rapid burst delivers only the first value`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<Int>()
        val throttler =
            StreamThrottlerImpl<Int>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.leading(windowMs = 1_000),
            )
        throttler.onValue { delivered.add(it) }

        val results = (1..50).map { throttler.submit(it) }
        testScheduler.runCurrent()

        assertTrue(results.first())
        assertTrue(results.drop(1).all { !it })
        assertEquals(listOf(1), delivered)
    }

    @Test
    fun `leading - multiple windows deliver one value each`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.leading(windowMs = 500),
            )
        throttler.onValue { delivered.add(it) }

        throttler.submit("a")
        testScheduler.runCurrent()
        advanceTimeBy(501)
        testScheduler.runCurrent()

        throttler.submit("b")
        testScheduler.runCurrent()
        advanceTimeBy(501)
        testScheduler.runCurrent()

        throttler.submit("c")
        testScheduler.runCurrent()

        assertEquals(listOf("a", "b", "c"), delivered)
    }

    @Test
    fun `leading - reset allows immediate delivery`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.leading(windowMs = 1_000),
            )
        throttler.onValue { delivered.add(it) }

        throttler.submit("first")
        testScheduler.runCurrent()
        throttler.reset()

        assertTrue(throttler.submit("after-reset"))
        testScheduler.runCurrent()

        assertEquals(listOf("first", "after-reset"), delivered)
    }

    @Test
    fun `leading - stale window expiry does not close new window after reset`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.leading(windowMs = 1_000),
            )
        throttler.onValue { delivered.add(it) }

        // t=0: submit A, starts window expiry at t=1000
        throttler.submit("A")
        testScheduler.runCurrent()

        // t=300: reset cancels old window job
        advanceTimeBy(300)
        throttler.reset()

        // t=300: submit B, starts new window expiry at t=1300
        assertTrue(throttler.submit("B"))
        testScheduler.runCurrent()

        // t=1000: old delay WOULD have fired here — must NOT clear the window
        advanceTimeBy(700)
        testScheduler.runCurrent()

        // C should be rejected because B's window (until t=1300) is still active
        assertFalse(throttler.submit("C"))

        // t=1301: B's window expires
        advanceTimeBy(301)
        testScheduler.runCurrent()
        assertTrue(throttler.submit("D"))
        testScheduler.runCurrent()

        assertEquals(listOf("A", "B", "D"), delivered)
    }

    @Test
    fun `leading - no crash when no callback registered`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.leading(windowMs = 1_000),
            )

        assertTrue(throttler.submit("orphan"))
        testScheduler.runCurrent()
    }

    @Test
    fun `leading - window reopens exactly after windowMs`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.leading(windowMs = 1_000),
            )
        throttler.onValue { delivered.add(it) }

        throttler.submit("first")
        testScheduler.runCurrent()

        advanceTimeBy(999)
        testScheduler.runCurrent()
        assertFalse(throttler.submit("too-early"))

        advanceTimeBy(2)
        testScheduler.runCurrent()
        assertTrue(throttler.submit("on-time"))
        testScheduler.runCurrent()

        assertEquals(listOf("first", "on-time"), delivered)
    }

    // ---- Trailing mode ----

    @Test
    fun `trailing - single value delivered after window expires`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.trailing(windowMs = 1_000),
            )
        throttler.onValue { delivered.add(it) }

        assertTrue(throttler.submit("first"))
        testScheduler.runCurrent()
        assertTrue(delivered.isEmpty())

        advanceTimeBy(1_001)
        testScheduler.runCurrent()

        assertEquals(listOf("first"), delivered)
    }

    @Test
    fun `trailing - last value wins within window`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.trailing(windowMs = 1_000),
            )
        throttler.onValue { delivered.add(it) }

        assertTrue(throttler.submit("first"))
        assertTrue(throttler.submit("second"))
        assertTrue(throttler.submit("third"))
        testScheduler.runCurrent()
        assertTrue(delivered.isEmpty())

        advanceTimeBy(1_001)
        testScheduler.runCurrent()

        assertEquals(listOf("third"), delivered)
    }

    @Test
    fun `trailing - all submits return true`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.trailing(windowMs = 1_000),
            )
        throttler.onValue {}

        assertTrue(throttler.submit("a"))
        assertTrue(throttler.submit("b"))
        assertTrue(throttler.submit("c"))
    }

    @Test
    fun `trailing - multiple windows deliver last value each`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.trailing(windowMs = 500),
            )
        throttler.onValue { delivered.add(it) }

        throttler.submit("a1")
        throttler.submit("a2")
        advanceTimeBy(501)
        testScheduler.runCurrent()

        throttler.submit("b1")
        throttler.submit("b2")
        throttler.submit("b3")
        advanceTimeBy(501)
        testScheduler.runCurrent()

        assertEquals(listOf("a2", "b3"), delivered)
    }

    @Test
    fun `trailing - reset cancels pending delivery`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.trailing(windowMs = 1_000),
            )
        throttler.onValue { delivered.add(it) }

        throttler.submit("pending")
        testScheduler.runCurrent()
        throttler.reset()

        advanceTimeBy(1_001)
        testScheduler.runCurrent()

        assertTrue(delivered.isEmpty())
    }

    @Test
    fun `trailing - submit after reset starts new window`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.trailing(windowMs = 500),
            )
        throttler.onValue { delivered.add(it) }

        throttler.submit("cancelled")
        throttler.reset()

        throttler.submit("fresh")
        advanceTimeBy(501)
        testScheduler.runCurrent()

        assertEquals(listOf("fresh"), delivered)
    }

    // ---- LeadingAndTrailing mode ----

    @Test
    fun `leadingAndTrailing - first value delivered immediately`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.leadingAndTrailing(windowMs = 1_000),
            )
        throttler.onValue { delivered.add(it) }

        throttler.submit("first")
        testScheduler.runCurrent()

        assertEquals(listOf("first"), delivered)
    }

    @Test
    fun `leadingAndTrailing - trailing value delivered at window end`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.leadingAndTrailing(windowMs = 1_000),
            )
        throttler.onValue { delivered.add(it) }

        throttler.submit("first")
        testScheduler.runCurrent()
        throttler.submit("second")
        throttler.submit("third")

        advanceTimeBy(1_001)
        testScheduler.runCurrent()

        assertEquals(listOf("first", "third"), delivered)
    }

    @Test
    fun `leadingAndTrailing - no trailing delivery if no new values`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.leadingAndTrailing(windowMs = 1_000),
            )
        throttler.onValue { delivered.add(it) }

        throttler.submit("only-one")
        testScheduler.runCurrent()

        advanceTimeBy(1_001)
        testScheduler.runCurrent()

        assertEquals(listOf("only-one"), delivered)
    }

    @Test
    fun `leadingAndTrailing - all submits return true`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.leadingAndTrailing(windowMs = 1_000),
            )
        throttler.onValue {}

        assertTrue(throttler.submit("a"))
        assertTrue(throttler.submit("b"))
        assertTrue(throttler.submit("c"))
    }

    @Test
    fun `leadingAndTrailing - multiple windows`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.leadingAndTrailing(windowMs = 500),
            )
        throttler.onValue { delivered.add(it) }

        // Window 1: leading=a, trailing=c
        throttler.submit("a")
        testScheduler.runCurrent()
        throttler.submit("b")
        throttler.submit("c")
        advanceTimeBy(501)
        testScheduler.runCurrent()

        // Window 2: leading=d, no trailing
        throttler.submit("d")
        testScheduler.runCurrent()
        advanceTimeBy(501)
        testScheduler.runCurrent()

        assertEquals(listOf("a", "c", "d"), delivered)
    }

    @Test
    fun `leadingAndTrailing - reset clears pending trailing`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.leadingAndTrailing(windowMs = 1_000),
            )
        throttler.onValue { delivered.add(it) }

        throttler.submit("leading")
        testScheduler.runCurrent()
        throttler.submit("pending-trailing")
        throttler.reset()

        advanceTimeBy(1_001)
        testScheduler.runCurrent()

        assertEquals(listOf("leading"), delivered)
    }

    @Test
    fun `leadingAndTrailing - rapid burst delivers first and last`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<Int>()
        val throttler =
            StreamThrottlerImpl<Int>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.leadingAndTrailing(windowMs = 1_000),
            )
        throttler.onValue { delivered.add(it) }

        (1..50).forEach { throttler.submit(it) }
        testScheduler.runCurrent()

        advanceTimeBy(1_001)
        testScheduler.runCurrent()

        assertEquals(listOf(1, 50), delivered)
    }

    // ---- Edge cases ----

    @Test
    fun `policy rejects zero windowMs`() {
        assertFailsWith<IllegalArgumentException> { StreamThrottlePolicy.leading(windowMs = 0) }
        assertFailsWith<IllegalArgumentException> { StreamThrottlePolicy.trailing(windowMs = 0) }
        assertFailsWith<IllegalArgumentException> {
            StreamThrottlePolicy.leadingAndTrailing(windowMs = 0)
        }
    }

    @Test
    fun `policy rejects negative windowMs`() {
        assertFailsWith<IllegalArgumentException> { StreamThrottlePolicy.leading(windowMs = -1) }
    }

    @Test
    fun `leading - callback exception does not permanently lock throttler`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val handler = CoroutineExceptionHandler { _, _ -> /* swallow */ }
        val scope = CoroutineScope(SupervisorJob() + dispatcher + handler)
        val delivered = mutableListOf<String>()
        var shouldThrow = true
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.leading(windowMs = 500),
            )
        throttler.onValue {
            if (shouldThrow) {
                @Suppress("TooGenericExceptionThrown") throw RuntimeException("boom")
            }
            delivered.add(it)
        }

        // First submit — callback throws, but window timer still runs
        throttler.submit("explode")
        testScheduler.runCurrent()

        // Wait for window to expire
        advanceTimeBy(501)
        testScheduler.runCurrent()

        // Window should have expired, next submit should work
        shouldThrow = false
        assertTrue(throttler.submit("recover"))
        testScheduler.runCurrent()

        assertEquals(listOf("recover"), delivered)
    }

    @Test
    fun `trailing - stale window expiry does not close new window after reset`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.trailing(windowMs = 1_000),
            )
        throttler.onValue { delivered.add(it) }

        // t=0: submit A, starts window expiry at t=1000
        throttler.submit("A")
        testScheduler.runCurrent()

        // t=300: reset cancels old window
        advanceTimeBy(300)
        throttler.reset()

        // t=300: submit B, starts new window expiry at t=1300
        throttler.submit("B")
        testScheduler.runCurrent()

        // t=1000: old delay would have fired — must NOT deliver A or close B's window
        advanceTimeBy(700)
        testScheduler.runCurrent()
        assertTrue(delivered.isEmpty())

        // t=1300: update to C while B's window is still active
        throttler.submit("C")

        // t=1301: B's window expires, delivers latest value (C)
        advanceTimeBy(301)
        testScheduler.runCurrent()

        assertEquals(listOf("C"), delivered)
    }

    @Test
    fun `leadingAndTrailing - trailing delivery allows new window to start`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.leadingAndTrailing(windowMs = 500),
            )
        throttler.onValue { delivered.add(it) }

        // Window 1
        throttler.submit("leading-1")
        testScheduler.runCurrent()
        throttler.submit("trailing-1")
        advanceTimeBy(501)
        testScheduler.runCurrent()

        assertEquals(listOf("leading-1", "trailing-1"), delivered)

        // Window 2 should start fresh
        assertTrue(throttler.submit("leading-2"))
        testScheduler.runCurrent()

        assertEquals(listOf("leading-1", "trailing-1", "leading-2"), delivered)
    }

    @Test
    fun `double reset does not crash`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.leading(windowMs = 1_000),
            )

        throttler.submit("a")
        testScheduler.runCurrent()
        throttler.reset()
        throttler.reset()

        assertTrue(throttler.submit("b"))
    }

    @Test
    fun `submit after scope cancellation returns true but does not deliver`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val parentJob = SupervisorJob()
        val scope = CoroutineScope(parentJob + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                policy = StreamThrottlePolicy.leading(windowMs = 1_000),
            )
        throttler.onValue { delivered.add(it) }

        // Cancel the scope
        parentJob.cancel()
        testScheduler.runCurrent()

        // Submit returns true (CAS succeeds) but callback launch is dead
        val result = throttler.submit("dead")
        testScheduler.runCurrent()

        assertTrue(result)
        assertTrue(delivered.isEmpty())
    }
}
