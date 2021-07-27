/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.interpreter

import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.copyAttributes
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrBreakImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrContinueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
import org.jetbrains.kotlin.ir.interpreter.checker.EvaluationMode
import org.jetbrains.kotlin.ir.interpreter.stack.CallStack
import org.jetbrains.kotlin.ir.interpreter.state.State
import org.jetbrains.kotlin.ir.interpreter.state.asBooleanOrNull
import org.jetbrains.kotlin.ir.interpreter.state.isUnit
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid

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

    internal fun withRollbackOnFailure(block: () -> Boolean) {
        // TODO
    }

    fun interpret(block: IrReturnableBlock): IrElement {
        callStack.newFrame(block)
        fallbackIrStatements(block)
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
        return args.reversed()
    }

    fun fallbackIrCall(expression: IrCall, dispatchReceiver: State?, extensionReceiver: State?, args: List<State?>): IrCall {
        val actualArgs = listOf(dispatchReceiver, extensionReceiver, *args.toTypedArray()).filterNotNull()
        if (actualArgs.size != expression.getExpectedArgumentsCount()) return expression

        val owner = expression.symbol.owner
        if (owner.fqName.startsWith("kotlin.") || EvaluationMode.ONLY_BUILTINS.canEvaluateFunction(owner, expression) || EvaluationMode.WITH_ANNOTATIONS.canEvaluateFunction(owner, expression)) {
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
        fallbackIrStatements(expression)
        callStack.dropSubFrame()
        if (expression.origin == IrStatementOrigin.FOR_LOOP && expression.statements.last() !is IrWhileLoop) {
            return IrBlockImpl(expression.startOffset, expression.endOffset, expression.type, origin = null, expression.statements)
        }
        return expression
    }

    private fun fallbackIrStatements(container: IrContainerExpression) {
        val newStatements = mutableListOf<IrStatement>()
        for (i in container.statements.indices) {
            val newStatement = container.statements[i].transform(transformer, null) as IrStatement
            if (newStatement is IrBreakContinue) break
            newStatements += newStatement
            if (i != container.statements.lastIndex && callStack.peekState().isUnit()) callStack.popState()
        }
        container.statements.clear()
        container.statements.addAll(newStatements)
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
        callStack.rollbackAllChanges {
            evalIrBranchResult(branch)
            true
        }
        return branch
    }

    fun fallbackIrWhen(expression: IrWhen, beginFromIndex: Int = 0, inclusive: Boolean = true): IrExpression {
        callStack.removeAllMutatedVariablesAndFields {
            for (i in (beginFromIndex until expression.branches.size)) {
                val condition = if (!inclusive && i == beginFromIndex) null else evalIrBranchCondition(expression.branches[i])
                fallbackIrBranch(expression.branches[i], condition)
            }
            // TODO that to do if object is passed to some none compile time function? 1. only scan it and delete mutated fields 2. remove entire symbol from stack
            true
        }
        return expression
    }

    fun evalIrSetValue(expression: IrSetValue): State? {
        expression.value = expression.value.transform(transformer, null)
        return callStack.tryToPopState()
    }

    fun fallbackIrSetValue(expression: IrSetValue, value: State?): IrExpression {
        if (value == null) {
            callStack.dropState(expression.symbol)
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

    fun evalIrVarargElements(expression: IrVararg): List<State?> {
        (0 until expression.elements.size).forEach {
            expression.putElement(it, expression.elements[it].transform(transformer, null) as IrVarargElement)
        }
        val args = mutableListOf<State?>()
        (0 until expression.elements.size).forEach {
            args += callStack.tryToPopState()
        }
        return args
    }

    fun fallbackIrVararg(expression: IrVararg, args: List<State?>): IrExpression {
        val actualArgs = args.filterNotNull()
        if (actualArgs.size != expression.elements.size) return expression

        evaluate(expression, actualArgs, interpretOnly = true)
        return expression
    }

    fun evalIrWhileCondition(expression: IrWhileLoop): State? {
        expression.condition = expression.condition.transform(transformer, null)
        return callStack.tryToPopState()
    }

    private class Copier(symbolRemapper: SymbolRemapper, typeRemapper: TypeRemapper) : DeepCopyIrTreeWithSymbols(symbolRemapper, typeRemapper) {
        override fun visitBreak(jump: IrBreak): IrBreak {
            return IrBreakImpl(jump.startOffset, jump.endOffset, jump.type, jump.loop)
        }

        override fun visitContinue(jump: IrContinue): IrContinue {
            return IrContinueImpl(jump.startOffset, jump.endOffset, jump.type, jump.loop)
        }
    }

    fun fallbackIrWhileLoop(expression: IrWhileLoop): IrExpression {
        val newBlock = IrBlockImpl(0, 0, expression.body!!.type)
        var competed = false

        callStack.removeAllMutatedVariablesAndFields {
            while (true) {
                val condition = evalIrWhileCondition(expression)?.asBooleanOrNull()
                if (condition == null) {
                    expression.body?.transformChildren(transformer, null) // transform for 2 reasons: 1. remove mutated vars; 2. optimize that is possible
                    return@removeAllMutatedVariablesAndFields true
                }
                if (condition == false) {
                    competed = true
                    break
                }

                val newBody = expression.body!!.deepCopyWithSymbols(ParentFinder().apply { expression.accept(this, null) }.parent, ::Copier)
//                if (newBody is IrBlock) {
//                    newBlock.statements += newBody.statements[1]
//                } else {
//                    newBlock.statements += newBody
//                }
                newBlock.statements += newBody
                newBody.transformChildren(transformer, null)

                val jump = when {
                    newBody is IrBreakContinue -> {
                        newBlock.statements.removeLast()
                        newBody
                    }
                    newBody is IrContainerExpression && newBody.statements.last() is IrBreakContinue -> {
                        newBody.statements.removeLast() as IrBreakContinue
                    }
                    else -> null
                }
                if (jump is IrBreak) {
                    if (jump.loop == expression) { competed = true; break } else return jump
                } else if (jump is IrContinue) {
                    if (jump.loop == expression) continue else return jump
                }
            }
            false
        }
        return if (competed) newBlock else expression
    }
}

private class ParentFinder : IrElementVisitorVoid {
    var parent: IrDeclarationParent? = null
    override fun visitElement(element: IrElement) {
        if (element is IrDeclarationBase && parent == null) {
            parent = element.parent
            return
        }
        element.acceptChildren(this, null)
    }
}
