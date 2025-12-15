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

package io.getstream.android.core.sample

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import android.os.StrictMode
import io.getstream.android.core.api.StreamClient
import io.getstream.android.core.api.authentication.StreamTokenProvider
import io.getstream.android.core.api.model.StreamUser
import io.getstream.android.core.api.model.config.StreamClientSerializationConfig
import io.getstream.android.core.api.model.value.StreamApiKey
import io.getstream.android.core.api.model.value.StreamHttpClientInfoHeader
import io.getstream.android.core.api.model.value.StreamToken
import io.getstream.android.core.api.model.value.StreamUserId
import io.getstream.android.core.api.model.value.StreamWsUrl
import io.getstream.android.core.api.serialization.StreamEventSerialization
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SampleApp : Application() {

    lateinit var streamClient: StreamClient
    private val user = StreamUser(StreamUserId.fromString("sample-user"))
    private val token =
        StreamToken.fromString(
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoicGV0YXIifQ.mZFi4iSblaIoyo9JDdcxIkGkwI-tuApeSBawxpz42rs"
        )
    private val coroutinesScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        lateinit var instance: SampleApp
    }

    @SuppressLint("NotKeepingInstance")
    override fun onCreate() {
        super.onCreate()
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build()
        )
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        instance = this
        streamClient =
            StreamClient(
                context = this.applicationContext,
                scope = coroutinesScope,
                apiKey = StreamApiKey.fromString("pd67s34fzpgw"),
                user = user,
                products = listOf("feeds", "chat", "video"),
                wsUrl =
                    StreamWsUrl.fromString(
                        "wss://chat-edge-frankfurt-ce1.stream-io-api.com/api/v2/connect"
                    ),
                clientInfoHeader =
                    StreamHttpClientInfoHeader.create(
                        product = "android-core",
                        productVersion = "1.1.0",
                        os = "Android",
                        apiLevel = Build.VERSION.SDK_INT,
                        deviceModel = "Pixel 7 Pro",
                        app = "Stream Android Core Sample",
                        appVersion = "1.0.0",
                    ),
                tokenProvider =
                    object : StreamTokenProvider {
                        override suspend fun loadToken(userId: StreamUserId): StreamToken {
                            return token
                        }
                    },
                serializationConfig =
                    StreamClientSerializationConfig.default(
                        object : StreamEventSerialization<Unit> {
                            override fun serialize(data: Unit): Result<String> = Result.success("")

                            override fun deserialize(raw: String): Result<Unit> =
                                Result.success(Unit)
                        }
                    ),
            )
    }
}
