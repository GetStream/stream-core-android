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

package io.getstream.android.core.api.utils

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class CatchingUtilsTest {
    @Test
    fun `returns success with value`() {
        val result = runCatchingCancellable { 42 }
        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `returns success with null for nullable T`() {
        val result = runCatchingCancellable<String?> { null }
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `wraps non-cancellation exceptions into failure`() {
        val boom = IllegalStateException("boom")
        val result = runCatchingCancellable<Int> { throw boom }
        assertTrue(result.isFailure)
        assertSame(boom, result.exceptionOrNull())
    }

    @Test
    fun `rethrows CancellationException`() {
        try {
            runCatchingCancellable<Int> { throw CancellationException("cancelled") }
            fail("Expected CancellationException to be rethrown, not wrapped")
        } catch (ce: CancellationException) {
            assertEquals("cancelled", ce.message)
        }
    }

    @Test
    fun `invokes block at most once on success`() {
        val calls = AtomicInteger(0)
        val result = runCatchingCancellable {
            calls.incrementAndGet()
            "ok"
        }
        assertTrue(result.isSuccess)
        assertEquals("ok", result.getOrNull())
        assertEquals(1, calls.get())
    }

    @Test
    fun `invokes block at most once on failure`() {
        val calls = AtomicInteger(0)
        val err = RuntimeException("x")
        val result =
            runCatchingCancellable<Unit> {
                calls.incrementAndGet()
                throw err
            }
        assertTrue(result.isFailure)
        assertSame(err, result.exceptionOrNull())
        assertEquals(1, calls.get())
    }
}
