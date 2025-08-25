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
package io.getstream.android.core.internal.authentication

import io.getstream.android.core.api.authentication.StreamTokenManager
import io.getstream.android.core.api.authentication.StreamTokenProvider
import io.getstream.android.core.api.model.StreamTypedKey.Companion.randomExecutionKey
import io.getstream.android.core.api.model.value.StreamToken
import io.getstream.android.core.api.model.value.StreamUserId
import io.getstream.android.core.api.processing.StreamSingleFlightProcessor
import io.getstream.android.core.api.utils.runCatchingCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class StreamTokenManagerImpl(
    private val userId: StreamUserId,
    private var tokenProvider: StreamTokenProvider,
    private val singleFlight: StreamSingleFlightProcessor,
) : StreamTokenManager {
    private val refreshKey = randomExecutionKey<StreamToken>()
    private var cachedToken: MutableStateFlow<StreamToken?> = MutableStateFlow(null)
    override val token: StateFlow<StreamToken?> = cachedToken.asStateFlow()

    override fun setProvider(provider: StreamTokenProvider): Result<Unit> = runCatching {
        this.tokenProvider = provider
    }

    override fun setToken(token: StreamToken): Result<Unit> = runCatching {
        this.cachedToken.value = token
    }

    override fun invalidate(): Result<Unit> = runCatching { this.cachedToken.value = null }

    override suspend fun loadIfAbsent(): Result<StreamToken> = runCatchingCancellable {
        val token = cachedToken.value
        if (singleFlight.has(refreshKey) || token == null) {
            refresh().getOrThrow()
        } else {
            token
        }
    }

    override suspend fun refresh(): Result<StreamToken> =
        singleFlight.run(refreshKey) {
            val newToken = tokenProvider.loadToken(userId)
            this.cachedToken.value = newToken
            return@run newToken
        }
}
