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

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.annotations.StreamPublishedApi

/**
 * A comparator that can sort model instances by extracting comparable values.
 *
 * This class provides the foundation for local sorting operations by wrapping a lambda that
 * extracts comparable values from model instances. It handles the comparison logic and direction
 * handling internally.
 *
 * @param T The type of the model instances to be compared.
 * @param V The type of the comparable value extracted from the model instances.
 * @property value A lambda that extracts a comparable value from a model instance.
 */
@StreamPublishedApi
public class SortComparator<T, V : Comparable<V>>(public val value: (T) -> V) {

    /**
     * Compares two model instances using the extracted values and sort direction.
     *
     * @param lhs The first model instance to compare
     * @param rhs The second model instance to compare
     * @param direction The direction of the sort
     * @return A comparison result indicating the relative ordering
     */
    public fun compare(lhs: T?, rhs: T?, direction: SortDirection): Int {
        val value1 = lhs?.let(value)
        val value2 = rhs?.let(value)

        return when {
            value1 == null && value2 == null -> 0
            value1 == null -> -direction.value
            value2 == null -> direction.value
            else -> value1.compareTo(value2) * direction.value
        }
    }

    /**
     * Converts this comparator to a type-erased version.
     *
     * @return An AnySortComparator that wraps this comparator
     */
    public fun toAny(): AnySortComparator<T> {
        return AnySortComparator(this)
    }
}

/**
 * A type-erased wrapper for sort comparators that can work with any model type.
 *
 * This class provides a way to store and use sort comparators without knowing their specific
 * generic type parameters. It's useful for creating collections of different sort configurations
 * that can all work with the same model type.
 *
 * Type erased type avoids making SortField generic while keeping the underlying value type intact
 * (no runtime type checks while sorting).
 */
@StreamPublishedApi
public class AnySortComparator<T>(private val compare: (T?, T?, SortDirection) -> Int) {

    /**
     * Creates a type-erased comparator from a specific comparator instance.
     *
     * @param sort The specific comparator to wrap
     */
    public constructor(sort: SortComparator<T, *>) : this(sort::compare)

    /**
     * Compares two model instances using the wrapped comparator.
     *
     * @param lhs The left-hand side model instance
     * @param rhs The right-hand side model instance
     * @param direction The direction of the sort
     * @return A comparison result indicating the relative ordering
     */
    public fun compare(lhs: T?, rhs: T?, direction: SortDirection): Int {
        return this.compare.invoke(lhs, rhs, direction)
    }
}

/**
 * Extension function to sort a list of models using a list of sort configurations.
 *
 * @param T The type of elements in the list.
 * @param sort A list of sort configurations to apply to the list.
 */
@StreamInternalApi
public fun <T> List<T>.sortedWith(sort: List<Sort<T>>): List<T> =
    sortedWith(CompositeComparator(sort))

/**
 * A composite comparator that combines multiple sort comparators. This class allows for sorting
 * based on multiple criteria, where each comparator is applied in sequence.
 *
 * This implementation mirrors the Swift Array.sorted(using:) extension behavior:
 * - Iterates through each sort comparator in order
 * - Returns the first non-equal comparison result
 * - If all comparators return equal (0), returns 0 to maintain stable sort order
 *
 * @param T The type of elements to be compared.
 * @param comparators The list of comparators to be combined.
 */
@StreamInternalApi
public class CompositeComparator<T>(private val comparators: List<Comparator<T>>) : Comparator<T> {

    override fun compare(o1: T, o2: T): Int {
        for (comparator in comparators) {
            val result = comparator.compare(o1, o2)
            when (result) {
                0 -> continue // Equal, move to the next comparator
                else -> return result // Return the first non-equal comparison result
            }
        }
        return 0 // All comparators returned equal, maintain original order
    }
}
