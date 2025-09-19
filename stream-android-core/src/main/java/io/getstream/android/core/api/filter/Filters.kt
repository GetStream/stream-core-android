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
package io.getstream.android.core.api.filter

import io.getstream.android.core.annotations.StreamPublishedApi
import io.getstream.android.core.internal.filter.BinaryOperator
import io.getstream.android.core.internal.filter.CollectionOperator

/** Utility class for building filters. */
public object Filters {
    /**
     * Creates a filter that combines multiple filters with a logical AND operation.
     *
     * @param filters The filters to combine.
     * @return A filter that matches when all provided filters match.
     */
    @StreamPublishedApi
    public fun <M, F : FilterField<M>> and(vararg filters: Filter<M, F>): Filter<M, F> =
        CollectionOperationFilter(CollectionOperator.AND, filters.toSet())

    /**
     * Creates a filter that combines multiple filters with a logical OR operation.
     *
     * @param filters The filters to combine.
     * @return A filter that matches when any of the specified filters match.
     */
    @StreamPublishedApi
    public fun <M, F : FilterField<M>> or(vararg filters: Filter<M, F>): Filter<M, F> =
        CollectionOperationFilter(CollectionOperator.OR, filters.toSet())
}

/**
 * Creates a filter that checks if this field equals a specific value.
 *
 * @param value The value to check equality against.
 * @return A filter that matches when this field equals the specified value.
 */
@StreamPublishedApi
public fun <M, F : FilterField<M>> F.equal(value: Any): Filter<M, F> =
    BinaryOperationFilter(BinaryOperator.EQUAL, this, value)

/**
 * Creates a filter that checks if this field is greater than a specific value.
 *
 * @param value The value to check against.
 * @return A filter that matches when this field is greater than the specified value.
 */
@StreamPublishedApi
public fun <M, F : FilterField<M>> F.greater(value: Any): Filter<M, F> =
    BinaryOperationFilter(BinaryOperator.GREATER, this, value)

/**
 * Creates a filter that checks if this field is greater than or equal to a specific value.
 *
 * @param value The value to check against.
 * @return A filter that matches when this field is greater than or equal to the specified value.
 */
@StreamPublishedApi
public fun <M, F : FilterField<M>> F.greaterOrEqual(value: Any): Filter<M, F> =
    BinaryOperationFilter(BinaryOperator.GREATER_OR_EQUAL, this, value)

/**
 * Creates a filter that checks if this field is less than a specific value.
 *
 * @param value The value to check against.
 * @return A filter that matches when this field is less than the specified value.
 */
@StreamPublishedApi
public fun <M, F : FilterField<M>> F.less(value: Any): Filter<M, F> =
    BinaryOperationFilter(BinaryOperator.LESS, this, value)

/**
 * Creates a filter that checks if this field is less than or equal to a specific value.
 *
 * @param value The value to check against.
 * @return A filter that matches when this field is less than or equal to the specified value.
 */
@StreamPublishedApi
public fun <M, F : FilterField<M>> F.lessOrEqual(value: Any): Filter<M, F> =
    BinaryOperationFilter(BinaryOperator.LESS_OR_EQUAL, this, value)

/**
 * Creates a filter that checks if this field's value is in a specific list of values.
 *
 * @param values The list of values to check against.
 * @return A filter that matches when this field's value is in the specified list.
 */
@StreamPublishedApi
public fun <M, F : FilterField<M>> F.`in`(values: List<Any>): Filter<M, F> =
    BinaryOperationFilter(BinaryOperator.IN, this, values.toSet())

/**
 * Creates a filter that checks if this field's value is in a specific set of values.
 *
 * @param values The values to check against.
 * @return A filter that matches when this field's value is in the specified values.
 */
@StreamPublishedApi
public fun <M, F : FilterField<M>> F.`in`(vararg values: Any): Filter<M, F> =
    BinaryOperationFilter(BinaryOperator.IN, this, values.toSet())

/**
 * Creates a filter that performs a full-text query on this field.
 *
 * @param value The query string to search for.
 * @return A filter that matches based on the full-text query.
 */
@StreamPublishedApi
public fun <M, F : FilterField<M>> F.query(value: String): Filter<M, F> =
    BinaryOperationFilter(BinaryOperator.QUERY, this, value)

/**
 * Creates a filter that performs autocomplete matching on this field.
 *
 * @param value The string to autocomplete against.
 * @return A filter that matches based on autocomplete functionality.
 */
@StreamPublishedApi
public fun <M, F : FilterField<M>> F.autocomplete(value: String): Filter<M, F> =
    BinaryOperationFilter(BinaryOperator.AUTOCOMPLETE, this, value)

/**
 * Creates a filter that checks if this field exists.
 *
 * @return A filter that matches when this field exists.
 */
@StreamPublishedApi
public fun <M, F : FilterField<M>> F.exists(): Filter<M, F> =
    BinaryOperationFilter(BinaryOperator.EXISTS, this, true)

/**
 * Creates a filter that checks if this field does not exist.
 *
 * @return A filter that matches when this field does not exist.
 */
@StreamPublishedApi
public fun <M, F : FilterField<M>> F.doesNotExist(): Filter<M, F> =
    BinaryOperationFilter(BinaryOperator.EXISTS, this, false)

/**
 * Creates a filter that checks if this field contains a specific value.
 *
 * @param value The value to check for within this field.
 * @return A filter that matches when this field contains the specified value.
 */
@StreamPublishedApi
public fun <M, F : FilterField<M>> F.contains(value: Any): Filter<M, F> =
    BinaryOperationFilter(BinaryOperator.CONTAINS, this, value)

/**
 * Creates a filter that checks if a specific path exists within this field.
 *
 * @param value The path to check for existence.
 * @return A filter that matches when the specified path exists in this field.
 */
@StreamPublishedApi
public fun <M, F : FilterField<M>> F.pathExists(value: String): Filter<M, F> =
    BinaryOperationFilter(BinaryOperator.PATH_EXISTS, this, value)
