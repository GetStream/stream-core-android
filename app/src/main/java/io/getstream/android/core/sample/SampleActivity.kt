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

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.getstream.android.core.api.StreamClient
import io.getstream.android.core.api.authentication.StreamTokenProvider
import io.getstream.android.core.api.model.connection.network.StreamNetworkState
import io.getstream.android.core.api.model.value.StreamApiKey
import io.getstream.android.core.api.model.value.StreamHttpClientInfoHeader
import io.getstream.android.core.api.model.value.StreamToken
import io.getstream.android.core.api.model.value.StreamUserId
import io.getstream.android.core.api.model.value.StreamWsUrl
import io.getstream.android.core.sample.client.createStreamClient
import io.getstream.android.core.sample.ui.ConnectionStateCard
import io.getstream.android.core.sample.ui.NetworkInfoCard
import io.getstream.android.core.sample.ui.theme.StreamandroidcoreTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SampleActivity : ComponentActivity() {

    val userId = StreamUserId.fromString("petar")
    var streamClient: StreamClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val streamClient2 =
            createStreamClient(
                context = this.applicationContext,
                scope = lifecycleScope,
                apiKey = StreamApiKey.fromString("pd67s34fzpgw"),
                userId = userId,
                wsUrl =
                    StreamWsUrl.fromString(
                        "wss://chat-edge-frankfurt-ce1.stream-io-api.com/api/v2/connect"
                    ),
                clientInfoHeader =
                    StreamHttpClientInfoHeader.create(
                        product = "android-core",
                        productVersion = "1.0.0",
                        os = "Android",
                        apiLevel = Build.VERSION.SDK_INT,
                        deviceModel = "Pixel 7 Pro",
                        app = "Stream Android Core Sample",
                        appVersion = "1.0.0",
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
        streamClient = streamClient2
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) { streamClient?.connect() }
        }
        enableEdgeToEdge()
        setContent {
            StreamandroidcoreTheme {
                val scrollState = rememberScrollState()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier =
                            Modifier.fillMaxSize()
                                .padding(innerPadding)
                                .verticalScroll(scrollState)
                                .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Greeting(name = "Android")
                        ClientInfo(streamClient = streamClient2)
                    }
                }
            }
        }
    }

    override fun onStop() {
        runBlocking { streamClient?.disconnect() }
        super.onStop()
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
fun ClientInfo(streamClient: StreamClient) {
    val state = streamClient.connectionState.collectAsStateWithLifecycle()
    val networkSnapshot = streamClient.networkState.collectAsStateWithLifecycle()
    Log.d("SampleActivity", "Client state: ${state.value}")
    val networkState = networkSnapshot.value
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConnectionStateCard(state = state.value)
        when (networkState) {
            is StreamNetworkState.Available -> {
                NetworkInfoCard(snapshot = networkState.snapshot)
            }
            else -> {
                NetworkInfoCard(snapshot = null)
            }
        }
    }
}
