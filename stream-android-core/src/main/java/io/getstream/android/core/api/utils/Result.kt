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
import io.getstream.android.core.api.model.exceptions.StreamEndpointException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Returns the result of applying the given [transform] function to the value of this [Result] if it
 * is [Result.isSuccess], or propagates the failure if it is [Result.isFailure].
 *
 * This is the "monadic bind" operation for [Result] — it allows chaining of operations where each
 * step returns a [Result], without nesting (avoids `Result<Result<R>>`).
 *
 * @param T The type of the success value in the receiver [Result].
 * @param R The type of the success value in the returned [Result].
 * @param transform A function to transform the success value into another [Result].
 * @return The [Result] returned by [transform] if this [Result] is successful, or the original
 *   failure if this [Result] is a failure.
 * @throws Throwable if [transform] throws, including [CancellationException].
 */
@StreamInternalApi
public inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> =
    fold(onSuccess = { transform(it) }, onFailure = { Result.failure(it) })

/**
 * Invokes the given [function] if this [Result] is a failure and the error is a token error.
 *
 * @param function A function to invoke if this [Result] is a failure and the error is a token
 *   error.
 * @return This [Result] if it is a success, or the result of [function] if it is a failure and the
 *   error is a token error.
 */
@StreamInternalApi
public suspend fun <T> Result<T>.onTokenError(
    function: suspend (exception: StreamEndpointException, code: Int) -> Result<T>
): Result<T> = onFailure {
    if (it is StreamEndpointException && it.apiError?.code?.div(10) == 4) {
        function(it, it.apiError.code)
    } else {
        this
    }
}
