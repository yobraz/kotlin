/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.lower.ANNOTATION_IMPLEMENTATION
import org.jetbrains.kotlin.backend.common.lower.AnnotationImplementationLowering
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.lower.FunctionReferenceLowering.Companion.javaClassReference
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isArray
import org.jetbrains.kotlin.ir.types.isKClass
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.findDeclaration
import org.jetbrains.kotlin.ir.util.isPrimitiveArray

internal val annotationImplementationPhase = makeIrFilePhase(
    ::JvmAnnotationImplementationLowering,
    name = "AnnotationImplementation",
    description = "Create synthetic annotations implementations and use them in annotations constructor calls"
)

class JvmAnnotationImplementationLowering(val jvmContext: JvmBackendContext): AnnotationImplementationLowering(jvmContext) {
    override fun IrType.kClassToJClassIfNeeded(): IrType {
        if (!this.isKClass()) return this
        return jvmContext.ir.symbols.javaLangClass.starProjectedType
    }

    override fun IrBuilderWithScope.kClassExprToJClassIfNeeded(irExpression: IrExpression): IrExpression {
        with(this) {
            return irGet(
                jvmContext.ir.symbols.javaLangClass.starProjectedType,
                null,
                jvmContext.ir.symbols.kClassJava.owner.getter!!.symbol
            ).apply {
                extensionReceiver = irExpression
            }
        }
    }

    override fun generatedEquals(irBuilder: IrBlockBodyBuilder, type: IrType, arg1: IrExpression, arg2: IrExpression): IrExpression {
        if (type.isArray() || type.isPrimitiveArray()) {
            val targetType = if (type.isPrimitiveArray()) type else jvmContext.ir.symbols.arrayOfAnyNType
            val requiredSymbol = jvmContext.ir.symbols.arraysClass.owner.findDeclaration<IrFunction> {
                it.name.asString() == "equals" && it.valueParameters.size == 2 && it.valueParameters.first().type == targetType
            }
            if (requiredSymbol != null) {
                return irBuilder.irCall(
                    requiredSymbol.symbol
                ).apply {
                    putValueArgument(0, arg1)
                    putValueArgument(1, arg2)
                }
            }
        }
        return super.generatedEquals(irBuilder, type, arg1, arg2)
    }

    override fun implementPlatformSpecificParts(annotationClass: IrClass, implClass: IrClass) {
        implClass.addFunction(
            name = "annotationType",
            returnType = jvmContext.ir.symbols.javaLangClass.starProjectedType,
            origin = ANNOTATION_IMPLEMENTATION,
            isStatic = false
        ).apply {
            body = jvmContext.createIrBuilder(symbol).irBlockBody {
                +irReturn(javaClassReference(annotationClass.defaultType, jvmContext))
            }
        }
    }
}
