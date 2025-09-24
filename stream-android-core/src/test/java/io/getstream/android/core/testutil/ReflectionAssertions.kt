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
package io.getstream.android.core.testutil

import kotlin.test.assertEquals
import kotlin.test.assertSame

/** Reads a private field named [fieldName] from [instance] (walking the class hierarchy). */
private fun readPrivateFieldInternal(instance: Any?, fieldName: String): Any? {
    val target = requireNotNull(instance) { "Cannot read '$fieldName' on null instance" }
    var current: Class<*>? = target::class.java
    while (current != null) {
        runCatching {
            val field = current.getDeclaredField(fieldName)
            field.isAccessible = true
            return field.get(target)
        }
        current = current.superclass
    }
    throw NoSuchFieldException(fieldName)
}

/** Extension wrapper over [readPrivateField] for ergonomic usage. */
public fun Any?.readPrivateField(fieldName: String): Any? = readPrivateFieldInternal(this, fieldName)

/**
 * Asserts that the private field [fieldName] equals [expected].
 * Value classes, primitives, numbers, booleans, and strings are compared with [assertEquals];
 * all other references are compared with [assertSame].
 */
public fun Any.assertFieldEquals(fieldName: String, expected: Any) {
    val value = readPrivateFieldInternal(this, fieldName)
    if (shouldUseEquals(expected)) {
        assertEquals(expected, value)
    } else {
        assertSame(expected, value)
    }
}

private fun shouldUseEquals(expected: Any): Boolean =
    expected::class.isValue ||
        expected::class.isData ||
        expected::class.javaPrimitiveType != null ||
        expected is String ||
        expected is Number ||
        expected is Boolean ||
        expected is List<*>
