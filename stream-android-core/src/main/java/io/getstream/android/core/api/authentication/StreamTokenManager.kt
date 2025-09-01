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
package io.getstream.android.core.api.authentication

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.model.value.StreamToken
import io.getstream.android.core.api.model.value.StreamUserId
import io.getstream.android.core.api.processing.StreamSingleFlightProcessor
import io.getstream.android.core.internal.authentication.StreamTokenManagerImpl
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages the SDK’s authentication token lifecycle.
 *
 * A `StreamTokenManager` exposes the current token as a hot [kotlinx.coroutines.flow.StateFlow] and
 * provides operations to supply a [StreamTokenProvider], set a token directly, and invalidate the
 * current token. Implementations are expected to cache the latest valid token and retrieve a new
 * one via the provider on demand.
 *
 * ### Semantics
 * - [token] emits the most recently known token, or `null` if no token is available.
 * - Calling [setProvider] registers the source from which new tokens will be obtained.
 * - Calling [setToken] sets the token immediately (useful when you already have one).
 * - Calling [invalidate] clears the current token; the next access may trigger a refresh if a
 *   provider has been set.
 *
 * ### Threading
 * Implementations should be safe to use from any thread. The [token] flow is hot and never
 * completes.
 *
 * ### Usage
 *
 * ```kotlin
 * val tokenManager = StreamTokenManager()
 * tokenManager.setProvider(MyTokenProvider())
 * tokenManager.token.value // null
 * ```
 */
@StreamInternalApi
public interface StreamTokenManager {
    /**
     * A hot stream of the current authentication token.
     *
     * Emits `null` when there is no token (e.g., before the first load or after [invalidate] is
     * called) and updates with each successful set or refresh.
     */
    public val token: StateFlow<StreamToken?>

    /**
     * Registers the provider used to load tokens on demand.
     *
     * Supplying a provider does not necessarily trigger an immediate load; the implementation may
     * fetch lazily or eagerly depending on its policy.
     *
     * @param provider The token provider to use for future refreshes.
     * @return [Result.success] on success, or [Result.failure] if the provider cannot be installed
     *   in the current state.
     */
    public fun setProvider(provider: StreamTokenProvider): Result<Unit>

    /**
     * Sets the current token explicitly.
     *
     * Use this when you already hold a freshly issued token and want to inject it directly. This
     * overrides any previously stored token.
     *
     * @param token The token to store as current.
     * @return [Result.success] on success, or [Result.failure] if the token is rejected by the
     *   implementation (e.g., invalid format).
     */
    public fun setToken(token: StreamToken): Result<Unit>

    /**
     * Invalidates and clears the current token.
     *
     * After invalidation, [token] will emit `null`. If a provider is set, the next operation that
     * requires a token may trigger a refresh.
     *
     * @return [Result.success] on success, or [Result.failure] if the token cannot be invalidated
     *   in the current state.
     */
    public fun invalidate(): Result<Unit>

    /**
     * Ensures that a valid token is available.
     *
     * If a token is already cached, it is returned immediately. Otherwise, the provider is invoked
     * to fetch a new token. This method does not block.
     *
     * @return [Result.success] containing the token, or [Result.failure] if no token is available
     *   and fetching failed.
     */
    public suspend fun loadIfAbsent(): Result<StreamToken>

    /**
     * Refreshes the current token from the provider.
     *
     * @return [Result.success] containing the token, or [Result.failure] if fetching failed.
     */
    public suspend fun refresh(): Result<StreamToken>
}

/**
 * Creates a [StreamTokenManager] instance.
 *
 * @param userId The user ID for which tokens are managed.
 * @param tokenProvider The provider used to load tokens on demand.
 * @param singleFlight The single-flight processor used to coordinate concurrent requests.
 * @return A new [StreamTokenManager] instance.
 */
@StreamInternalApi
public fun StreamTokenManager(
    userId: StreamUserId,
    tokenProvider: StreamTokenProvider,
    singleFlight: StreamSingleFlightProcessor,
): StreamTokenManager = StreamTokenManagerImpl(userId, tokenProvider, singleFlight)
