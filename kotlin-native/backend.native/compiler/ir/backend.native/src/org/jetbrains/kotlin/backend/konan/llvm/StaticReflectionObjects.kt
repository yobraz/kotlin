/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.llvm

import kotlinx.cinterop.toByte
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal fun StaticData.createKTypeObject(type: IrType): ConstPointer {
    if (type !is IrSimpleType) throw NotImplementedError()

    val classifier = type.classifier as? IrClassSymbol ?: throw NotImplementedError()

    val kTypeProjectionListClass = context.ir.symbols.kTypeProjectionSpecialList.owner

    fun getIdByVariance(name: String) =
            kTypeProjectionListClass.companionObject()!!.declarations.asSequence()
                    .filterIsInstance<IrProperty>()
                    .singleOrNull { it.name.toString() == "VARIANCE_$name" }
                    ?.backingField?.initializer?.expression
                    ?.safeAs<IrConst<*>>()
                    ?.value as? Int
                    ?: throw IllegalStateException("KTypeProjection.companion should contain VARIANCE_$name int constant")

    val argumentVairance = mutableListOf<ConstValue>()
    val argumentType = mutableListOf<ConstValue>()
    for (argument in type.arguments) {
        val variance: ConstValue
        val projectionType: ConstValue
        when (argument) {
            is IrStarProjection -> {
                variance = Int32(getIdByVariance("STAR"))
                projectionType = NullPointer(kObjHeader)
            }
            is IrTypeProjection -> {
                variance = Int32(getIdByVariance(when (argument.variance) {
                    Variance.INVARIANT -> "INVARIANT"
                    Variance.IN_VARIANCE -> "IN"
                    Variance.OUT_VARIANCE -> "OUT"
                }))
                projectionType = kotlinTypeObject(argument.type)
            }
            else -> throw NotImplementedError()
        }
        argumentVairance.add(variance)
        argumentType.add(projectionType)
    }

    val arguments = createConstKotlinObject(context.ir.symbols.kTypeProjectionSpecialList.owner, mapOf(
            "variance" to createConstKotlinArray(context.ir.symbols.intArray.owner, argumentVairance),
            "type" to createConstKotlinArray(context.ir.symbols.array.owner, argumentType)
    ))

    return createConstKotlinObject(context.ir.symbols.kTypeImpl.owner, mapOf(
            "classifier" to createConstKotlinObject(context.ir.symbols.kClassImpl.owner, classifier.owner.typeInfoPtr),
            "arguments" to arguments,
            "isMarkedNullable" to Int1(type.isNullable().toByte())
    ))
}