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
package io.getstream.android.core.sample

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.getstream.android.core.api.StreamClient
import io.getstream.android.core.api.authentication.StreamTokenProvider
import io.getstream.android.core.api.model.config.StreamEndpointConfig
import io.getstream.android.core.api.model.value.StreamApiKey
import io.getstream.android.core.api.model.value.StreamHttpClientInfoHeader
import io.getstream.android.core.api.model.value.StreamToken
import io.getstream.android.core.api.model.value.StreamUserId
import io.getstream.android.core.api.state.StreamClientState
import io.getstream.android.core.sample.ui.theme.StreamandroidcoreTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SampleActivity : ComponentActivity() {
    val streamClient =
        StreamClient(
            apiKey = StreamApiKey.fromString("pd67s34fzpgw"),
            userId = StreamUserId.fromString("petar"),
            scope = lifecycleScope,
            endpoint =
                StreamEndpointConfig(
                    httpUrl = "https://chat-edge-frankfurt-ce1.stream-io-api.com",
                    wsUrl = "wss://chat-edge-frankfurt-ce1.stream-io-api.com/api/v2/connect",
                    clientInfoHeader =
                        StreamHttpClientInfoHeader.create(
                            product = "feeds-android-sdk",
                            productVersion = "0.0.1",
                            app = "Sample App",
                            appVersion = "1.0",
                            os = "Android ${Build.VERSION.RELEASE}",
                            apiLevel = Build.VERSION.SDK_INT,
                            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                        ),
                ),
            tokenProvider =
                object : StreamTokenProvider {
                    override suspend fun loadToken(userId: StreamUserId): StreamToken {
                        return StreamToken.fromString(
                            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoicGV0YXIifQ.mZFi4iSblaIoyo9JDdcxIkGkwI-tuApeSBawxpz42rs"
                        )
                    }
                },
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) { streamClient.connect() }
        }
        enableEdgeToEdge()
        setContent {
            StreamandroidcoreTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column {
                        Greeting(name = "Android", modifier = Modifier.padding(innerPadding))
                        ClientInfo(streamClient = streamClient)
                    }
                }
            }
        }
    }

    override fun onPause() {
        runBlocking { streamClient.disconnect() }
        super.onPause()
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    StreamandroidcoreTheme { Greeting("Android") }
}

@Composable
fun ClientInfo(streamClient: StreamClient<StreamClientState>) {
    val state = streamClient.state.connectionState.collectAsStateWithLifecycle()
    Text(text = "Client state: ${state.value}")
}
