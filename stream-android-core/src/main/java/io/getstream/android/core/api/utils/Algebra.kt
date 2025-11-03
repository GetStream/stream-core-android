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

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.annotations.StreamPublishedApi
import io.getstream.android.core.api.model.exceptions.StreamAggregateException

/**
 * Operator overload for creating a [StreamAggregateException] from two [Throwable]s.
 *
 * @param other The other [Throwable] to combine with this one.
 * @return A [StreamAggregateException] containing both [Throwable]s.
 */
@StreamPublishedApi
public operator fun Throwable.plus(other: Throwable): StreamAggregateException {
    val message = "Multiple errors occurred. (${this.message}, ${other.message})"
    return if (this is StreamAggregateException && other is StreamAggregateException) {
        StreamAggregateException(message, causes + other.causes)
    } else if (this is StreamAggregateException) {
        StreamAggregateException(message, causes + other)
    } else if (other is StreamAggregateException) {
        StreamAggregateException(message, listOf(this) + other.causes)
    } else {
        StreamAggregateException(message, listOf(this, other))
    }
}

/**
 * Operator overload for creating a [Result] of a pair from two [Result]s.
 *
 * @param other The other [Result] to combine with this one.
 * @return A [Result] containing a pair of the values from this and the other [Result], or a
 *   [StreamAggregateException] if either [Result] is a failure.
 */
@StreamInternalApi
public operator fun <T, R> Result<T>.times(other: Result<R>): Result<Pair<T, R>> {
    when {
        this.isFailure && other.isFailure -> {
            return Result.failure(this.exceptionOrNull()!! + other.exceptionOrNull()!!)
        }

        this.isFailure -> return Result.failure(this.exceptionOrNull()!!)
        other.isFailure -> return Result.failure(other.exceptionOrNull()!!)
    }
    return Result.success(this.getOrThrow() to other.getOrThrow())
}
