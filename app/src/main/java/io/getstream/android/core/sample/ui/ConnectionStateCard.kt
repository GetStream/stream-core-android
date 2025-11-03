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
package io.getstream.android.core.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.getstream.android.core.api.model.connection.StreamConnectedUser
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.sample.ui.theme.StreamandroidcoreTheme
import java.util.Date

@Composable
public fun ConnectionStateCard(state: StreamConnectionState) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Connection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            val statusLabel = connectionStatusLabel(state)
            val statusState = connectionStatusState(state)
            val statusAlert = statusState == false

            NetworkFactRow(
                label = "Status",
                value = statusLabel,
                state = statusState,
                alert = statusAlert,
            )

            when (state) {
                is StreamConnectionState.Connected -> {
                    Divider()
                    NetworkFactRow(
                        label = "User",
                        value = state.connectedUser.displayName(),
                        state = null,
                    )
                    NetworkFactRow(
                        label = "Connection ID",
                        value = state.connectionId,
                        state = null,
                    )
                }

                is StreamConnectionState.Connecting.Opening -> {
                    Divider()
                    NetworkFactRow(label = "Stage", value = "Opening socket", state = null)
                    NetworkFactRow(label = "User", value = state.userId, state = null)
                }

                is StreamConnectionState.Connecting.Authenticating -> {
                    Divider()
                    NetworkFactRow(label = "Stage", value = "Authenticating", state = null)
                    NetworkFactRow(label = "User", value = state.userId, state = null)
                }

                is StreamConnectionState.Disconnected -> {
                    Divider()
                    NetworkFactRow(
                        label = "Cause",
                        value = state.cause?.localizedMessage ?: "No details",
                        state = false,
                        alert = state.cause != null,
                    )
                }

                StreamConnectionState.Idle -> {
                    Divider()
                    NetworkFactRow(label = "Details", value = "Client idle", state = null)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionStateCardPreview() {
    StreamandroidcoreTheme {
        ConnectionStateCard(
            StreamConnectionState.Connected(
                connectedUser = sampleConnectedUser(),
                connectionId = "conn-1234",
            )
        )
    }
}

private fun sampleConnectedUser(): StreamConnectedUser =
    StreamConnectedUser(
        createdAt = Date(),
        id = "petar",
        language = "en",
        role = "user",
        updatedAt = Date(),
        teams = emptyList(),
        name = "Petar",
    )
