package io.getstream.android.core.sample

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import androidx.lifecycle.lifecycleScope
import io.getstream.android.core.api.StreamClient
import io.getstream.android.core.api.authentication.StreamTokenProvider
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
import kotlin.coroutines.coroutineContext

class SampleApp : Application() {

    lateinit var streamClient: StreamClient
    private val userId = StreamUserId.fromString("sample-user")
    private val token = StreamToken.fromString(
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoicGV0YXIifQ.mZFi4iSblaIoyo9JDdcxIkGkwI-tuApeSBawxpz42rs"
    )
    private val coroutinesScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + Dispatchers.Main + Dispatchers.Default)

    companion object {
        lateinit var instance: SampleApp
    }

    @SuppressLint("NotKeepingInstance")
    override fun onCreate() {
        super.onCreate()
        instance = this
        streamClient = StreamClient(
            context = this.applicationContext,
            scope = coroutinesScope,
            apiKey = StreamApiKey.fromString("pd67s34fzpgw"),
            userId = userId,
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