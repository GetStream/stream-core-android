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

import io.getstream.android.core.api.model.exceptions.StreamAggregateException
import kotlin.test.assertSame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlgebraTest {

    @Test
    fun `plus combines plain throwables into aggregate`() {
        val first = IllegalStateException("one")
        val second = IllegalArgumentException("two")

        val combined = first + second

        assertEquals(listOf(first, second), combined.causes)
        assertTrue(combined.message?.contains("Multiple errors occurred") == true)
    }

    @Test
    fun `plus merges causes when both sides are aggregates`() {
        val firstCause = IllegalStateException("first")
        val secondCause = IllegalArgumentException("second")
        val thirdCause = IllegalArgumentException("third")
        val firstAgg = StreamAggregateException("left", listOf(firstCause, secondCause))
        val secondAgg = StreamAggregateException("right", listOf(thirdCause))

        val combined = firstAgg + secondAgg

        assertEquals(listOf(firstCause, secondCause, thirdCause), combined.causes)
    }

    @Test
    fun `plus appends plain throwable to existing aggregate on left`() {
        val existing = IllegalStateException("existing")
        val other = IllegalArgumentException("other")
        val aggregate = StreamAggregateException("agg", listOf(existing))

        val combined = aggregate + other

        assertEquals(listOf(existing, other), combined.causes)
    }

    @Test
    fun `plus prepends plain throwable to aggregate on right`() {
        val first = IllegalStateException("first")
        val second = IllegalArgumentException("second")
        val third = IllegalArgumentException("third")
        val rightAggregate = StreamAggregateException("agg", listOf(second, third))

        val combined = first + rightAggregate

        assertEquals(listOf(first, second, third), combined.causes)
    }

    @Test
    fun `times returns pair when both results succeed`() {
        val left = Result.success(4)
        val right = Result.success("value")

        val combined = left * right

        assertEquals(4 to "value", combined.getOrThrow())
    }

    @Test
    fun `times propagates failure from left result`() {
        val failure = IllegalStateException("failed")
        val left = Result.failure<Int>(failure)
        val right = Result.success("value")

        val combined = left * right

        assertTrue(combined.isFailure)
        assertSame(failure, combined.exceptionOrNull())
    }

    @Test
    fun `times propagates failure from right result`() {
        val left = Result.success(1)
        val failure = IllegalArgumentException("broken")
        val right = Result.failure<String>(failure)

        val combined = left * right

        assertTrue(combined.isFailure)
        assertSame(failure, combined.exceptionOrNull())
    }

    @Test
    fun `times propagates both results when both fail`() {
        val failure = IllegalArgumentException("broken")
        val failure2 = IllegalArgumentException("broken2")
        val left = Result.failure<String>(failure)
        val right = Result.failure<String>(failure2)

        val combined = left * right

        assertTrue(combined.isFailure)
        val exception = combined.exceptionOrNull() as StreamAggregateException
        assertEquals(listOf(failure, failure2), exception.causes)
    }
}
