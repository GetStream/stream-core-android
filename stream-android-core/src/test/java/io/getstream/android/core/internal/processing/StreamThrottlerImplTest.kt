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

import io.mockk.mockk
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

    @Test
    fun `first value is delivered immediately`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                windowMs = 1_000,
            )
        throttler.onValue { delivered.add(it) }

        val accepted = throttler.submit("first")
        testScheduler.runCurrent()

        assertTrue(accepted)
        assertEquals(listOf("first"), delivered)
    }

    @Test
    fun `second value within window is dropped`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                windowMs = 1_000,
            )
        throttler.onValue { delivered.add(it) }

        val first = throttler.submit("first")
        testScheduler.runCurrent()
        val second = throttler.submit("second")
        testScheduler.runCurrent()

        assertTrue(first)
        assertFalse(second)
        assertEquals(listOf("first"), delivered)
    }

    @Test
    fun `value after window expires is delivered`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                windowMs = 1_000,
            )
        throttler.onValue { delivered.add(it) }

        throttler.submit("first")
        testScheduler.runCurrent()

        advanceTimeBy(1_001)
        testScheduler.runCurrent()

        val accepted = throttler.submit("second")
        testScheduler.runCurrent()

        assertTrue(accepted)
        assertEquals(listOf("first", "second"), delivered)
    }

    @Test
    fun `rapid burst delivers only the first value`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<Int>()
        val throttler =
            StreamThrottlerImpl<Int>(
                scope = scope,
                logger = mockk(relaxed = true),
                windowMs = 1_000,
            )
        throttler.onValue { delivered.add(it) }

        val results = (1..50).map { throttler.submit(it) }
        testScheduler.runCurrent()

        assertTrue(results.first())
        assertTrue(results.drop(1).all { !it })
        assertEquals(listOf(1), delivered)
    }

    @Test
    fun `multiple windows deliver one value each`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                windowMs = 500,
            )
        throttler.onValue { delivered.add(it) }

        throttler.submit("a")
        testScheduler.runCurrent()

        advanceTimeBy(501)
        testScheduler.runCurrent()
        throttler.submit("b")
        testScheduler.runCurrent()
        assertFalse(throttler.submit("b-dropped"))

        advanceTimeBy(501)
        testScheduler.runCurrent()
        throttler.submit("c")
        testScheduler.runCurrent()

        assertEquals(listOf("a", "b", "c"), delivered)
    }

    @Test
    fun `reset allows immediate delivery`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                windowMs = 1_000,
            )
        throttler.onValue { delivered.add(it) }

        throttler.submit("first")
        testScheduler.runCurrent()

        throttler.reset()

        val accepted = throttler.submit("after-reset")
        testScheduler.runCurrent()

        assertTrue(accepted)
        assertEquals(listOf("first", "after-reset"), delivered)
    }

    @Test
    fun `submit returns false when window is active`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                windowMs = 1_000,
            )
        throttler.onValue {}

        assertTrue(throttler.submit("first"))
        assertFalse(throttler.submit("second"))
        assertFalse(throttler.submit("third"))
    }

    @Test
    fun `no crash when no callback registered`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                windowMs = 1_000,
            )

        val accepted = throttler.submit("orphan")
        testScheduler.runCurrent()

        assertTrue(accepted)
    }

    @Test
    fun `reset mid-window allows new value through`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                windowMs = 1_000,
            )
        throttler.onValue { delivered.add(it) }

        throttler.submit("first")
        testScheduler.runCurrent()

        advanceTimeBy(300)
        throttler.reset()

        throttler.submit("mid-window")
        testScheduler.runCurrent()

        assertEquals(listOf("first", "mid-window"), delivered)
    }

    @Test
    fun `window reopens exactly after windowMs`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val delivered = mutableListOf<String>()
        val throttler =
            StreamThrottlerImpl<String>(
                scope = scope,
                logger = mockk(relaxed = true),
                windowMs = 1_000,
            )
        throttler.onValue { delivered.add(it) }

        throttler.submit("first")
        testScheduler.runCurrent()

        // At exactly 999ms, still within window
        advanceTimeBy(999)
        testScheduler.runCurrent()
        assertFalse(throttler.submit("too-early"))

        // At 1001ms total, window should have closed
        advanceTimeBy(2)
        testScheduler.runCurrent()
        assertTrue(throttler.submit("on-time"))
        testScheduler.runCurrent()

        assertEquals(listOf("first", "on-time"), delivered)
    }
}
