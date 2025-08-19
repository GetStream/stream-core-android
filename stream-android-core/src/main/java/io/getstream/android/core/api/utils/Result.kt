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

import io.getstream.android.core.annotations.StreamCoreApi
import kotlin.coroutines.cancellation.CancellationException

/**
 * Returns the result of applying the given [transform] function to the value of this [Result] if it
 * is [Result.isSuccess], or propagates the failure if it is [Result.isFailure].
 *
 * This is the "monadic bind" operation for [Result] — it allows chaining of operations where each
 * step returns a [Result], without nesting (avoids `Result<Result<R>>`).
 *
 * Unlike [flatMapCatching], this function does **not** catch exceptions thrown by [transform]:
 * - If [transform] throws any exception, including [CancellationException], it is rethrown.
 *
 * @param T The type of the success value in the receiver [Result].
 * @param R The type of the success value in the returned [Result].
 * @param transform A function to transform the success value into another [Result].
 * @return The [Result] returned by [transform] if this [Result] is successful, or the original
 *   failure if this [Result] is a failure.
 * @throws Throwable if [transform] throws, including [CancellationException].
 */
@StreamCoreApi
inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> =
    fold(onSuccess = { transform(it) }, onFailure = { Result.failure(it) })

/**
 * Returns the result of applying the given [transform] function to the value of this [Result] if it
 * is [Result.isSuccess], or propagates the failure if it is [Result.isFailure].
 *
 * This is similar to [flatMap], but **catches** any exception thrown by [transform] and wraps it in
 * a failed [Result], **except** for [CancellationException], which is always rethrown to preserve
 * coroutine cancellation.
 *
 * Behaves like [mapCatching], but for functions that return [Result] directly.
 *
 * @param T The type of the success value in the receiver [Result].
 * @param R The type of the success value in the returned [Result].
 * @param transform A function to transform the success value into another [Result]. If this
 *   function throws:
 *     - Any non-[CancellationException]: it is wrapped in a failed [Result].
 *     - A [CancellationException]: it is rethrown.
 *
 * @return The [Result] returned by [transform] if this [Result] is successful and [transform]
 *   completes normally, or a failed [Result] if [transform] throws a non-cancellation exception, or
 *   the original failure if this [Result] is a failure.
 * @throws CancellationException if either this [Result] is a failure with a [CancellationException]
 *   or [transform] throws one.
 */
@StreamCoreApi
inline fun <T, R> Result<T>.flatMapCatching(transform: (T) -> Result<R>): Result<R> =
    fold(
        onSuccess = { value ->
            try {
                transform(value)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Result.failure(e)
            }
        },
        onFailure = { e ->
            if (e is CancellationException) throw e
            Result.failure(e)
        },
    )
