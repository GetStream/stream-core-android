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

package io.getstream.android.core.api.model.connection.recovery

import io.getstream.android.core.annotations.StreamInternalApi

/**
 * Represents a connection recovery decision.
 *
 * @param T The type of the data that is passed to the recovery decision.
 */
@StreamInternalApi
public sealed class Recovery {

    /**
     * The connection should be reconnected.
     *
     * @param why The reason for the reconnect.
     * @param T The type of the data that is passed to the recovery decision.
     */
    public data class Connect<T>(val why: T) : Recovery()

    /**
     * The connection should be disconnected.
     *
     * @param why The reason for the disconnect.
     * @param T The type of the data that is passed to the recovery decision.
     */
    public data class Disconnect<T>(val why: T) : Recovery()

    /**
     * An error occurred while evaluating the recovery strategy.
     *
     * @property error The error that occurred.
     */
    public data class Error(val error: Throwable) : Recovery()
}
