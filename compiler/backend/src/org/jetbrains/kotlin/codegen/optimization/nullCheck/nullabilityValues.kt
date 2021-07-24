/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.codegen.optimization.nullCheck

import org.jetbrains.kotlin.resolve.jvm.AsmTypes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.tree.analysis.Value

enum class Nullability {
    NULL, NOT_NULL, NULLABLE
}

sealed class NullabilityValue(private val _size: Int, val nullability: Nullability) : Value {
    override fun getSize() = _size

    interface Ref {
        val type: Type
    }

    object Primitive1 : NullabilityValue(1, Nullability.NOT_NULL) {
        override fun toString(): String = "P1"
    }

    object Primitive2 : NullabilityValue(2, Nullability.NOT_NULL) {
        override fun toString(): String = "P2"
    }

    object Null : NullabilityValue(1, Nullability.NULL), Ref {
        override val type: Type
            get() = AsmTypes.OBJECT_TYPE

        override fun toString(): String = "NULL"
    }

    class NotNull(override val type: Type) : NullabilityValue(1, Nullability.NOT_NULL), Ref {
        override fun toString(): String =
            "NotNull:${type.descriptor}"

        override fun equals(other: kotlin.Any?): Boolean =
            this === other || other is NotNull && other.type == type

        override fun hashCode(): Int =
            type.hashCode()
    }

    class Nullable(override val type: Type) : NullabilityValue(1, Nullability.NULLABLE), Ref {
        override fun toString(): String =
            "Nullable:${type.descriptor}"

        override fun equals(other: kotlin.Any?): Boolean =
            this === other || other is Nullable && other.type == type

        override fun hashCode(): Int =
            type.hashCode() + 1
    }

    object Any : NullabilityValue(1, Nullability.NULLABLE) {
        override fun toString(): String =
            "ANY"
    }
}
