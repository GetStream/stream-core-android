package io.getstream.android.core.api.utils

import io.getstream.android.core.api.model.exceptions.StreamEndpointErrorData
import io.getstream.android.core.api.serialization.StreamJsonSerialization
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert.*
import org.junit.Test
import io.mockk.every
import io.mockk.mockk

class ResponseTest {

    @Test
    fun `toErrorData returns success when parsing is successful`() {
        val jsonSerialization = mockk<StreamJsonSerialization>()
        val errorData = StreamEndpointErrorData(1000, "detail", "field")
        val responseBody = "{\"code\":\"code\",\"detail\":\"detail\",\"field\":\"field\"}"
        val response = Response.Builder()
            .code(400)
            .message("Bad Request")
            .request(okhttp3.Request.Builder().url("http://localhost").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .body(ResponseBody.create("application/json".toMediaTypeOrNull(), responseBody))
            .build()
        every { jsonSerialization.fromJson(any(), StreamEndpointErrorData::class.java) } returns Result.success(errorData)
        val result = response.toErrorData(jsonSerialization)
        assertTrue(result.isSuccess)
        assertEquals(errorData, result.getOrNull())
    }

    @Test
    fun `toErrorData returns failure when parsing throws`() {
        val jsonSerialization = mockk<StreamJsonSerialization>()
        val responseBody = "invalid json"
        val response = Response.Builder()
            .code(400)
            .message("Bad Request")
            .request(okhttp3.Request.Builder().url("http://localhost").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .body(ResponseBody.create("application/json".toMediaTypeOrNull(), responseBody))
            .build()
        every { jsonSerialization.fromJson(any(), StreamEndpointErrorData::class.java) } returns Result.failure(IllegalArgumentException("Parse error"))
        val result = response.toErrorData(jsonSerialization)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Parse error", result.exceptionOrNull()?.message)
    }
}
