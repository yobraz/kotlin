/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.interpreter.stack

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.interpreter.CompoundInstruction
import org.jetbrains.kotlin.ir.interpreter.Instruction
import org.jetbrains.kotlin.ir.interpreter.SimpleInstruction
import org.jetbrains.kotlin.ir.interpreter.handleAndDropResult
import org.jetbrains.kotlin.ir.interpreter.state.State
import org.jetbrains.kotlin.ir.interpreter.state.StateWithClosure
import org.jetbrains.kotlin.ir.interpreter.state.UnknownState
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.fileOrNull

internal class CallStack {
    private val frames = mutableListOf<Frame>()
    private val currentFrame get() = frames.last()
    internal val currentFrameOwner get() = currentFrame.currentSubFrameOwner
    private var collectAllChanges = false

    fun newFrame(frameOwner: IrElement, irFile: IrFile? = null) {
        val newFrame = if (collectAllChanges) SubFrameWithHistory(frameOwner) else SubFrame(frameOwner)
        frames.add(Frame(newFrame, irFile))
    }

    fun newFrame(frameOwner: IrFunction) {
        val newFrame = if (collectAllChanges) SubFrameWithHistory(frameOwner) else SubFrame(frameOwner)
        frames.add(Frame(newFrame, frameOwner.fileOrNull))
    }

    fun newSubFrame(frameOwner: IrElement) {
        val newFrame = if (collectAllChanges) SubFrameWithHistory(frameOwner) else SubFrame(frameOwner)
        currentFrame.addSubFrame(newFrame)
    }

    fun dropFrame() {
        frames.removeLast()
    }

    fun dropFrameAndCopyResult() {
        val result = peekState() ?: return dropFrame()
        popState()
        dropFrame()
        pushState(result)
    }

    fun dropSubFrame() {
        currentFrame.removeSubFrame()
    }

    fun safeExecute(block: () -> Unit) {
        val originalFrame = currentFrame
        val originalFrameOwner = currentFrameOwner
        try {
            block()
        } catch (e: Exception) {
            while (currentFrame != originalFrame) {
                dropFrame()
            }
            while (currentFrameOwner != originalFrameOwner) {
                dropSubFrame()
            }
        }
    }

    fun rollbackAllChanges(block: () -> Boolean) {
        val previous = collectAllChanges
        collectAllChanges = true
        currentFrame.addSubFrame(SubFrameWithHistory(currentFrameOwner))
        if (block()) {
            currentFrame.rollbackAllCollectedChanges()
            dropSubFrame()
            collectAllChanges = previous
        }
    }

    inline fun removeAllMutatedVariablesAndFields(block: () -> Boolean) {
        val previous = collectAllChanges
        collectAllChanges = true
        currentFrame.addSubFrame(SubFrameWithHistory(currentFrameOwner))
        if (block()) {
            currentFrame.dropAllVariablesInHistory()
            dropSubFrame()
            collectAllChanges = previous
        }
    }

    fun returnFromFrameWithResult(irReturn: IrReturn) {
        val result = popState()
        val returnTarget = irReturn.returnTargetSymbol.owner
        var frameOwner = currentFrameOwner
        while (frameOwner != returnTarget) {
            when (frameOwner) {
                is IrTry -> {
                    dropSubFrame()
                    pushState(result)
                    addInstruction(SimpleInstruction(irReturn))
                    frameOwner.finallyExpression?.handleAndDropResult(this)
                    return
                }
                is IrCatch -> {
                    val tryBlock = currentFrame.dropInstructions()!!.element as IrTry// last instruction in `catch` block is `try`
                    dropSubFrame()
                    pushState(result)
                    addInstruction(SimpleInstruction(irReturn))
                    tryBlock.finallyExpression?.handleAndDropResult(this)
                    return
                }
                else -> {
                    dropSubFrame()
                    if (currentFrame.hasNoSubFrames() && frameOwner != returnTarget) dropFrame()
                    frameOwner = currentFrameOwner
                }
            }
        }

        currentFrame.dropInstructions()
        addInstruction(SimpleInstruction(returnTarget))
        if (returnTarget !is IrConstructor) pushState(result)
    }

