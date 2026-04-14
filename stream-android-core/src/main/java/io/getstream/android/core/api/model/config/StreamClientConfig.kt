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

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.log.StreamLoggerProvider

/**
 * Configuration for tuning [StreamClient][io.getstream.android.core.api.StreamClient] behaviour.
 *
 * All fields have sensible defaults. Pass an instance only when you need to override something.
 * Socket-specific tunables (URL, health timing, batching) belong in [StreamSocketConfig].
 *
 * ### Usage
 *
 * ```kotlin
 * // All defaults
 * val client = StreamClient(scope, context, user, tokenProvider, socketConfig, ...)
 *
 * // Custom logging and HTTP config
 * val client = StreamClient(
 *     ...,
 *     config = StreamClientConfig(
 *         logProvider = myLogProvider,
 *         httpConfig = StreamHttpConfig(builder),
 *     ),
 * )
 * ```
 *
 * @param logProvider Logger provider. Defaults to Android logcat.
 * @param httpConfig Optional HTTP client customization (OkHttp builder, interceptors).
 * @param serializationConfig Optional override for JSON and event serialization. When null, the
 *   factory builds a default config from the provided [productEventSerializer].
 */
@StreamInternalApi
public data class StreamClientConfig(
    val logProvider: StreamLoggerProvider = StreamLoggerProvider.defaultAndroidLogger(),
    val httpConfig: StreamHttpConfig? = null,
    val serializationConfig: StreamClientSerializationConfig? = null,
)
