/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.interpreter

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

// TODO
// 1. cache.
// there can be many `lowerings` that are using compile time information.
// but this information will be the same every time, so there is no need to reinterpret all code
abstract class PartialIrInterpreter(val irBuiltIns: IrBuiltIns) : IrElementTransformerVoid() {
    internal val evaluator = Evaluator(irBuiltIns, this)

    fun interpret(block: IrReturnableBlock): IrElement {
        return evaluator.interpret(block)
    }

    override fun visitReturn(expression: IrReturn): IrExpression {
        return evaluator.fallbackIrReturn(
            expression,
            evaluator.evalIrReturnValue(expression)
        )
    }

    override fun visitCall(expression: IrCall): IrExpression {
        return evaluator.fallbackIrCall(
            expression,
            evaluator.evalIrCallDispatchReceiver(expression),
            evaluator.evalIrCallExtensionReceiver(expression),
            evaluator.evalIrCallArgs(expression)
        )
    }

    override fun visitConstructorCall(expression: IrConstructorCall): IrExpression {
        return evaluator.fallbackIrConstructorCall(
            expression,
            evaluator.evalIrCallDispatchReceiver(expression),
            evaluator.evalIrCallArgs(expression)
        )
    }

    override fun visitBlock(expression: IrBlock): IrExpression {
        return evaluator.fallbackIrBlock(expression)
    }

    override fun visitWhen(expression: IrWhen): IrExpression {
        return evaluator.fallbackIrWhen(expression)
    }

    override fun visitSetValue(expression: IrSetValue): IrExpression {
        return evaluator.fallbackIrSetValue(
            expression,
            evaluator.evalIrSetValue(expression)
        )
    }

    override fun visitGetValue(expression: IrGetValue): IrExpression {
        return evaluator.fallbackIrGetValue(
            expression,
            evaluator.evalIrGetValue(expression)
        )
    }

    override fun <T> visitConst(expression: IrConst<T>): IrExpression {
        return evaluator.fallbackIrConst(
            expression,
            evaluator.evalIrConst(expression)
        )
    }

    override fun visitVariable(declaration: IrVariable): IrStatement {
        return evaluator.fallbackIrVariable(
            declaration,
            evaluator.evalIrVariable(declaration)
        )
    }

    override fun visitGetField(expression: IrGetField): IrExpression {
        return evaluator.fallbackIrGetField(expression)
    }

    override fun visitSetField(expression: IrSetField): IrExpression {
        return evaluator.fallbackIrSetField(
            expression,
            evaluator.evalIrSetFieldValue(expression)
        )
    }
}