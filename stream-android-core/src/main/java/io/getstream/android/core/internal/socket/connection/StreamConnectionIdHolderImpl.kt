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
package io.getstream.android.core.internal.socket.connection

import io.getstream.android.core.api.socket.StreamConnectionIdHolder
import java.util.concurrent.atomic.AtomicReference

internal class StreamConnectionIdHolderImpl : StreamConnectionIdHolder {
    private val connectionIdRef = AtomicReference<String?>(null)

    override fun clear(): Result<Unit> = runCatching { connectionIdRef.set(null) }

    override fun setConnectionId(connectionId: String): Result<String> = runCatching {
        if (connectionId.isBlank()) {
            throw IllegalArgumentException("Connection ID cannot be blank")
        }
        connectionIdRef.set(connectionId)
        connectionId
    }

    override fun getConnectionId(): Result<String?> = runCatching { connectionIdRef.get() }
}
