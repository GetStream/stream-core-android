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

import io.getstream.android.core.api.processing.StreamDebouncer
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamDebouncerImplTest {

    private fun debouncer(delayMs: Long = 200L): StreamDebouncer<String> =
        StreamDebouncerImpl(
            scope =
                kotlinx.coroutines.CoroutineScope(
                    kotlinx.coroutines.SupervisorJob() +
                        kotlinx.coroutines.test.StandardTestDispatcher(
                            kotlinx.coroutines.test.TestCoroutineScheduler()
                        )
                ),
            logger = mockk(relaxed = true),
            delayMs = delayMs,
        )

    @Test
    fun `delivers value after delay elapses`() = runTest {
        val delivered = mutableListOf<String>()
        val debouncer =
            StreamDebouncerImpl<String>(
                scope = backgroundScope,
                logger = mockk(relaxed = true),
                delayMs = 100,
            )
        debouncer.onValue { delivered.add(it) }

        debouncer.submit("hello")
        advanceTimeBy(50)
        assertTrue(delivered.isEmpty())

        advanceTimeBy(51)
        advanceUntilIdle()
        assertEquals(listOf("hello"), delivered)
    }

    @Test
    fun `last value wins when multiple submitted within window`() = runTest {
        val delivered = mutableListOf<String>()
        val debouncer =
            StreamDebouncerImpl<String>(
                scope = backgroundScope,
                logger = mockk(relaxed = true),
                delayMs = 100,
            )
        debouncer.onValue { delivered.add(it) }

        debouncer.submit("first")
        advanceTimeBy(50)
        debouncer.submit("second")
        advanceTimeBy(50)
        debouncer.submit("third")

        advanceTimeBy(101)
        advanceUntilIdle()

        assertEquals(listOf("third"), delivered)
    }

    @Test
    fun `timer resets on each submit`() = runTest {
        val delivered = mutableListOf<String>()
        val debouncer =
            StreamDebouncerImpl<String>(
                scope = backgroundScope,
                logger = mockk(relaxed = true),
                delayMs = 100,
            )
        debouncer.onValue { delivered.add(it) }

        debouncer.submit("a")
        advanceTimeBy(80)
        assertTrue(delivered.isEmpty())

        debouncer.submit("b")
        advanceTimeBy(80)
        assertTrue(delivered.isEmpty())

        advanceTimeBy(21)
        advanceUntilIdle()
        assertEquals(listOf("b"), delivered)
    }

    @Test
    fun `cancel prevents delivery`() = runTest {
        val delivered = mutableListOf<String>()
        val debouncer =
            StreamDebouncerImpl<String>(
                scope = backgroundScope,
                logger = mockk(relaxed = true),
                delayMs = 100,
            )
        debouncer.onValue { delivered.add(it) }

        debouncer.submit("value")
        advanceTimeBy(50)
        debouncer.cancel()

        advanceTimeBy(200)
        advanceUntilIdle()
        assertTrue(delivered.isEmpty())
    }

    @Test
    fun `submit after cancel works normally`() = runTest {
        val delivered = mutableListOf<String>()
        val debouncer =
            StreamDebouncerImpl<String>(
                scope = backgroundScope,
                logger = mockk(relaxed = true),
                delayMs = 100,
            )
        debouncer.onValue { delivered.add(it) }

        debouncer.submit("cancelled")
        debouncer.cancel()
        debouncer.submit("delivered")

        advanceTimeBy(101)
        advanceUntilIdle()
        assertEquals(listOf("delivered"), delivered)
    }

    @Test
    fun `delivers each settled value separately when gaps exceed delay`() = runTest {
        val delivered = mutableListOf<String>()
        val debouncer =
            StreamDebouncerImpl<String>(
                scope = backgroundScope,
                logger = mockk(relaxed = true),
                delayMs = 100,
            )
        debouncer.onValue { delivered.add(it) }

        debouncer.submit("first")
        advanceTimeBy(101)
        advanceUntilIdle()

        debouncer.submit("second")
        advanceTimeBy(101)
        advanceUntilIdle()

        assertEquals(listOf("first", "second"), delivered)
    }

    @Test
    fun `rapid fire burst delivers only the last value`() = runTest {
        val delivered = mutableListOf<Int>()
        val debouncer =
            StreamDebouncerImpl<Int>(
                scope = backgroundScope,
                logger = mockk(relaxed = true),
                delayMs = 100,
            )
        debouncer.onValue { delivered.add(it) }

        for (i in 1..50) {
            debouncer.submit(i)
        }

        advanceTimeBy(101)
        advanceUntilIdle()

        assertEquals(listOf(50), delivered)
    }

    @Test
    fun `no delivery when no callback registered`() = runTest {
        val debouncer =
            StreamDebouncerImpl<String>(
                scope = backgroundScope,
                logger = mockk(relaxed = true),
                delayMs = 100,
            )

        debouncer.submit("orphan")
        advanceTimeBy(200)
        advanceUntilIdle()
        // No crash, no delivery — default no-op callback
    }
}
