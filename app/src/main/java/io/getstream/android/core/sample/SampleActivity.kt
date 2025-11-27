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
import androidx.compose.material3.Button
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
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.model.connection.recovery.Recovery
import io.getstream.android.core.api.socket.listeners.StreamClientListener
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.sample.ui.ConnectionStateCard
import io.getstream.android.core.sample.ui.theme.StreamandroidcoreTheme
import kotlinx.coroutines.launch

class SampleActivity : ComponentActivity(), StreamClientListener {

    var handle: StreamSubscription? = null

    override fun onRecovery(recovery: Recovery) {
        super.onRecovery(recovery)
        Log.d("SampleActivity", "Recovery: $recovery")
    }

    override fun onError(err: Throwable) {
        super.onError(err)
        Log.e("SampleActivity", "Error: $err")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val streamClient = SampleApp.instance.streamClient
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) { streamClient.connect() }
        }
        if (handle == null) {
            handle =
                streamClient
                    .subscribe(
                        this,
                        options =
                            StreamSubscriptionManager.Options(
                                retention =
                                    StreamSubscriptionManager.Options.Retention.KEEP_UNTIL_CANCELLED
                            ),
                    )
                    .getOrThrow()
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
                        ClientInfo(streamClient = streamClient)
                        val state = streamClient.connectionState?.collectAsStateWithLifecycle()
                        val buttonState =
                            when (state?.value) {
                                is StreamConnectionState.Connected -> {
                                    Triple(
                                        "Disconnect",
                                        true,
                                        {
                                            lifecycleScope.launch { streamClient.disconnect() }
                                            Unit
                                        },
                                    )
                                }

                                is StreamConnectionState.Connecting -> {
                                    Triple("Connecting", false, { Unit })
                                }

                                else -> {
                                    Triple(
                                        "Connect",
                                        true,
                                        {
                                            lifecycleScope.launch { streamClient?.connect() }
                                            Unit
                                        },
                                    )
                                }
                            }
                        Button(onClick = buttonState.third, enabled = buttonState.second) {
                            Text(text = buttonState.first)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handle?.cancel()
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
    Log.d("SampleActivity", "Client state: ${state.value}")
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConnectionStateCard(state = state.value)
    }
}
