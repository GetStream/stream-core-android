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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.getstream.android.core.api.model.connection.StreamConnectedUser
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo

@Composable
internal fun TransportChip(label: String) {
    Box(
        modifier =
            Modifier
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(50),
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
internal fun NetworkFactRow(
    label: String,
    value: String,
    state: Boolean?,
    alert: Boolean = false,
) {
    val indicatorColor: Color =
        when (state) {
            true -> MaterialTheme.colorScheme.primary
            false -> MaterialTheme.colorScheme.error
            null -> MaterialTheme.colorScheme.outline
        }
    val baseValueColor =
        when (state) {
            true -> MaterialTheme.colorScheme.primary
            false -> MaterialTheme.colorScheme.error
            null -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val valueColor = if (alert) MaterialTheme.colorScheme.error else baseValueColor

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .background(indicatorColor, CircleShape),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

internal data class SignalViewData(val description: String, val progress: Float?)

internal fun StreamNetworkInfo.Snapshot.signalSummary(): SignalViewData {
    val level = signal.level()?.coerceIn(0, 4)
    val progress = level?.let { it / 4f }
    return SignalViewData(signal.summary(), progress)
}

private fun StreamNetworkInfo.Signal?.level(): Int? =
    when (this) {
        is StreamNetworkInfo.Signal.Wifi -> level0to4
        is StreamNetworkInfo.Signal.Cellular -> level0to4
        else -> null
    }

private fun StreamNetworkInfo.Signal?.summary(): String =
    when (this) {
        is StreamNetworkInfo.Signal.Wifi ->
            "Wi-Fi RSSI: ${rssiDbm ?: "?"} dBm"
        is StreamNetworkInfo.Signal.Cellular ->
            "Cellular ${rat ?: "Radio"} RSRP: ${rsrpDbm ?: "?"} dBm"
        is StreamNetworkInfo.Signal.Generic -> "Generic signal: $value"
        null -> "Signal data unavailable"
    }

internal fun Boolean?.toStatusValue(trueText: String, falseText: String): String =
    when (this) {
        true -> trueText
        false -> falseText
        null -> "Unknown"
    }

internal val StreamNetworkInfo.Metered.label: String
    get() =
        when (this) {
            StreamNetworkInfo.Metered.NOT_METERED -> "Unmetered"
            StreamNetworkInfo.Metered.TEMPORARILY_NOT_METERED -> "Temporarily unmetered"
            StreamNetworkInfo.Metered.UNKNOWN_OR_METERED -> "Metered"
        }

internal val StreamNetworkInfo.PriorityHint.label: String
    get() =
        when (this) {
            StreamNetworkInfo.PriorityHint.NONE -> "Balanced"
            StreamNetworkInfo.PriorityHint.LATENCY -> "Latency"
            StreamNetworkInfo.PriorityHint.BANDWIDTH -> "Bandwidth"
        }

internal fun StreamNetworkInfo.Transport.label(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercaseChar() }

internal fun connectionStatusLabel(state: StreamConnectionState): String =
    when (state) {
        StreamConnectionState.Idle -> "Idle"
        is StreamConnectionState.Connecting.Opening -> "Connecting"
        is StreamConnectionState.Connecting.Authenticating -> "Authenticating"
        is StreamConnectionState.Connected -> "Connected"
        is StreamConnectionState.Disconnected -> "Disconnected"
    }

internal fun connectionStatusState(state: StreamConnectionState): Boolean? =
    when (state) {
        StreamConnectionState.Idle -> null
        is StreamConnectionState.Connecting -> null
        is StreamConnectionState.Connected -> true
        is StreamConnectionState.Disconnected -> false
    }

internal fun StreamConnectedUser.displayName(): String = name ?: id
