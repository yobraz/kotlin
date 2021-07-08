/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.interpreter.stack

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.interpreter.Instruction
import org.jetbrains.kotlin.ir.interpreter.exceptions.InterpreterError
import org.jetbrains.kotlin.ir.interpreter.state.State
import org.jetbrains.kotlin.ir.interpreter.state.UnknownState
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly

internal class Frame(subFrame: AbstractSubFrame, val irFile: IrFile? = null) {
    private val innerStack = mutableListOf(subFrame)
    private var currentInstruction: Instruction? = null

    private val currentFrame get() = innerStack.last()
    val currentSubFrameOwner: IrElement get() = currentFrame.owner

    companion object {
        const val NOT_DEFINED = "Not defined"
    }

    fun addSubFrame(frame: AbstractSubFrame) {
        innerStack.add(frame)
    }

    fun removeSubFrame() {
        currentFrame.peekState()?.let { if (innerStack.size > 1) innerStack[innerStack.size - 2].pushState(it) }
        removeSubFrameWithoutDataPropagation()
    }

    fun rollbackAllCollectedChanges() {
        (innerStack.last() as SubFrameWithHistory).history.forEach { (symbol, oldState) -> setState(symbol, oldState) }
    }

    fun removeSubFrameWithoutDataPropagation() {
        if (innerStack.size > 1 && innerStack[innerStack.size - 2] is SubFrameWithHistory && innerStack[innerStack.size - 1] is SubFrameWithHistory) {
            (innerStack[innerStack.size - 2] as SubFrameWithHistory).combineHistory(innerStack[innerStack.size - 1] as SubFrameWithHistory)
        }
        innerStack.removeLast()
    }

    fun hasNoSubFrames() = innerStack.isEmpty()
    fun hasNoInstructions() = innerStack.all { it.isEmpty() }

    fun addInstruction(instruction: Instruction) {
        currentFrame.pushInstruction(instruction)
    }

    fun popInstruction(): Instruction {
        return currentFrame.popInstruction().apply { currentInstruction = this }
    }

    fun dropInstructions() = currentFrame.dropInstructions()

    fun pushState(state: State) {
        currentFrame.pushState(state)
    }

    fun popState(): State = currentFrame.popState()
    fun peekState(): State? = currentFrame.peekState()

    fun addVariable(variable: Variable) {
        currentFrame.addVariable(variable)
    }

    fun getState(symbol: IrSymbol): State {
        return (innerStack.lastIndex downTo 0).firstNotNullOfOrNull { innerStack[it].getState(symbol)?.takeIf { it !is UnknownState } }
            ?: throw InterpreterError("$symbol not found") // TODO better message
    }

    fun setState(symbol: IrSymbol, newState: State) {
        (innerStack.lastIndex downTo 0).forEach {
            val oldState = innerStack[it].getState(symbol)
            if (oldState != null) {
                (currentFrame as? SubFrameWithHistory)?.storeOldValue(symbol, oldState)
                return innerStack[it].setState(symbol, newState)
            }
        }
    }

    fun setFieldForReceiver(receiver: IrSymbol, propertySymbol: IrSymbol, newField: State) {
        val receiverState = getState(receiver)
        (currentFrame as? SubFrameWithHistory)?.storeChangeOfField(receiverState, propertySymbol)
        receiverState.setField(Variable(propertySymbol, newField))
    }

    fun containsVariable(symbol: IrSymbol): Boolean = (innerStack.lastIndex downTo 0).any { innerStack[it].containsVariable(symbol) }

    fun getAll(): List<Variable> = innerStack.flatMap { it.getAll() }

    private fun getLineNumberForCurrentInstruction(): String {
        irFile ?: return ""
        val frameOwner = currentInstruction?.element
        return when {
            frameOwner is IrExpression || (frameOwner is IrDeclaration && frameOwner.origin == IrDeclarationOrigin.DEFINED) ->
                ":${irFile.fileEntry.getLineNumber(frameOwner.startOffset) + 1}"
            else -> ""
        }
    }

    fun getFileAndPositionInfo(): String {
        irFile ?: return NOT_DEFINED
        val lineNum = getLineNumberForCurrentInstruction()
        return "${irFile.name}$lineNum"
    }

    override fun toString(): String {
        irFile ?: return NOT_DEFINED
        val fileNameCapitalized = irFile.name.replace(".kt", "Kt").capitalizeAsciiOnly()
        val entryPoint = innerStack.firstOrNull { it.owner is IrFunction }?.owner as? IrFunction
        val lineNum = getLineNumberForCurrentInstruction()

        return "at $fileNameCapitalized.${entryPoint?.fqNameWhenAvailable ?: "<clinit>"}(${irFile.name}$lineNum)"
    }
}
