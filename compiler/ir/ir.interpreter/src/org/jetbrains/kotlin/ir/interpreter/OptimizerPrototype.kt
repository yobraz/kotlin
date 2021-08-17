/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.interpreter

import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.interpreter.state.Primitive

class OptimizerPrototype(irBuiltIns: IrBuiltIns) : PartialIrInterpreter(irBuiltIns) {
    override fun visitReturn(expression: IrReturn): IrExpression {
        val result = evaluateReturn(expression)
        if (result is Primitive<*> && !result.type.isArrayOrPrimitiveArray()) {
            expression.value = result.value.toIrConst(result.type, expression.startOffset, expression.endOffset)
            return expression
        }
        return super.defaultReturnHandler(expression, result)
    }
}