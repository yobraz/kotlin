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
import org.jetbrains.kotlin.ir.interpreter.state.StateWithClosure
import org.jetbrains.kotlin.ir.interpreter.state.UnknownState
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly

internal class Frame(subFrameOwner: IrElement, collectAllChanges: Boolean, val irFile: IrFile? = null) {
    private val innerStack = ArrayDeque<AbstractSubFrame>().apply { add(if (collectAllChanges) SubFrameWithHistory(subFrameOwner) else SubFrame(subFrameOwner)) }
    private var currentInstruction: Instruction? = null

    private val currentFrame get() = innerStack.last()
    val currentSubFrameOwner: IrElement get() = currentFrame.owner

    companion object {
        const val NOT_DEFINED = "Not defined"
    }

    fun addSubFrame(subFrameOwner: IrElement, collectAllChanges: Boolean) {
        val newFrame = if (collectAllChanges) SubFrameWithHistory(subFrameOwner) else SubFrame(subFrameOwner)
        innerStack.add(newFrame)
    }

    fun removeSubFrame() {
        val state = currentFrame.peekState()
        removeSubFrameWithoutDataPropagation()
        if (!hasNoSubFrames() && state != null) currentFrame.pushState(state)
    }

    fun rollbackAllCollectedChanges() {
        (innerStack.last() as SubFrameWithHistory).history.forEach { (symbol, oldState) -> rewriteState(symbol, oldState) }
        (innerStack.last() as SubFrameWithHistory).fieldHistory.forEach { (receiver, fieldToState) ->
            fieldToState.forEach { (propertySymbol, state) ->
                receiver.setField(propertySymbol, state)
            }
        }
    }

    fun dropAllVariablesInHistory() {
        (innerStack.last() as SubFrameWithHistory).history.forEach { (symbol, _) -> rewriteState(symbol, UnknownState) }
        (innerStack.last() as SubFrameWithHistory).fieldHistory.forEach { (receiver, fieldToState) ->
            fieldToState.forEach { (propertySymbol, _) ->
                receiver.setField(propertySymbol, UnknownState)
            }
        }
    }

    fun removeSubFrameWithoutDataPropagation() {
        if (innerStack.size > 1 && innerStack[innerStack.size - 2] is SubFrameWithHistory && innerStack[innerStack.size - 1] is SubFrameWithHistory) {
            (innerStack[innerStack.size - 2] as SubFrameWithHistory).combineHistory(innerStack[innerStack.size - 1] as SubFrameWithHistory)
        }
        innerStack.removeLast()
    }

    fun hasNoSubFrames() = innerStack.isEmpty()
    fun hasNoInstructions() = innerStack.all { it.isEmpty() }
    fun pushInstruction(instruction: Instruction) = currentFrame.pushInstruction(instruction)
    fun popInstruction(): Instruction = currentFrame.popInstruction().apply { currentInstruction = this }
    fun dropInstructions() = currentFrame.dropInstructions()

    fun pushState(state: State) = currentFrame.pushState(state)
    fun popState(): State = currentFrame.popState()
    fun peekState(): State? = currentFrame.peekState()

    fun storeState(symbol: IrSymbol, state: State?) = currentFrame.storeState(symbol, state)
    fun storeState(symbol: IrSymbol, variable: Variable) = currentFrame.storeState(symbol, variable)

    private inline fun forEachSubFrame(block: (AbstractSubFrame) -> Unit) {
        // TODO speed up reverse iteration or do it forward
        (innerStack.lastIndex downTo 0).forEach {
            block(innerStack[it])
        }
    }

    fun loadState(symbol: IrSymbol): State {
        forEachSubFrame { it.loadState(symbol)?.let { state -> return state } }
        throw InterpreterError("$symbol not found") // TODO better message
    }

    fun rewriteState(symbol: IrSymbol, newState: State?) {
        forEachSubFrame {
            if (it.containsStateInMemory(symbol)) {
                (currentFrame as? SubFrameWithHistory)?.storeOldValue(symbol, it.loadState(symbol))
                return it.rewriteState(symbol, newState)
            }
        }
    }

    fun setFieldForReceiver(receiver: IrSymbol, propertySymbol: IrSymbol, newField: State) {
        val receiverState = loadState(receiver)
        (currentFrame as? SubFrameWithHistory)?.storeChangeOfField(receiverState, propertySymbol)
        receiverState.setField(propertySymbol, newField)
    }

    fun containsStateInMemory(symbol: IrSymbol): Boolean {
        forEachSubFrame { if (it.containsStateInMemory(symbol)) return true }
        return false
    }

    fun copyMemoryInto(newFrame: Frame) {
        this.getAll().forEach { (symbol, variable) -> if (!newFrame.containsStateInMemory(symbol)) newFrame.storeState(symbol, variable) }
    }

    fun copyMemoryInto(closure: StateWithClosure) {
        getAll().reversed().forEach { (symbol, variable) -> closure.upValues[symbol] = variable }
    }

    private fun getAll(): List<Pair<IrSymbol, Variable>> = innerStack.flatMap { it.getAll() }

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
