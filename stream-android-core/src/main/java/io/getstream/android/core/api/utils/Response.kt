package io.getstream.android.core.api.utils

import io.getstream.android.core.api.model.exceptions.StreamEndpointErrorData
import io.getstream.android.core.api.model.exceptions.StreamEndpointException
import io.getstream.android.core.api.serialization.StreamJsonSerialization
import okhttp3.Response

/**
 * Extracts the API error from the response body.
 *
 * @param with The JSON parser to use for parsing the response body.
 * @return The API error, or a failure if the response body could not be parsed.
 * @receiver The response to extract the error from.
 */
public fun Response.toErrorData(with: StreamJsonSerialization): Result<StreamEndpointErrorData> =
    runCatching {
        peekBody(Long.MAX_VALUE).string()
    }.flatMap {
        with.fromJson(it, StreamEndpointErrorData::class.java)
    }
