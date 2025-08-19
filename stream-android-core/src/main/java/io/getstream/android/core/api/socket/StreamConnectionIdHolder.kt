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
package io.getstream.android.core.api.socket

import io.getstream.android.core.annotations.StreamCoreApi

/**
 * Holds and manages the connection ID for the Stream client.
 *
 * Implementations are responsible for storing the connection ID in a thread-safe manner and
 * exposing it to other parts of the SDK.
 *
 * All methods return a [Result] to allow explicit error handling. On success, the [Result] contains
 * the expected value (or `null` if no connection ID is set). On failure, the [Result] contains the
 * relevant [Throwable].
 */
@StreamCoreApi
interface StreamConnectionIdHolder {

    /**
     * Clears the stored connection ID.
     *
     * @return A [Result] indicating success, or containing an error if the operation fails.
     */
    fun clear(): Result<Unit>

    /**
     * Stores the given connection ID.
     *
     * @param connectionId The connection ID to store.
     * @return A [Result] containing the stored connection ID if successful, or an error if the
     *   operation fails.
     */
    fun setConnectionId(connectionId: String): Result<String>

    /**
     * Retrieves the stored connection ID.
     *
     * @return A [Result] containing the connection ID if available, `null` if none is set, or an
     *   error if the operation fails.
     */
    fun getConnectionId(): Result<String?>
}
