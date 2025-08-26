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
    val configuredInterceptors: Set<Interceptor> = emptySet(),
)
