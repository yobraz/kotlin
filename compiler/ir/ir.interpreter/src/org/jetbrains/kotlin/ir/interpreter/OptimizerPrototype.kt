/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.interpreter

import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.interpreter.state.Primitive
import org.jetbrains.kotlin.ir.interpreter.state.asBoolean

class OptimizerPrototype(irBuiltIns: IrBuiltIns) : PartialIrInterpreter(irBuiltIns) {
    override fun visitReturn(expression: IrReturn): IrExpression {
        val result = evaluator.evalIrReturnValue(expression)
        if (result is Primitive<*> && !result.type.isArrayOrPrimitiveArray()) {
            expression.value = result.value.toIrConst(result.type, expression.startOffset, expression.endOffset)
            return expression
        }
        return evaluator.fallbackIrReturn(expression, result)
    }

    override fun visitWhen(expression: IrWhen): IrExpression {
        for ((i, branch) in expression.branches.withIndex()) {
            val condition = evaluator.evalIrBranchCondition(branch)
            when {
                condition == null -> return evaluator.fallbackIrWhen(expression, i, inclusive = false)
                condition.asBoolean() -> {
                    evaluator.evalIrBranchResult(branch)?.let { evaluator.callStack.pushState(it) }
                    return branch.result
                }
                // else -> ignore
            }
        }
        return expression
    }
}