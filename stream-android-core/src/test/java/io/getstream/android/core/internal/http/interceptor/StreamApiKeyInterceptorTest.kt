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
package io.getstream.android.core.internal.http.interceptor

import io.getstream.android.core.api.model.exceptions.StreamClientException
import io.getstream.android.core.api.model.value.StreamApiKey
import io.getstream.android.core.utils.boxWithReflection
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.Test

class StreamApiKeyInterceptorTest {

    @Test
    fun `appends api_key when missing and non-blank`() {
        val apiKey: StreamApiKey = StreamApiKey.fromString("key123")
        val interceptor = StreamApiKeyInterceptor(apiKey)

        val request = Request.Builder().url("https://example.com/v1/resources?x=1").build()

        val chain = mockChain(request)
        interceptor.intercept(chain)

        val captured = captureRequest(chain)
        val url = captured.url
        assertEquals("key123", url.queryParameter("api_key"))
        assertEquals("1", url.queryParameter("x"))
    }

    @Test
    fun `throws when api_key already present in url`() {
        val apiKey: StreamApiKey = StreamApiKey.fromString("key123")
        val request =
            Request.Builder().url("https://example.com/v1/resources?api_key=existing").build()

        val interceptor = StreamApiKeyInterceptor(apiKey)
        val chain = mockChain(request)

        assertFailsWith<StreamClientException> { interceptor.intercept(chain) }
    }

    @Test
    fun `throws when api key is blank`() {
        val apiKey: StreamApiKey = boxWithReflection("")
        val request = Request.Builder().url("https://example.com/v1/anything").build()

        val interceptor = StreamApiKeyInterceptor(apiKey)
        val chain = mockChain(request)

        assertFailsWith<StreamClientException> { interceptor.intercept(chain) }
    }

    // --- Helpers ---

    private fun mockChain(request: Request): Interceptor.Chain {
        val chain = mockk<Interceptor.Chain>(relaxed = true)
        every { chain.request() } returns request
        every { chain.proceed(any()) } returns mockk<Response>(relaxed = true)
        return chain
    }

    private fun captureRequest(chain: Interceptor.Chain): Request {
        val slot = slot<Request>()
        verify { chain.proceed(capture(slot)) }
        return slot.captured
    }
}
