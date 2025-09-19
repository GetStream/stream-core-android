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
package io.getstream.android.core.api.utils

import io.getstream.android.core.api.model.exceptions.StreamEndpointErrorData
import io.getstream.android.core.api.serialization.StreamJsonSerialization
import io.mockk.every
import io.mockk.mockk
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert.*
import org.junit.Test

class ResponseTest {

    @Test
    fun `toErrorData returns success when parsing is successful`() {
        val jsonSerialization = mockk<StreamJsonSerialization>()
        val errorData = StreamEndpointErrorData(1000, "detail", "field")
        val responseBody = "{\"code\":\"code\",\"detail\":\"detail\",\"field\":\"field\"}"
        val response =
            Response.Builder()
                .code(400)
                .message("Bad Request")
                .request(okhttp3.Request.Builder().url("http://localhost").build())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .body(ResponseBody.create("application/json".toMediaTypeOrNull(), responseBody))
                .build()
        every { jsonSerialization.fromJson(any(), StreamEndpointErrorData::class.java) } returns
            Result.success(errorData)
        val result = response.toErrorData(jsonSerialization)
        assertTrue(result.isSuccess)
        assertEquals(errorData, result.getOrNull())
    }

    @Test
    fun `toErrorData returns failure when parsing throws`() {
        val jsonSerialization = mockk<StreamJsonSerialization>()
        val responseBody = "invalid json"
        val response =
            Response.Builder()
                .code(400)
                .message("Bad Request")
                .request(okhttp3.Request.Builder().url("http://localhost").build())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .body(ResponseBody.create("application/json".toMediaTypeOrNull(), responseBody))
                .build()
        every { jsonSerialization.fromJson(any(), StreamEndpointErrorData::class.java) } returns
            Result.failure(IllegalArgumentException("Parse error"))
        val result = response.toErrorData(jsonSerialization)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Parse error", result.exceptionOrNull()?.message)
    }
}
