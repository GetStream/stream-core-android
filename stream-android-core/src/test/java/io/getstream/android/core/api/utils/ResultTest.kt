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
package io.getstream.android.core.api.utils

import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.*
import org.junit.Test

class ResultTest {

    @Test
    fun `flatMap - success chains to success`() {
        val res = Result.success(2).flatMap { v -> Result.success(v * 3) }
        assertTrue(res.isSuccess)
        assertEquals(6, res.getOrNull())
    }

    @Test
    fun `flatMap - failure propagates unchanged`() {
        val ex = IllegalArgumentException("fail")
        val start: Result<Int> = Result.failure(ex)
        val res = start.flatMap { Result.success(it * 2) }
        assertTrue(res.isFailure)
        assertSame(ex, res.exceptionOrNull())
    }

    @Test(expected = IllegalStateException::class)
    fun `flatMap - transform throws non-cancellation - rethrows`() {
        Result.success(1).flatMap<Int, Int> {
            @Suppress("ThrowingExceptionsWithoutMessage") throw IllegalStateException()
        }
        // Expect exception propagated, not wrapped in Result
    }

    // ---------- flatMapCatching ----------

    @Test
    fun `flatMapCatching - success chains to success`() {
        val res = Result.success(4).flatMapCatching { v -> Result.success(v + 1) }
        assertTrue(res.isSuccess)
        assertEquals(5, res.getOrNull())
    }

    @Test
    fun `flatMapCatching - input failure propagates unchanged`() {
        val ex = IllegalArgumentException("boom")
        val start: Result<Int> = Result.failure(ex)
        val res = start.flatMapCatching { Result.success(it * 2) }
        assertTrue(res.isFailure)
        assertSame(ex, res.exceptionOrNull())
    }

    @Test
    fun `flatMapCatching - transform returns failure is propagated`() {
        val ex = IllegalStateException("x")
        val res = Result.success(10).flatMapCatching { Result.failure<Int>(ex) }
        assertTrue(res.isFailure)
        assertSame(ex, res.exceptionOrNull())
    }

    @Test
    fun `flatMapCatching - transform throws non-cancellation - wrapped as failure`() {
        val res = Result.success(7).flatMapCatching<Int, Int> { throw IllegalStateException("bad") }
        assertTrue(res.isFailure)
        val e = res.exceptionOrNull()
        assertNotNull(e)
        assertTrue(e is IllegalStateException)
        assertEquals("bad", e!!.message)
    }

    @Test
    fun `flatMapCatching - transform throws CancellationException - rethrown`() {
        val ce = CancellationException("cancel")
        try {
            Result.success(1).flatMapCatching<Int, Int> { throw ce }
            fail("Expected CancellationException to be thrown")
        } catch (e: CancellationException) {
            assertSame(ce, e) // the exact CE must be rethrown
        }
    }

    @Test
    fun `flatMapCatching - input failure is CancellationException - rethrown`() {
        val ce = CancellationException("stop")
        try {
            Result.failure<Int>(ce).flatMapCatching { Result.success(it) }
            fail("Expected CancellationException to be thrown")
        } catch (e: CancellationException) {
            assertSame(ce, e)
        }
    }
}
