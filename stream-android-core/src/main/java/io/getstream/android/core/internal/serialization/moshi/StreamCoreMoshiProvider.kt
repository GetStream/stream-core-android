/*
 * Copyright (c) 2014-2026 Stream.io Inc. All rights reserved.
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

package io.getstream.android.core.internal.serialization.moshi

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.getstream.android.core.api.model.event.StreamClientWsEvent
import io.getstream.android.core.internal.model.events.StreamClientConnectedEvent
import io.getstream.android.core.internal.model.events.StreamClientConnectionErrorEvent
import io.getstream.android.core.internal.model.events.StreamHealthCheckEvent
import java.util.Date

internal class StreamCoreMoshiProvider {
    /**
     * Adapter for [Date] fields on internal WS events.
     *
     * Writes epoch millis, so the wire format of outbound messages is unchanged. Reads leniently:
     * gateways send dates either as epoch millis (number) or as RFC3339/ISO-8601 strings — e.g. the
     * video coordinator's `connection.ok` carries `"created_at": "2026-07-06T07:47:26.592958Z"` at
     * `$.me.created_at` — so both encodings are accepted.
     */
    object LenientDateAdapter : JsonAdapter<Date>() {
        private val rfc3339 = Rfc3339DateJsonAdapter()

        override fun fromJson(reader: JsonReader): Date? =
            when (reader.peek()) {
                JsonReader.Token.NULL -> reader.nextNull()
                JsonReader.Token.NUMBER -> Date(reader.nextLong())
                JsonReader.Token.STRING -> rfc3339.fromJson(reader)
                else ->
                    throw JsonDataException(
                        "Expected a date as epoch millis or an RFC3339 string " +
                            "but was ${reader.peek()} at path ${reader.path}"
                    )
            }

        override fun toJson(writer: JsonWriter, value: Date?) {
            writer.value(value?.time)
        }
    }

    fun builder(configure: (Moshi.Builder) -> Unit): Moshi.Builder {
        val builder = Moshi.Builder()
        configure(builder)
        val moshiBuilder =
            builder.apply {
                add(KotlinJsonAdapterFactory())
                add(Date::class.java, LenientDateAdapter)
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
