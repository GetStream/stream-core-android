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

import io.getstream.android.core.annotations.StreamCoreApi
import io.getstream.android.core.api.model.value.StreamToken
import io.getstream.android.core.api.model.value.StreamUserId

/**
 * Supplies authentication tokens (JWTs) for a given user.
 *
 * Implementations usually call **your backend** to mint or fetch a signed, short-lived JWT for the
 * provided user ID. Never embed server secrets or mint tokens on-device.
 *
 * ### Contract
 * - Must return a valid, signed JWT for the requested [userId].
 * - May be invoked multiple times (e.g., initial connect, refresh); implement caching if
 *   appropriate.
 * - This is a `suspend` function; perform network/IO work without blocking and respect
 *   cancellation.
 * - Signal failures by throwing an exception (don’t return empty/placeholder tokens).
 *
 * ### Example
 *
 * ```kotlin
 * class MyTokenProvider(
 *     private val authApi: AuthApi
 * ) : StreamTokenProvider {
 *     override suspend fun loadToken(userId: String): StreamToken {
 *         // Network call to your service; may throw on failure
 *         val response = authApi.issueToken(userId)
 *         val streamToken = StreamToken.from(response.token)
 *         return streamToken
 *     }
 * }
 * ```
 *
 * ### Security
 * - Token creation/rotation must happen on your server.
 * - Prefer short expirations and refresh on demand.
 */
@StreamCoreApi
fun interface StreamTokenProvider {
    /**
     * Returns a JWT that authenticates the given [userId].
     *
     * Called by the SDK when establishing a connection and whenever a token refresh is needed.
     * Implementations may perform network requests and should be cancellable.
     *
     * **Note:** do not return a placeholder token or an empty string, throw an exception instead.
     *
     * @param userId The user identifier the token should be issued for.
     * @return A signed JWT string authorizing the user in the form of [StreamToken]
     * @throws Exception If the token cannot be obtained (network error, server error, invalid user,
     *   etc.).
     */
    suspend fun loadToken(userId: StreamUserId): StreamToken
}
