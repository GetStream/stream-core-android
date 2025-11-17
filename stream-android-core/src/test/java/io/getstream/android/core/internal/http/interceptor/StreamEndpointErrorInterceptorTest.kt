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

import io.getstream.android.core.api.model.exceptions.StreamEndpointErrorData
import io.getstream.android.core.api.model.exceptions.StreamEndpointException
import io.getstream.android.core.api.serialization.StreamJsonSerialization
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

internal class StreamEndpointErrorInterceptorTest {

    private val request = Request.Builder().url("https://example.test/path").build()

    private fun chainReturning(response: Response) =
        object : Interceptor.Chain {
            override fun call() = throw UnsupportedOperationException()

            override fun connectTimeoutMillis() = 0

            override fun proceed(request: Request): Response = response

            override fun connection(): Connection? = null

            override fun readTimeoutMillis() = 0

            override fun request(): Request = request

            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) =
                this

            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this

            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this

            override fun writeTimeoutMillis() = 0
        }

    @Test
    fun `successful response bypasses error handling`() {
        val response =
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("body".toResponseBody("text/plain".toMediaType()))
                .build()
        val interceptor = StreamEndpointErrorInterceptor(MockJsonSerialization())

        val result = interceptor.intercept(chainReturning(response))

        assertEquals(response, result)
    }

    @Test
    fun `unsuccessful response with parseable error returns api error`() {
        val error = StreamEndpointErrorData(code = 40, message = "Invalid token")
        val response =
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .body("{\"code\":40}".toResponseBody("application/json".toMediaType()))
                .build()
        val parser = MockJsonSerialization(result = Result.success(error))
        val interceptor = StreamEndpointErrorInterceptor(parser)

        val exception =
            assertFailsWith<StreamEndpointException> {
                interceptor.intercept(chainReturning(response))
            }

        assertEquals("Failed request: https://example.test/path", exception.message)
        assertEquals(error, exception.apiError)
    }

    @Test
    fun `unsuccessful response with parse failure propagates cause`() {
        val failure = IOException("boom")
        val response =
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message("Server Error")
                .body("oops".toResponseBody("text/plain".toMediaType()))
                .build()
        val parser = MockJsonSerialization(result = Result.failure(failure))
        val interceptor = StreamEndpointErrorInterceptor(parser)

        val exception =
            assertFailsWith<StreamEndpointException> {
                interceptor.intercept(chainReturning(response))
            }

        assertEquals("Failed request: https://example.test/path", exception.message)
        assertEquals(failure, exception.cause)
    }

    private class MockJsonSerialization(
        private val result: Result<StreamEndpointErrorData> =
            Result.failure(UnsupportedOperationException())
    ) : StreamJsonSerialization {
        override fun toJson(any: Any): Result<String> =
            Result.failure(UnsupportedOperationException())

        override fun <T : Any> fromJson(raw: String, clazz: Class<T>): Result<T> {
            @Suppress("UNCHECKED_CAST")
            return result as Result<T>
        }
    }
}
