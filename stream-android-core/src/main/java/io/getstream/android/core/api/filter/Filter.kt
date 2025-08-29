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
import io.getstream.android.core.internal.filter.FilterOperator

/**
 * Base interface for filters used in Stream API operations.
 *
 * Filters are used to specify criteria for querying and retrieving data from Stream services. Each
 * filter implementation defines specific matching logic for different comparison operations.
 */
@StreamPublishedApi public sealed interface Filter<F : FilterField>

internal data class BinaryOperationFilter<F : FilterField, V : Any>(
    val operator: FilterOperator,
    val field: F,
    val value: V,
) : Filter<F>

internal data class CollectionOperationFilter<F : FilterField>(
    internal val operator: FilterOperator,
    val filters: Set<Filter<F>>,
) : Filter<F>

/** Converts a [Filter] instance to a request map suitable for API queries. */
@StreamInternalApi
public fun Filter<*>.toRequest(): Map<String, Any> =
    when (this) {
        is BinaryOperationFilter<*, *> -> mapOf(field.remote to mapOf(operator.remote to value))
        is CollectionOperationFilter<*> ->
            mapOf(operator.remote to filters.map(Filter<*>::toRequest))
    }
