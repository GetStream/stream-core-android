/*
 * Copyright (c) 2014-2026 Stream.io Inc. All rights reserved.
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
import io.getstream.android.core.api.model.value.StreamWsUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StreamSocketConfigTest {

    private val apiKey = StreamApiKey.fromString("key")
    private val wsUrl = StreamWsUrl.fromString("wss://chat.stream.io")
    private val header = StreamHttpClientInfoHeader.create("product", "1.0", "android", 34, "pixel")

    @Test
    fun `anonymous config uses anonymous auth type`() {
        val config =
            StreamSocketConfig.anonymous(url = wsUrl, apiKey = apiKey, clientInfoHeader = header)

        assertEquals(wsUrl, config.url)
        assertEquals(apiKey, config.apiKey)
        assertEquals("anonymous", config.authType)
        assertEquals(header, config.clientInfoHeader)
    }

    @Test
    fun `custom config uses provided auth type and validates input`() {
        val customUrl = StreamWsUrl.fromString("wss://chat.stream.io/custom")
        val config =
            StreamSocketConfig.custom(
                url = customUrl,
                apiKey = apiKey,
                authType = "token",
                clientInfoHeader = header,
            )

        assertEquals("token", config.authType)

        assertFailsWith<IllegalArgumentException> {
            StreamSocketConfig.custom(wsUrl, apiKey, "", header)
        }
    }

    @Test
    fun `jwt config uses default operational params`() {
        val config = StreamSocketConfig.jwt(url = wsUrl, apiKey = apiKey, clientInfoHeader = header)

        assertEquals("jwt", config.authType)
        assertEquals(StreamSocketConfig.DEFAULT_HEALTH_INTERVAL_MS, config.healthCheckIntervalMs)
        assertEquals(StreamSocketConfig.DEFAULT_LIVENESS_MS, config.livenessThresholdMs)
        assertEquals(StreamSocketConfig.DEFAULT_CONNECTION_TIMEOUT_MS, config.connectionTimeoutMs)
        assertEquals(StreamSocketConfig.DEFAULT_AGGREGATION_THRESHOLD, config.aggregationThreshold)
        assertEquals(
            StreamSocketConfig.DEFAULT_AGGREGATION_MAX_WINDOW_MS,
            config.aggregationMaxWindowMs,
        )
        assertEquals(
            StreamSocketConfig.DEFAULT_AGGREGATION_DISPATCH_QUEUE_CAPACITY,
            config.aggregationDispatchQueueCapacity,
        )
    }

    @Test
    fun `jwt config accepts custom operational params`() {
        val config =
            StreamSocketConfig.jwt(
                url = wsUrl,
                apiKey = apiKey,
                clientInfoHeader = header,
                healthCheckIntervalMs = 5_000L,
                livenessThresholdMs = 15_000L,
                connectionTimeoutMs = 2_000L,
                aggregationThreshold = 10,
                aggregationMaxWindowMs = 200L,
                aggregationDispatchQueueCapacity = 8,
            )

        assertEquals(5_000L, config.healthCheckIntervalMs)
        assertEquals(15_000L, config.livenessThresholdMs)
        assertEquals(2_000L, config.connectionTimeoutMs)
        assertEquals(10, config.aggregationThreshold)
        assertEquals(200L, config.aggregationMaxWindowMs)
        assertEquals(8, config.aggregationDispatchQueueCapacity)
    }
}
