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
package io.getstream.android.core.internal.serialization.moshi

import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.getstream.android.core.api.model.event.StreamClientWsEvent
import io.getstream.android.core.internal.model.events.StreamClientConnectedEvent
import io.getstream.android.core.internal.model.events.StreamClientConnectionErrorEvent
import io.getstream.android.core.internal.model.events.StreamHealthCheckEvent
import java.util.Date

internal class StreamCoreMoshiProvider {

    object DateMillisAdapter {
        @ToJson fun toJson(value: Date?): Long? = value?.time

        @FromJson fun fromJson(value: Long?): Date? = value?.let { Date(it) }
    }

    fun builder(configure: (Moshi.Builder) -> Unit): Moshi.Builder {
        val builder = Moshi.Builder()
        configure(builder)
        val moshiBuilder =
            builder.apply {
                add(KotlinJsonAdapterFactory())
                add(DateMillisAdapter)
                add(
                    PolymorphicJsonAdapterFactory.of(StreamClientWsEvent::class.java, "type")
                        .withSubtype(StreamClientConnectedEvent::class.java, "connection.ok")
                        .withSubtype(
                            StreamClientConnectionErrorEvent::class.java,
                            "connection.error",
                        )
                        .withSubtype(StreamHealthCheckEvent::class.java, "health.check")
                )
            }
        return moshiBuilder
    }
}
