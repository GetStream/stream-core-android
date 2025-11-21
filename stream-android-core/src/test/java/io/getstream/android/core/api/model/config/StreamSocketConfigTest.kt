package io.getstream.android.core.api.model.config

import io.getstream.android.core.api.model.value.StreamApiKey
import io.getstream.android.core.api.model.value.StreamHttpClientInfoHeader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class StreamSocketConfigTest {

    private val apiKey = StreamApiKey.fromString("key")
    private val header = StreamHttpClientInfoHeader.create("product", "1.0", "android", 34, "pixel")

    @Test
    fun `anonymous config uses anonymous auth type`() {
        val config = StreamSocketConfig.anonymous(
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
