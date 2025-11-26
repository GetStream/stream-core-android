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

package io.getstream.android.core.api.model.config

import io.getstream.android.core.api.model.value.StreamApiKey
import io.getstream.android.core.api.model.value.StreamHttpClientInfoHeader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StreamSocketConfigTest {

    private val apiKey = StreamApiKey.fromString("key")
    private val header = StreamHttpClientInfoHeader.create("product", "1.0", "android", 34, "pixel")

    @Test
    fun `anonymous config uses anonymous auth type`() {
        val config =
            StreamSocketConfig.anonymous(
                url = "wss://chat.stream.io",
                apiKey = apiKey,
                clientInfoHeader = header,
            )

        assertEquals("wss://chat.stream.io", config.url)
        assertEquals(apiKey, config.apiKey)
        assertEquals("anonymous", config.authType)
        assertEquals(header, config.clientInfoHeader)
    }

    @Test
    fun `custom config uses provided auth type and validates input`() {
        val config =
            StreamSocketConfig.custom(
                url = "wss://chat.stream.io/custom",
                apiKey = apiKey,
                authType = "token",
                clientInfoHeader = header,
            )

        assertEquals("token", config.authType)

        assertFailsWith<IllegalArgumentException> {
            StreamSocketConfig.custom("", apiKey, "jwt", header)
        }
        assertFailsWith<IllegalArgumentException> {
            StreamSocketConfig.custom("wss://chat.stream.io", apiKey, "", header)
        }
    }
}
