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
package io.getstream.android.core.api.model.exceptions

import io.getstream.android.core.annotations.StreamCoreApi

/**
 * Base exception for all Stream client errors.
 *
 * Serves as the common parent for all exceptions thrown by Stream client components. You can catch
 * this type to handle all Stream-related errors in a generic way.
 *
 * @property message The error message associated with this exception.
 */
@StreamCoreApi
open class StreamClientException(message: String = "", cause: Throwable? = null) :
    Exception(message)

/**
 * Exception representing an error response from the Stream API.
 *
 * Typically thrown when a REST endpoint or WebSocket call returns an error status and includes
 * details about the failure in the API error payload.
 *
 * @property message The error message describing the failure.
 * @property apiError The structured error response returned by the Stream API, if available, or
 *   `null` otherwise.
 */
@StreamCoreApi
class StreamEndpointException(message: String = "", val apiError: StreamEndpointErrorData?) :
    StreamClientException(message)

/**
 * Exception representing multiple errors that occurred during a single operation.
 *
 * This is useful when a single logical operation results in multiple underlying failures, such as
 * parallel network requests or batch processing. It aggregates the individual exceptions for later
 * inspection.
 *
 * @property message A descriptive message about the failure.
 * @property causes The list of underlying exceptions that caused the failure.
 */
@StreamCoreApi
class StreamAggregateException(message: String = "", val causes: List<Throwable>) :
    StreamClientException(message)