    fun unrollInstructionsForBreakContinue(breakOrContinue: IrBreakContinue) {
        var frameOwner = currentFrameOwner
        while (frameOwner != breakOrContinue.loop) {
            when (frameOwner) {
                is IrTry -> {
                    currentFrame.removeSubFrameWithoutDataPropagation()
                    addInstruction(CompoundInstruction(breakOrContinue))
                    newSubFrame(frameOwner) // will be deleted when interpret 'try'
                    addInstruction(SimpleInstruction(frameOwner))
                    return
                }
                is IrCatch -> {
                    val tryInstruction = currentFrame.dropInstructions()!! // last instruction in `catch` block is `try`
                    currentFrame.removeSubFrameWithoutDataPropagation()
                    addInstruction(CompoundInstruction(breakOrContinue))
                    newSubFrame(tryInstruction.element!!)  // will be deleted when interpret 'try'
                    addInstruction(tryInstruction)
                    return
                }
                else -> {
                    currentFrame.removeSubFrameWithoutDataPropagation()
                    frameOwner = currentFrameOwner
                }
            }
        }

        when (breakOrContinue) {
            is IrBreak -> currentFrame.removeSubFrameWithoutDataPropagation() // drop loop
            else -> if (breakOrContinue.loop is IrDoWhileLoop) {
                addInstruction(SimpleInstruction(breakOrContinue.loop))
                addInstruction(CompoundInstruction(breakOrContinue.loop.condition))
            } else {
                addInstruction(CompoundInstruction(breakOrContinue.loop))
            }
        }
    }

    fun dropFramesUntilTryCatch() {
        val exception = popState()
        var frameOwner = currentFrameOwner
        while (frames.isNotEmpty()) {
            val frame = currentFrame
            while (!frame.hasNoSubFrames()) {
                frameOwner = frame.currentSubFrameOwner
                when (frameOwner) {
                    is IrTry -> {
                        dropSubFrame()  // drop all instructions that left
                        newSubFrame(frameOwner)
                        addInstruction(SimpleInstruction(frameOwner)) // to evaluate finally at the end
                        frameOwner.catches.reversed().forEach { addInstruction(CompoundInstruction(it)) }
                        pushState(exception)
                        return
                    }
                    is IrCatch -> {
                        // in case of exception in catch, drop everything except of last `try` instruction
                        addInstruction(frame.dropInstructions()!!)
                        pushState(exception)
                        return
                    }
                    else -> frame.removeSubFrameWithoutDataPropagation()
                }
            }
            dropFrame()
        }

        if (frames.size == 0) newFrame(frameOwner) // just stub frame
        pushState(exception)
    }

    fun hasNoInstructions() = frames.isEmpty() || (frames.size == 1 && currentFrame.hasNoInstructions())

    fun addInstruction(instruction: Instruction) {
        currentFrame.addInstruction(instruction)
    }

    fun popInstruction(): Instruction {
        return currentFrame.popInstruction()
    }

    fun pushState(state: State) {
        currentFrame.pushState(state)
    }

    fun popState(): State = currentFrame.popState()
    fun peekState(): State? = currentFrame.peekState()
    fun tryToPopState(): State? = currentFrame.peekState()?.also { currentFrame.popState() }

    fun addVariable(variable: Variable) {
        currentFrame.addVariable(variable)
    }

    fun getState(symbol: IrSymbol): State = currentFrame.getState(symbol)
    fun setState(symbol: IrSymbol, newState: State) = currentFrame.setState(symbol, newState)
    fun dropState(symbol: IrSymbol) = currentFrame.setState(symbol, UnknownState)
    fun containsVariable(symbol: IrSymbol): Boolean = currentFrame.containsVariable(symbol)
    fun setFieldForReceiver(receiver: IrSymbol, propertySymbol: IrSymbol, newField: State) = currentFrame.setFieldForReceiver(receiver, propertySymbol, newField)

    fun storeUpValues(state: StateWithClosure) {
        // TODO save only necessary declarations
        currentFrame.getAll().reversed().forEach { variable ->
            // TODO replace list with map
            val index = state.upValues.indexOfFirst { it.symbol == variable.symbol }
            if (index == -1) state.upValues.add(variable) else state.upValues[index] = variable
        }
    }

    fun loadUpValues(state: StateWithClosure) {
        state.upValues.forEach { addVariable(it) }
    }

    fun copyUpValuesFromPreviousFrame() {
        frames[frames.size - 2].getAll().forEach { if (!containsVariable(it.symbol)) addVariable(it) }
    }

    fun getStackTrace(): List<String> {
        return frames.map { it.toString() }.filter { it != Frame.NOT_DEFINED }
    }

    fun getFileAndPositionInfo(): String {
        return frames[frames.size - 2].getFileAndPositionInfo()
    }

    fun getStackCount(): Int = frames.size
}
