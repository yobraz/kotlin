/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.interpreter

import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.interpreter.checker.EvaluationMode
import org.jetbrains.kotlin.ir.interpreter.stack.CallStack
import org.jetbrains.kotlin.ir.interpreter.state.State
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

abstract class PartialIrInterpreter(val irBuiltIns: IrBuiltIns) : IrElementTransformerVoid() {
    private val environment = IrInterpreterEnvironment(irBuiltIns)
    internal val callStack: CallStack
        get() = environment.callStack
    private val interpreter = IrInterpreter(environment, emptyMap())

    internal fun evaluate(irExpression: IrExpression, args: List<State> = emptyList(), interpretOnly: Boolean = true) {
        callStack.safeExecute {
            interpreter.interpret(
                {
                    this.newSubFrame(irExpression)
                    this.pushInstruction(if (interpretOnly) SimpleInstruction(irExpression) else CompoundInstruction(irExpression))
                    args.reversed().forEach { this.pushState(it) }
                },
                { this.dropSubFrame() }
            )
        }
    }

    fun interpret(block: IrReturnableBlock): IrElement {
        callStack.newFrame(block)
        block.transformChildren(this, null)
        callStack.dropFrame()
        return block
    }

    private fun IrFunctionAccessExpression.getExpectedArgumentsCount(): Int {
        val dispatch = dispatchReceiver?.let { 1 } ?: 0
        val extension = extensionReceiver?.let { 1 } ?: 0
        return dispatch + extension + valueArgumentsCount
    }

    override fun visitCall(expression: IrCall): IrExpression {
        // TODO if has defaults create new function
        expression.dispatchReceiver = expression.dispatchReceiver?.transform(this, null)
        expression.extensionReceiver = expression.extensionReceiver?.transform(this, null)
        (0 until expression.valueArgumentsCount).forEach {
            expression.putValueArgument(it, expression.getValueArgument(it)?.transform(this, null))
        }

        val args = mutableListOf<State>()
        (0 until expression.valueArgumentsCount).forEach {
            args += callStack.tryToPopState() ?: return expression
        }
        expression.extensionReceiver?.let { args += callStack.tryToPopState() ?: return expression }
        expression.dispatchReceiver?.let { args += callStack.tryToPopState() ?: return expression }
        if (args.size != expression.getExpectedArgumentsCount()) return expression

        val owner = expression.symbol.owner
        if (EvaluationMode.ONLY_BUILTINS.canEvaluateFunction(owner, expression) || EvaluationMode.WITH_ANNOTATIONS.canEvaluateFunction(owner, expression)) {
            evaluate(expression, args, interpretOnly = true)
            // TODO if result is Primitive -> return const
            return expression
        }
        return expression
    }

//    private fun customVisitCall(expression: IrCall, receiver: State?, args: List<State>?): IrExpression {
//        TODO()
//    }

    override fun visitBlock(expression: IrBlock): IrExpression {
        callStack.newSubFrame(expression)
        expression.transformChildren(this, null)
        callStack.dropSubFrame()
        return expression
    }

    internal fun evaluateReturn(expression: IrReturn): State? {
        expression.value = expression.value.transform(this, null)
        return callStack.tryToPopState()
    }

    internal fun defaultReturnHandler(expression: IrReturn, returnValue: State?): IrExpression {
        return expression
    }

    override fun visitReturn(expression: IrReturn): IrExpression {
        // TODO if value was not calculated -> continue
        return defaultReturnHandler(expression, evaluateReturn(expression))
    }

//    override fun visitSetField(expression: IrSetField): IrExpression {
//        expression.value = expression.value.transform(this, null)
//        // TODO if value was not calculated -> return
//        return expression
//    }
//
//    override fun visitGetField(expression: IrGetField): IrExpression {
//        // TODO evaluate if possible
//        return expression
//    }

    override fun visitGetValue(expression: IrGetValue): IrExpression {
        // TODO evaluate can throw exception, how to avoid?
        evaluate(expression, interpretOnly = false)
        return expression
    }

    override fun <T> visitConst(expression: IrConst<T>): IrExpression {
        evaluate(expression)
        return expression
    }

    override fun visitVariable(declaration: IrVariable): IrStatement {
        if (declaration.initializer == null) {
            callStack.storeState(declaration.symbol, null)
        } else {
            declaration.initializer = declaration.initializer?.transform(this, null)
            callStack.tryToPopState()?.let { callStack.storeState(declaration.symbol, it) }
        }

        return declaration
    }
}
