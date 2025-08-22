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

import io.getstream.android.core.api.authentication.StreamTokenManager
import io.getstream.android.core.api.model.exceptions.StreamEndpointErrorData
import io.getstream.android.core.api.model.exceptions.StreamEndpointException
import io.getstream.android.core.api.model.value.StreamToken
import io.getstream.android.core.api.serialization.StreamJsonSerialization
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class StreamAuthInterceptorTest {

    private lateinit var server: MockWebServer

    @MockK lateinit var tokenManager: StreamTokenManager
    @MockK lateinit var json: StreamJsonSerialization

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `adds auth headers and succeeds without retry`() {
        val token = streamToken("t1")
        coEvery { tokenManager.loadIfAbsent() } returns Result.success(token)

        val interceptor = StreamAuthInterceptor(tokenManager, json, authType = "jwt")
        val client = client(interceptor)

        server.enqueue(MockResponse().setResponseCode(200))
        val url = server.url("/v1/ping")

        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            assertTrue(resp.isSuccessful)
        }

        val req = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("jwt", req.getHeader("stream-auth-type"))
        assertEquals("t1", req.getHeader("Authorization"))

        coVerify(exactly = 1) { tokenManager.loadIfAbsent() }
        verify(exactly = 0) { tokenManager.invalidate() }
        coVerify(exactly = 0) { tokenManager.refresh() }
    }

    @Test
    fun `refreshes once on 40 and retries with new token`() {
        val initial = streamToken("expired")
        val refreshed = streamToken("fresh")
        coEvery { tokenManager.loadIfAbsent() } returns Result.success(initial)
        every { tokenManager.invalidate() } returns Result.success(Unit)
        coEvery { tokenManager.refresh() } returns Result.success(refreshed)

        val errorData = tokenErrorData(40)
        every { json.fromJson(any(), StreamEndpointErrorData::class.java) } returns
            Result.success(errorData)

        val interceptor = StreamAuthInterceptor(tokenManager, json, authType = "jwt")
        val client = client(interceptor)

        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"token invalid"}"""))
        server.enqueue(MockResponse().setResponseCode(200))

        val url = server.url("/v1/protected")
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            assertTrue(resp.isSuccessful)
        }

        val firstReq = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("expired", firstReq.getHeader("Authorization"))

        val secondReq = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("fresh", secondReq.getHeader("Authorization"))
        assertEquals("present", secondReq.getHeader("x-stream-retried-on-auth"))

        coVerify(exactly = 1) { tokenManager.loadIfAbsent() }
        verify(exactly = 1) { tokenManager.invalidate() }
        coVerify(exactly = 1) { tokenManager.refresh() }
    }

    @Test
    fun `refreshes once on 41 and retries with new token`() {
        val initial = streamToken("expired")
        val refreshed = streamToken("fresh")
        coEvery { tokenManager.loadIfAbsent() } returns Result.success(initial)
        every { tokenManager.invalidate() } returns Result.success(Unit)
        coEvery { tokenManager.refresh() } returns Result.success(refreshed)

        val errorData = tokenErrorData(41)
        every { json.fromJson(any(), StreamEndpointErrorData::class.java) } returns
                Result.success(errorData)

        val interceptor = StreamAuthInterceptor(tokenManager, json, authType = "jwt")
        val client = client(interceptor)

        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"token invalid"}"""))
        server.enqueue(MockResponse().setResponseCode(200))

        val url = server.url("/v1/protected")
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            assertTrue(resp.isSuccessful)
        }

        val firstReq = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("expired", firstReq.getHeader("Authorization"))

        val secondReq = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("fresh", secondReq.getHeader("Authorization"))
        assertEquals("present", secondReq.getHeader("x-stream-retried-on-auth"))

        coVerify(exactly = 1) { tokenManager.loadIfAbsent() }
        verify(exactly = 1) { tokenManager.invalidate() }
        coVerify(exactly = 1) { tokenManager.refresh() }
    }

    @Test
    fun `refreshes once on 42 and retries with new token`() {
        val initial = streamToken("expired")
        val refreshed = streamToken("fresh")
        coEvery { tokenManager.loadIfAbsent() } returns Result.success(initial)
        every { tokenManager.invalidate() } returns Result.success(Unit)
        coEvery { tokenManager.refresh() } returns Result.success(refreshed)

        val errorData = tokenErrorData(42)
        every { json.fromJson(any(), StreamEndpointErrorData::class.java) } returns
                Result.success(errorData)

        val interceptor = StreamAuthInterceptor(tokenManager, json, authType = "jwt")
        val client = client(interceptor)

        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"token invalid"}"""))
        server.enqueue(MockResponse().setResponseCode(200))

        val url = server.url("/v1/protected")
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            assertTrue(resp.isSuccessful)
        }

        val firstReq = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("expired", firstReq.getHeader("Authorization"))

        val secondReq = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("fresh", secondReq.getHeader("Authorization"))
        assertEquals("present", secondReq.getHeader("x-stream-retried-on-auth"))

        coVerify(exactly = 1) { tokenManager.loadIfAbsent() }
        verify(exactly = 1) { tokenManager.invalidate() }
        coVerify(exactly = 1) { tokenManager.refresh() }
    }

    @Test
    fun `non-token error throws StreamEndpointException without retry`() {
        val token = streamToken("t1")
        coEvery { tokenManager.loadIfAbsent() } returns Result.success(token)

        val nonTokenError = tokenErrorData(422)
        every { json.fromJson(any(), StreamEndpointErrorData::class.java) } returns
            Result.success(nonTokenError)

        val interceptor = StreamAuthInterceptor(tokenManager, json, authType = "jwt")
        val client = client(interceptor)

        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"error":"unprocessable"}"""))

        val url = server.url("/v1/fail")
        val ex =
            assertFailsWith<StreamEndpointException> {
                client
                    .newCall(Request.Builder().url(url).build())
                    .execute()
                    .use { /* force execution */ }
            }
        assertTrue(ex.message!!.contains("Failed request"))

        // Consume the single (failed) request
        val first = server.takeRequest(2, TimeUnit.SECONDS)
        kotlin.test.assertNotNull(first, "Expected exactly one request to be sent")

        // Assert no second request (i.e., no retry)
        val second = server.takeRequest(300, TimeUnit.MILLISECONDS)
        kotlin.test.assertNull(second, "Interceptor should not retry on non-token errors")

        coVerify(exactly = 1) { tokenManager.loadIfAbsent() }
        io.mockk.verify(exactly = 0) { tokenManager.invalidate() }
        coVerify(exactly = 0) { tokenManager.refresh() }
    }

    @Test
    fun `unparseable error throws StreamEndpointException`() {
        val token = streamToken("t1")
        coEvery { tokenManager.loadIfAbsent() } returns Result.success(token)

        every { json.fromJson(any(), StreamEndpointErrorData::class.java) } returns
            Result.failure(IllegalStateException("parse error"))

        val interceptor = StreamAuthInterceptor(tokenManager, json, authType = "jwt")
        val client = client(interceptor)

        server.enqueue(MockResponse().setResponseCode(500).setBody("not-json"))

        val url = server.url("/v1/error")
        val ex =
            assertFailsWith<StreamEndpointException> {
                client.newCall(Request.Builder().url(url).build()).execute().use { /* consume */ }
            }
        assertTrue(ex.message!!.contains("Failed to serialize response error body"))

        coVerify(exactly = 1) { tokenManager.loadIfAbsent() }
        verify(exactly = 0) { tokenManager.invalidate() }
        coVerify(exactly = 0) { tokenManager.refresh() }
    }

    @Test
    fun `token error with alreadyRetried header does not retry again`() {
        val token = streamToken("stale")
        coEvery { tokenManager.loadIfAbsent() } returns Result.success(token)
        every { tokenManager.invalidate() } returns Result.success(Unit)

        val tokenError = tokenErrorData(401)
        every { json.fromJson(any(), StreamEndpointErrorData::class.java) } returns
            Result.success(tokenError)

        val interceptor = StreamAuthInterceptor(tokenManager, json, authType = "jwt")
        val client = client(interceptor)

        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"token invalid"}"""))

        val url = server.url("/v1/protected")
        val ex =
            assertFailsWith<StreamEndpointException> {
                client
                    .newCall(
                        Request.Builder()
                            .url(url)
                            .header(
                                "x-stream-retried-on-auth",
                                "present",
                            ) // simulate already retried
                            .build()
                    )
                    .execute()
                    .use { /* consume */ }
            }
        assertTrue(ex.message!!.contains("Failed request"))

        val first = server.takeRequest(2, TimeUnit.SECONDS)
        kotlin.test.assertNotNull(first)
        kotlin.test.assertNull(server.takeRequest(300, TimeUnit.MILLISECONDS))

        coVerify(exactly = 1) { tokenManager.loadIfAbsent() }
        verify(exactly = 0) { tokenManager.invalidate() }
        coVerify(exactly = 0) { tokenManager.refresh() }
    }

    // ----------------- Helpers -----------------

    private fun client(interceptor: Interceptor): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(interceptor).build()

    private fun streamToken(raw: String): StreamToken {
        val cls = Class.forName("io.getstream.android.core.api.model.value.StreamToken")
        val box = cls.getDeclaredMethod("box-impl", String::class.java)
        box.isAccessible = true
        return box.invoke(null, raw) as StreamToken
    }

    private fun tokenErrorData(code: Int): StreamEndpointErrorData =
        io.mockk.mockk<StreamEndpointErrorData>().also { every { it.code } returns code }
}
