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
}
