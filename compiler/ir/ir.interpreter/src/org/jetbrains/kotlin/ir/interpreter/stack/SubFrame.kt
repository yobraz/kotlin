/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.interpreter.stack

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.interpreter.Instruction
import org.jetbrains.kotlin.ir.interpreter.state.State
import org.jetbrains.kotlin.ir.symbols.IrSymbol

internal abstract class AbstractSubFrame(val owner: IrElement) {
    private val instructions = mutableListOf<Instruction>()
    private val dataStack = DataStack()
    private val memory = mutableListOf<Variable>()

    // Methods to work with instruction
    fun isEmpty() = instructions.isEmpty()
    fun pushInstruction(instruction: Instruction) = instructions.add(0, instruction)
    fun popInstruction(): Instruction = instructions.removeFirst()
    fun dropInstructions() = instructions.lastOrNull()?.apply { instructions.clear() }

    // Methods to work with data
    fun pushState(state: State) = dataStack.push(state)
    fun popState(): State = dataStack.pop()
    fun peekState(): State? = dataStack.peek()

    // Methods to work with memory
    fun addVariable(variable: Variable) {
        memory.add(0, variable)
    }

    protected fun getVariable(symbol: IrSymbol): Variable? = memory.firstOrNull { it.symbol == symbol }
    fun containsVariable(symbol: IrSymbol): Boolean = getVariable(symbol) != null
    fun getState(symbol: IrSymbol): State? = getVariable(symbol)?.state
    fun setState(symbol: IrSymbol, newState: State) {
        getVariable(symbol)?.state = newState
    }

    fun getAll(): List<Variable> = memory
}

internal class SubFrame(owner: IrElement) : AbstractSubFrame(owner)

internal class SubFrameWithHistory(owner: IrElement) : AbstractSubFrame(owner) {
    val history = mutableMapOf<IrSymbol, State>()
    val fieldHistory = mutableMapOf<State, MutableMap<IrSymbol, State>>()

    fun combineHistory(other: SubFrameWithHistory) {
        other.history.forEach { history.putIfAbsent(it.key, it.value) }
        other.fieldHistory.forEach { fieldHistory.putIfAbsent(it.key, it.value) }
    }

//    fun rollbackAllCollectedChanges() {
//        history.forEach { (variable, oldState) -> variable.state = oldState }
//    }

    fun storeChangeOfField(receiver: State, propertySymbol: IrSymbol) {
        fieldHistory.getOrPut(receiver) {
            mutableMapOf(Pair(propertySymbol, receiver.getField(propertySymbol)!!))
        }.putIfAbsent(propertySymbol, receiver.getField(propertySymbol)!!)
    }

    fun storeOldValue(symbol: IrSymbol, oldState: State) {
        if (!history.contains(symbol)) history[symbol] = oldState
    }
}

private class DataStack {
    private val stack = mutableListOf<State>()

    fun push(state: State) {
        stack.add(state)
    }

    fun pop(): State = stack.removeLast()
    fun peek(): State? = stack.lastOrNull()
}

private class HistoryOfChanges {
    val history = mutableMapOf<Variable, State>()
}
