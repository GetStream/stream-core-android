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

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.annotations.StreamPublishedApi
import io.getstream.android.core.internal.filter.BinaryOperator
import io.getstream.android.core.internal.filter.CollectionOperator
import io.getstream.android.core.internal.filter.FilterOperations

/**
 * Base interface for filters used in Stream API operations.
 *
 * Filters are used to specify criteria for querying and retrieving data from Stream services. Each
 * filter implementation defines specific matching logic for different comparison operations.
 */
@StreamPublishedApi public sealed interface Filter<M, F : FilterField<M>>

internal data class BinaryOperationFilter<M, F : FilterField<M>>(
    val operator: BinaryOperator,
    val field: F,
    val value: Any,
) : Filter<M, F>

internal data class CollectionOperationFilter<M, F : FilterField<M>>(
    internal val operator: CollectionOperator,
    val filters: Set<Filter<M, F>>,
) : Filter<M, F>

/** Converts a [Filter] instance to a request map suitable for API queries. */
@StreamInternalApi
public fun Filter<*, *>.toRequest(): Map<String, Any> =
    when (this) {
        is BinaryOperationFilter<*, *> -> mapOf(field.remote to mapOf(operator.remote to value))
        is CollectionOperationFilter<*, *> ->
            mapOf(operator.remote to filters.map(Filter<*, *>::toRequest))
    }

/** Checks if this filter matches the given item. */
@StreamInternalApi
public infix fun <M, F : FilterField<M>> Filter<M, F>.matches(item: M): Boolean =
    when (this) {
        is BinaryOperationFilter<M, F> -> {
            val fieldValue = field.localValue(item)
            val filterValue = value
            val notNull = fieldValue != null

            with(FilterOperations) {
                when (operator) {
                    BinaryOperator.EQUAL -> notNull && fieldValue == filterValue
                    BinaryOperator.GREATER -> notNull && fieldValue greater filterValue
                    BinaryOperator.LESS -> notNull && fieldValue less filterValue
                    BinaryOperator.GREATER_OR_EQUAL ->
                        notNull && fieldValue greaterOrEqual filterValue
                    BinaryOperator.LESS_OR_EQUAL -> notNull && fieldValue lessOrEqual filterValue
                    BinaryOperator.IN -> notNull && fieldValue `in` filterValue
                    BinaryOperator.QUERY -> notNull && search(filterValue, where = fieldValue)
                    BinaryOperator.AUTOCOMPLETE -> notNull && fieldValue autocompletes filterValue
                    BinaryOperator.EXISTS -> fieldValue exists filterValue
                    BinaryOperator.CONTAINS -> notNull && fieldValue contains filterValue
                    BinaryOperator.PATH_EXISTS -> notNull && fieldValue containsPath filterValue
                }
            }
        }

        is CollectionOperationFilter<M, F> -> {
            when (operator) {
                CollectionOperator.AND -> filters.all { it.matches(item) }
                CollectionOperator.OR -> filters.any { it.matches(item) }
            }
        }
    }
