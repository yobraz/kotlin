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
import org.jetbrains.kotlin.ir.interpreter.state.isUnit
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

internal class Evaluator(val irBuiltIns: IrBuiltIns, val transformer: IrElementTransformerVoid) {
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
                    args.forEach { this.pushState(it) }
                },
                { this.dropSubFrame() }
            )
        }
    }

    fun interpret(block: IrReturnableBlock): IrElement {
        callStack.newFrame(block)
        fallbackIrStatements(block.statements)
        callStack.dropFrame()
        return block
    }

    private fun IrFunctionAccessExpression.getExpectedArgumentsCount(): Int {
        val dispatch = dispatchReceiver?.let { 1 } ?: 0
        val extension = extensionReceiver?.let { 1 } ?: 0
        return dispatch + extension + valueArgumentsCount
    }

    fun evalIrReturnValue(expression: IrReturn): State? {
        expression.value = expression.value.transform(transformer, null)
        return callStack.tryToPopState()
    }

    fun fallbackIrReturn(expression: IrReturn, value: State?): IrReturn {
        return expression
    }

    fun evalIrCallDispatchReceiver(expression: IrFunctionAccessExpression): State? {
        expression.dispatchReceiver = expression.dispatchReceiver?.transform(transformer, null)
        return callStack.tryToPopState()
    }

    fun evalIrCallExtensionReceiver(expression: IrFunctionAccessExpression): State? {
        expression.extensionReceiver = expression.extensionReceiver?.transform(transformer, null)
        return callStack.tryToPopState()
    }

    fun evalIrCallArgs(expression: IrFunctionAccessExpression): List<State?> {
        (0 until expression.valueArgumentsCount).forEach {
            expression.putValueArgument(it, expression.getValueArgument(it)?.transform(transformer, null))
        }
        val args = mutableListOf<State?>()
        (0 until expression.valueArgumentsCount).forEach {
            args += callStack.tryToPopState()
        }
        return args
    }

    fun fallbackIrCall(expression: IrCall, dispatchReceiver: State?, extensionReceiver: State?, args: List<State?>): IrCall {
        val actualArgs = listOf(dispatchReceiver, extensionReceiver, *args.toTypedArray()).filterNotNull()
        if (actualArgs.size != expression.getExpectedArgumentsCount()) return expression

        val owner = expression.symbol.owner
        if (EvaluationMode.ONLY_BUILTINS.canEvaluateFunction(owner, expression) || EvaluationMode.WITH_ANNOTATIONS.canEvaluateFunction(owner, expression)) {
            evaluate(expression, actualArgs, interpretOnly = true)
            // TODO if result is Primitive -> return const
        }
        return expression
    }

    fun fallbackIrConstructorCall(expression: IrConstructorCall, dispatchReceiver: State?, args: List<State?>): IrConstructorCall {
        val actualArgs = listOf(dispatchReceiver, *args.toTypedArray()).filterNotNull()
        if (actualArgs.size != expression.getExpectedArgumentsCount()) return expression

        val owner = expression.symbol.owner
        if (EvaluationMode.ONLY_BUILTINS.canEvaluateFunction(owner) || EvaluationMode.WITH_ANNOTATIONS.canEvaluateFunction(owner)) {
            evaluate(expression, actualArgs, interpretOnly = true)
        }
        return expression
    }

    fun fallbackIrBlock(expression: IrBlock): IrExpression {
        callStack.newSubFrame(expression)
        fallbackIrStatements(expression.statements)
        callStack.dropSubFrame()
        return expression
    }

    private fun fallbackIrStatements(statements: MutableList<IrStatement>) {
        for (i in 0 until statements.size) {
            statements[i] = statements[i].transform(transformer, null) as IrStatement
            if (i != statements.lastIndex && callStack.peekState().isUnit()) callStack.popState()
        }
    }

    fun evalIrBranchCondition(branch: IrBranch): State? {
        branch.condition = branch.condition.transform(transformer, null)
        return callStack.tryToPopState()
    }

    fun evalIrBranchResult(branch: IrBranch): State? {
        branch.result = branch.result.transform(transformer, null)
        return callStack.tryToPopState()
    }

    fun fallbackIrBranch(branch: IrBranch, condition: State?): IrElement {
        evalIrBranchResult(branch)
        // TODO rollback all changes
        // TODO collect all mutable vars/fields and remove them
        // TODO that to do if object is passed to some none compile time function? 1. only scan it and delete mutated fields 2. remove entire symbol from stack
        return branch
    }

    fun fallbackIrWhen(expression: IrWhen, conditions: List<State?>): IrExpression {
        val beginFromIndex = expression.branches.size - conditions.size
        for (i in (beginFromIndex until expression.branches.size)) {
            fallbackIrBranch(expression.branches[i], conditions[i])
        }
        return expression
    }

    fun evalIrSetValue(expression: IrSetValue): State? {
        expression.value = expression.value.transform(transformer, null)
        return callStack.tryToPopState()
    }

    fun fallbackIrSetValue(expression: IrSetValue, value: State?): IrExpression {
        if (value == null) {
            // TODO remove from stack
            return expression
        }
        evaluate(expression, listOf(value))
        return expression
    }

    fun evalIrGetValue(expression: IrGetValue): State? {
        // TODO evaluate can throw exception, how to avoid?
        evaluate(expression, interpretOnly = false)
        return callStack.tryToPopState()
    }

    fun fallbackIrGetValue(expression: IrGetValue, value: State?): IrExpression {
        value?.let { callStack.pushState(it) }
        return expression
    }

    fun <T> evalIrConst(expression: IrConst<T>): State? {
        evaluate(expression)
        return callStack.tryToPopState()
    }

    fun <T> fallbackIrConst(expression: IrConst<T>, value: State?): IrExpression {
        value?.let { callStack.pushState(it) }
        return expression
    }

    fun evalIrVariable(declaration: IrVariable): State? {
        declaration.initializer = declaration.initializer?.transform(transformer, null)
        return callStack.tryToPopState()
    }

    fun fallbackIrVariable(declaration: IrVariable, value: State?): IrStatement {
        if (declaration.initializer == null) {
            callStack.storeState(declaration.symbol, null)
        } else {
            value?.let { callStack.storeState(declaration.symbol, it) }
        }
        return declaration
    }

    fun fallbackIrGetField(expression: IrGetField): IrExpression {
        evaluate(expression)
        return expression
    }

    fun evalIrSetFieldValue(expression: IrSetField): State? {
        // TODO copy assert from unfoldSetField
        expression.value = expression.value.transform(transformer, null)
        return callStack.tryToPopState()
    }

    fun fallbackIrSetField(expression: IrSetField, value: State?): IrExpression {
        value?.let { evaluate(expression, listOf(it)) }
        return expression
    }
}
