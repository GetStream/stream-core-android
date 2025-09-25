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
@file:OptIn(StreamInternalApi::class)

package io.getstream.android.core.api.http

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.authentication.StreamTokenManager
import io.getstream.android.core.api.model.value.StreamApiKey
import io.getstream.android.core.api.model.value.StreamHttpClientInfoHeader
import io.getstream.android.core.api.serialization.StreamJsonSerialization
import io.getstream.android.core.api.socket.StreamConnectionIdHolder
import io.getstream.android.core.internal.http.interceptor.StreamApiKeyInterceptor
import io.getstream.android.core.internal.http.interceptor.StreamAuthInterceptor
import io.getstream.android.core.internal.http.interceptor.StreamClientInfoInterceptor
import io.getstream.android.core.internal.http.interceptor.StreamConnectionIdInterceptor
import io.getstream.android.core.internal.http.interceptor.StreamEndpointErrorInterceptor
import io.getstream.android.core.testutil.assertFieldEquals
import io.getstream.android.core.testutil.readPrivateField
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

internal class StreamOkHttpInterceptorsTest {

    @Test
    fun `auth factory wires type token manager and parser`() {
        // Given
        val authType = "jwt"
        val tokenManager = mockk<StreamTokenManager>(relaxed = true)
        val jsonParser = mockk<StreamJsonSerialization>(relaxed = true)

        // When
        val interceptor = StreamOkHttpInterceptors.auth(authType, tokenManager, jsonParser)

        // Then
        assertTrue(interceptor is StreamAuthInterceptor)
        interceptor.assertFieldEquals("authType", authType)
        interceptor.assertFieldEquals("tokenManager", tokenManager)
        interceptor.assertFieldEquals("jsonParser", jsonParser)
    }

    @Test
    fun `connectionId factory wires holder`() {
        // Given
        val holder = mockk<StreamConnectionIdHolder>(relaxed = true)

        // When
        val interceptor = StreamOkHttpInterceptors.connectionId(holder)

        // Then
        assertTrue(interceptor is StreamConnectionIdInterceptor)
        interceptor.assertFieldEquals("connectionIdHolder", holder)
    }

    @Test
    fun `clientInfo factory wires header`() {
        // Given
        val header =
            StreamHttpClientInfoHeader.create(
                product = "android",
                productVersion = "1.0",
                os = "Android",
                apiLevel = 33,
                deviceModel = "Pixel",
                app = "test-app",
                appVersion = "1.2.3",
            )

        // When
        val interceptor = StreamOkHttpInterceptors.clientInfo(header)

        // Then
        assertTrue(interceptor is StreamClientInfoInterceptor)
        val stored = interceptor.readPrivateField("clientInfo")
        when (stored) {
            is String -> assertEquals(header.rawValue, stored)
            else -> assertEquals(header, stored)
        }
    }

    @Test
    fun `apiKey factory wires key`() {
        // Given
        val apiKey = StreamApiKey.fromString("key123")

        // When
        val interceptor = StreamOkHttpInterceptors.apiKey(apiKey)

        // Then
        assertTrue(interceptor is StreamApiKeyInterceptor)
        val storedKey = interceptor.readPrivateField("apiKey")
        when (storedKey) {
            is String -> assertEquals(apiKey.rawValue, storedKey)
            else -> assertEquals(apiKey, storedKey)
        }
    }

    @Test
    fun `error factory wires json parser`() {
        // Given
        val jsonParser = mockk<StreamJsonSerialization>(relaxed = true)

        // When
        val interceptor = StreamOkHttpInterceptors.error(jsonParser)

        // Then
        assertTrue(interceptor is StreamEndpointErrorInterceptor)
        interceptor.assertFieldEquals("jsonParser", jsonParser)
    }
}
