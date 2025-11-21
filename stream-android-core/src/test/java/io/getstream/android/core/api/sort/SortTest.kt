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

package io.getstream.android.core.api.sort

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.collections.sortedWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

internal class SortTest {

    private data class TestModel(val score: Int, val name: String)

    @Test
    fun `sort toDto exposes remote field and direction`() {
        // Given
        val field = SortField.create<TestModel, Int>(remote = "score") { it.score }
        val sort = Sort(field = field, direction = SortDirection.REVERSE)

        // When
        val dto = sort.toDto()

        // Then
        assertEquals(mapOf("field" to "score", "direction" to -1), dto)
    }

    @Test
    fun `sort compare delegates to comparator`() {
        // Given
        val comparator = mockk<AnySortComparator<TestModel>>()
        val first = TestModel(score = 10, name = "b")
        val second = TestModel(score = 7, name = "a")
        every { comparator.compare(first, second, SortDirection.REVERSE) } returns 42
        val sortField =
            object : SortField<TestModel> {
                override val comparator: AnySortComparator<TestModel> = comparator
                override val remote: String = "score"
            }
        val sort = Sort(field = sortField, direction = SortDirection.REVERSE)

        // When
        val result = sort.compare(first, second)

        // Then
        assertEquals(42, result)
        verify(exactly = 1) { comparator.compare(first, second, SortDirection.REVERSE) }
    }

    @Test
    fun `sort comparator pushes null objects to the end in forward order`() {
        // Given
        val comparator = SortComparator<TestModel, Int> { it.score }

        // When
        val comparison =
            comparator.compare(
                lhs = null,
                rhs = TestModel(score = 5, name = "a"),
                direction = SortDirection.FORWARD,
            )

        // Then
        assertEquals(-1, comparison)
    }

    @Test
    fun `sort comparator in reverse order flips the comparison result`() {
        // Given
        val comparator = SortComparator<TestModel, Int> { it.score }
        val lhs = TestModel(score = 3, name = "a")
        val rhs = TestModel(score = 7, name = "b")

        // When
        val forwardResult = comparator.compare(lhs, rhs, SortDirection.FORWARD)
        val reverseResult = comparator.compare(lhs, rhs, SortDirection.REVERSE)

        // Then
        assertTrue(forwardResult < 0)
        assertEquals(-forwardResult, reverseResult)
    }

    @Test
    fun `sortedWith applies multiple sorts in order`() {
        // Given
        val models =
            listOf(
                TestModel(score = 1, name = "c"),
                TestModel(score = 2, name = "b"),
                TestModel(score = 1, name = "a"),
            )
        val scoreSort =
            Sort(SortField.create<TestModel, Int>("score") { it.score }, SortDirection.FORWARD)
        val nameSort =
            Sort(SortField.create<TestModel, String>("name") { it.name }, SortDirection.FORWARD)

        // When
        val sorted = models.sortedWith(listOf(scoreSort, nameSort))

        // Then
        assertEquals(
            listOf(
                TestModel(score = 1, name = "a"),
                TestModel(score = 1, name = "c"),
                TestModel(score = 2, name = "b"),
            ),
            sorted,
        )
    }
}
