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

import io.getstream.android.core.api.authentication.StreamTokenManager
import io.getstream.android.core.api.model.exceptions.StreamEndpointErrorData
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
import kotlin.test.assertTrue
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `token error with alreadyRetried header passes through without retry`() {
        val token = streamToken("stale")
        coEvery { tokenManager.loadIfAbsent() } returns Result.success(token)

        // Proper token error code handled by this interceptor
        val tokenError = tokenErrorData(40)
        every { json.fromJson(any(), StreamEndpointErrorData::class.java) } returns
            Result.success(tokenError)

        val interceptor = StreamAuthInterceptor(tokenManager, json, authType = "jwt")
        val client = client(interceptor)

        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"token invalid"}"""))

        val url = server.url("/v1/protected")

        client
            .newCall(
                Request.Builder()
                    .url(url)
                    .header("x-stream-retried-on-auth", "present") // simulate already retried
                    .build()
            )
            .execute()
            .use { resp ->
                assertFalse(resp.isSuccessful) // pass-through, no exception here
                assertEquals(401, resp.code)
            }

        val first = server.takeRequest(2, TimeUnit.SECONDS)
        kotlin.test.assertNotNull(first)
        kotlin.test.assertNull(server.takeRequest(300, TimeUnit.MILLISECONDS)) // no second try

        // No refresh/invalidate when header indicates we already retried
        coVerify(exactly = 1) { tokenManager.loadIfAbsent() }
        verify(exactly = 0) { tokenManager.invalidate() }
        coVerify(exactly = 0) { tokenManager.refresh() }
    }

    /** Non-token error codes are NOT handled here; pass response through without retry. */
    @Test
    fun `non-token error passes through without retry`() {
        val token = streamToken("t1")
        coEvery { tokenManager.loadIfAbsent() } returns Result.success(token)

        // e.g., business error code that is not 40/41/42
        val nonTokenError = tokenErrorData(13)
        every { json.fromJson(any(), StreamEndpointErrorData::class.java) } returns
            Result.success(nonTokenError)

        val interceptor = StreamAuthInterceptor(tokenManager, json, authType = "jwt")
        val client = client(interceptor)

        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"error":"validation"}"""))

        val url = server.url("/v1/endpoint")
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            assertFalse(resp.isSuccessful) // still an error; just passed along
            assertEquals(422, resp.code)
        }

        // No retry, no token refresh
        val req = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("t1", req.getHeader("Authorization"))
        kotlin.test.assertNull(server.takeRequest(300, TimeUnit.MILLISECONDS))

        verify(exactly = 0) { tokenManager.invalidate() }
        coVerify(exactly = 0) { tokenManager.refresh() }
    }

    /** If the error body cannot be parsed into StreamEndpointErrorData, pass through. */
    @Test
    fun `unparsable error body passes through without retry`() {
        val token = streamToken("t1")
        coEvery { tokenManager.loadIfAbsent() } returns Result.success(token)

        every { json.fromJson(any(), StreamEndpointErrorData::class.java) } returns
            Result.failure(IllegalStateException("bad json"))

        val interceptor = StreamAuthInterceptor(tokenManager, json, authType = "jwt")
        val client = client(interceptor)

        server.enqueue(MockResponse().setResponseCode(500).setBody("""<html>oops</html>"""))

        val url = server.url("/v1/boom")
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            assertFalse(resp.isSuccessful)
            assertEquals(500, resp.code)
        }

        // Consume the single request we expect
        val first = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("t1", first.getHeader("Authorization"))

        // Now verify there's no retry
        kotlin.test.assertNull(server.takeRequest(300, TimeUnit.MILLISECONDS))

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
