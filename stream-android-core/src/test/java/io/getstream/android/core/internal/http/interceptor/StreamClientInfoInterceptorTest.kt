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
package io.getstream.android.core.internal.http.interceptor

import io.getstream.android.core.api.model.value.StreamHttpClientInfoHeader
import io.getstream.android.core.utils.boxWithReflection
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.Test

class StreamClientInfoInterceptorTest {
    @Test
    fun `adds X-Stream-Client header from provider`() {
        val clientInfo =
            boxWithReflection<StreamHttpClientInfoHeader>("product-1.2.3|os=Android 14")
        val interceptor = StreamClientInfoInterceptor(clientInfo)

        val req = Request.Builder().url("https://example.com/v1/ping").get().build()

        val proceeded = arrayOfNulls<Request>(1)
        val chain = mockChain(req) { proceeded[0] = it }

        val resp = interceptor.intercept(chain)
        assertTrue(resp.isSuccessful)

        val sent = proceeded[0]!!
        assertEquals("product-1.2.3|os=Android 14", sent.header("X-Stream-Client"))
    }

    @Test
    fun `appends header without clobbering existing X-Stream-Client`() {
        val clientInfo = boxWithReflection<StreamHttpClientInfoHeader>("from-interceptor")
        val interceptor = StreamClientInfoInterceptor(clientInfo)

        val req =
            Request.Builder()
                .url("https://example.com/v1/post")
                .header("X-Stream-Client", "preexisting")
                .post("body".toRequestBody("text/plain".toMediaType()))
                .build()

        val proceeded = arrayOfNulls<Request>(1)
        val chain = mockChain(req) { proceeded[0] = it }

        val resp = interceptor.intercept(chain)
        assertTrue(resp.isSuccessful)

        val sent = proceeded[0]!!
        assertEquals(listOf("preexisting", "from-interceptor"), sent.headers("X-Stream-Client"))
        assertEquals("POST", sent.method)
    }

    private fun mockChain(request: Request, onProceed: (Request) -> Unit): Interceptor.Chain =
        object : Interceptor.Chain {
            override fun request(): Request = request

            override fun proceed(request: Request): Response {
                onProceed(request)
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .build()
            }

            // Unused methods with safe defaults:
            override fun connection() = null

            override fun call() = throw UnsupportedOperationException()

            override fun connectTimeoutMillis() = 0

            override fun readTimeoutMillis() = 0

            override fun writeTimeoutMillis() = 0

            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) =
                this

            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this

            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }
}
