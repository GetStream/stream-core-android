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
package io.getstream.android.core.api.subscribe

import io.getstream.android.core.annotations.StreamPublishedApi

/**
 * Handle returned by every **Stream** `subscribe(…)` call.
 *
 * Holding a reference to this object keeps the underlying listener / flow active. Call [cancel]
 * when you no longer need updates to:
 * * stop receiving events,
 * * free any related resources (coroutine, network socket, etc.),
 * * avoid memory leaks.
 *
 * Instances are **single-shot**: once you invoke [cancel] the subscription is permanently
 * terminated and calling `cancel()` again has no effect.
 */
@StreamPublishedApi
public interface StreamSubscription {
    /**
     * Terminates this subscription.
     *
     * After the call:
     * * No further events will be delivered.
     * * The implementation should release all resources associated with the subscription.
     *
     * The operation is idempotent; invoking it multiple times is safe.
     */
    public fun cancel()
}
