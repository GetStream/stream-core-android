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

import io.getstream.android.core.api.socket.StreamConnectionIdHolder
import io.getstream.android.core.internal.socket.connection.StreamConnectionIdHolderImpl
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class StreamConnectionIdInterceptorTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `adds connection_id when holder returns non-blank`() {
        val holder = StreamConnectionIdHolderImpl().apply { setConnectionId("abc123").getOrThrow() }
        val interceptor = StreamConnectionIdInterceptor(holder)
        val client = client(interceptor)
        server.enqueue(MockResponse().setResponseCode(200))
        val url = server.url("/v1/resources?x=1")

        client.newCall(Request.Builder().url(url).build()).execute()

        val recorded = server.takeRequest()
        val u = recorded.requestUrl!!
        assertEquals("abc123", u.queryParameter("connection_id"))
        assertEquals("1", u.queryParameter("x"))
    }

    @Test
    fun `skips when holder returns null (no id set)`() {
        val holder =
            StreamConnectionIdHolderImpl().apply {
                clear().getOrThrow() // ensure null
            }
        val interceptor = StreamConnectionIdInterceptor(holder)
        val client = client(interceptor)
        server.enqueue(MockResponse().setResponseCode(200))
        val url = server.url("/v1/resources?x=1")

        client.newCall(Request.Builder().url(url).build()).execute()

        val u = server.takeRequest().requestUrl!!
        assertNull(u.queryParameter("connection_id"))
        assertEquals("1", u.queryParameter("x"))
    }

    @Test
    fun `does not duplicate when connection_id already present`() {
        val holder = StreamConnectionIdHolderImpl().apply { setConnectionId("newId").getOrThrow() }
        val interceptor = StreamConnectionIdInterceptor(holder)
        val client = client(interceptor)
        server.enqueue(MockResponse().setResponseCode(200))
        val url = server.url("/v1/resources?connection_id=existing&y=2")

        client.newCall(Request.Builder().url(url).build()).execute()

        val u = server.takeRequest().requestUrl!!
        // Stays as existing, not replaced and not duplicated
        assertEquals("existing", u.queryParameter("connection_id"))
        assertEquals(1, u.queryParameterValues("connection_id").size)
        assertEquals("2", u.queryParameter("y"))
    }

    @Test
    fun `trims whitespace from holder value`() {
        // setConnectionId forbids blank, but allows values with surrounding spaces
        val holder =
            StreamConnectionIdHolderImpl().apply { setConnectionId("  id-42  ").getOrThrow() }
        val interceptor = StreamConnectionIdInterceptor(holder)
        val client = client(interceptor)
        server.enqueue(MockResponse().setResponseCode(200))
        val url = server.url("/v1/echo")

        client.newCall(Request.Builder().url(url).build()).execute()

        val u = server.takeRequest().requestUrl!!
        assertEquals("id-42", u.queryParameter("connection_id"))
    }

    @Test
    fun `holder getConnectionId is called exactly once per request`() {
        val calls = AtomicInteger(0)
        val holder =
            CountingConnectionIdHolder(initial = "only-once", onGet = { calls.incrementAndGet() })
        val interceptor = StreamConnectionIdInterceptor(holder)
        val client = client(interceptor)
        server.enqueue(MockResponse().setResponseCode(200))
        val url = server.url("/v1/check")

        client.newCall(Request.Builder().url(url).build()).execute()

        assertEquals(1, calls.get())
    }

    @Test
    fun `preserves method and body`() {
        val holder = StreamConnectionIdHolderImpl().apply { setConnectionId("abc").getOrThrow() }
        val interceptor = StreamConnectionIdInterceptor(holder)
        val client = client(interceptor)
        server.enqueue(MockResponse().setResponseCode(200))
        val url = server.url("/v1/post")

        val body = "hello-world".toRequestBody("text/plain".toMediaType())
        val req = Request.Builder().url(url).post(body).build()

        client.newCall(req).execute()

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("hello-world", recorded.body.readUtf8())
        val u = recorded.requestUrl!!
        assertEquals("abc", u.queryParameter("connection_id"))
    }

    @Test
    fun `when getConnectionId throws, request is not modified`() {
        val holder =
            CountingConnectionIdHolder(onGet = { throw RuntimeException("boom") }).apply {
                setConnectionId("abc").getOrThrow()
            }
        val interceptor = StreamConnectionIdInterceptor(holder)
        val client = client(interceptor)
        server.enqueue(MockResponse().setResponseCode(200))
        val url = server.url("/v1/resources?x=1")

        client.newCall(Request.Builder().url(url).build()).execute()

        val u = server.takeRequest().requestUrl!!
        assertNull(u.queryParameter("connection_id"))
        assertEquals("1", u.queryParameter("x"))
    }

    // --- Helpers ---

    private fun client(interceptor: StreamConnectionIdInterceptor): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(interceptor).build()

    /**
     * Minimal test double that counts getConnectionId() calls and delegates behavior to in-memory
     * state.
     */
    private class CountingConnectionIdHolder(
        initial: String? = null,
        private val onGet: (() -> Unit)? = null,
    ) : StreamConnectionIdHolder {
        @Volatile private var value: String? = initial

        override fun clear() = runCatching { value = null }

        override fun setConnectionId(connectionId: String) = runCatching {
            require(connectionId.isNotBlank()) { "Connection ID cannot be blank" }
            value = connectionId
            connectionId
        }

        override fun getConnectionId() = runCatching {
            onGet?.invoke()
            value
        }
    }
}
