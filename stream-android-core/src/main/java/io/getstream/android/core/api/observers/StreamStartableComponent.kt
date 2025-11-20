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
package io.getstream.android.core.api.observers

import io.getstream.android.core.annotations.StreamInternalApi

/**
 * Minimal lifecycle contract for components that can be started and stopped on demand.
 *
 * Consumers typically call [start] during initialization and [stop] when tearing down resources or
 * responding to lifecycle events.
 */
@StreamInternalApi
public interface StreamStartableComponent {

    /**
     * Starts the component.
     *
     * ### Example
     *
     * ```kotlin
     * subscriptionManager.start()
     *     .onFailure { cause -> logger.e(cause) { "Unable to start monitor" } }
     * ```
     *
     * @return `Result.success(Unit)` on success; `Result.failure(cause)` when startup fails.
     */
    public fun start(): Result<Unit>

    /**
     * Stops the component.
     *
     * ### Example
     *
     * ```kotlin
     * subscriptionManager.stop()
     *     .onFailure { cause -> logger.w(cause) { "Unable to stop monitor" } }
     * ```
     *
     * @return `Result.success(Unit)` on success; `Result.failure(cause)` when shutdown fails.
     */
    public fun stop(): Result<Unit>
}
