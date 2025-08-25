package io.getstream.android.core.api.model.config

import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Configuration for HTTP clients in the Stream client.
 *
 * @param httpBuilder The HTTP client builder.
 * @param automaticInterceptors Whether to add automatic interceptors.
 * @param configuredInterceptors The configured interceptors.
 */
data class StreamHttpConfig(
    val httpBuilder: OkHttpClient.Builder,
    val automaticInterceptors: Boolean = true,
    val configuredInterceptors: Set<Interceptor> = emptySet()
)