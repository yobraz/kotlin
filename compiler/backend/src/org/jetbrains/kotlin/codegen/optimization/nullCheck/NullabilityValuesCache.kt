/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen.optimization.nullCheck

import org.jetbrains.kotlin.resolve.jvm.AsmTypes
import org.jetbrains.org.objectweb.asm.Type

class NullabilityValuesCache {
    private val nullables = HashMap<String, NullabilityValue>()
    private val notNulls = HashMap<String, NullabilityValue>()

    val notNullBooleanArray = notNull(Type.getType("[Z"))
    val notNullCharArray = notNull(Type.getType("[C"))
    val notNullByteArray = notNull(Type.getType("[B"))
    val notNullShortArray = notNull(Type.getType("[S"))
    val notNullIntArray = notNull(Type.getType("[I"))
    val notNullFloatArray = notNull(Type.getType("[F"))
    val notNullDoubleArray = notNull(Type.getType("[D"))
    val notNullLongArray = notNull(Type.getType("[J"))

    val notNullString = notNull(AsmTypes.JAVA_STRING_TYPE)
    val notNullClass = notNull(AsmTypes.JAVA_CLASS_TYPE)
    val notNullMethod = notNull(Type.getObjectType("java/lang/invoke/MethodType"))
    val notNullMethodHandle = notNull(Type.getObjectType("java/lang/invoke/MethodHandle"))

    val nullableObject = notNull(AsmTypes.OBJECT_TYPE)

    fun nullable(type: Type) =
        nullables.getOrPut(type.descriptor) {
            NullabilityValue.Nullable(type)
        }

    fun notNull(type: Type) =
        notNulls.getOrPut(type.descriptor) {
            NullabilityValue.NotNull(type)
        }
}