/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen.optimization.temporaryVals

import org.jetbrains.kotlin.codegen.optimization.DeadCodeEliminationMethodTransformer
import org.jetbrains.kotlin.codegen.optimization.common.InsnSequence
import org.jetbrains.kotlin.codegen.optimization.common.removeUnusedLocalVariables
import org.jetbrains.kotlin.codegen.optimization.transformer.MethodTransformer
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.tree.*
import kotlin.math.max

class CodeCompactionTransformer : MethodTransformer() {
    private val temporaryValsAnalyzer = TemporaryValsAnalyzer()
    private val deadCodeElimination = DeadCodeEliminationMethodTransformer()

    override fun transform(internalClassName: String, methodNode: MethodNode) {
        val cfg = ControlFlowGraph(methodNode)

        processLabels(cfg)
        peepholePass1(cfg)

        val temporaryVals = temporaryValsAnalyzer.analyze(internalClassName, methodNode)
        if (temporaryVals.isNotEmpty()) {
            optimizeTemporaryVals(cfg, temporaryVals)
        }

        methodNode.stripTemporaryValInitMarkers()
        methodNode.removeUnusedLocalVariables()
    }


    private class ControlFlowGraph(val methodNode: MethodNode) {
        private val nonTrivialPredecessors = HashMap<LabelNode, MutableList<AbstractInsnNode>>()

        fun reset() {
            nonTrivialPredecessors.clear()
        }

        fun addNonTrivialPredecessor(label: LabelNode, pred: AbstractInsnNode) {
            nonTrivialPredecessors.getOrPut(label) { SmartList() }.add(pred)
        }

        fun hasNonTrivialPredecessors(label: LabelNode) =
            nonTrivialPredecessors.containsKey(label)

        fun hasSinglePredecessor(label: LabelNode, expectedPredecessor: AbstractInsnNode): Boolean {
            var trivialPredecessor = label.previous
            if (trivialPredecessor.opcode == Opcodes.GOTO ||
                trivialPredecessor.opcode in Opcodes.IRETURN..Opcodes.RETURN ||
                trivialPredecessor.opcode == Opcodes.ATHROW
            ) {
                // Previous instruction is not a predecessor in CFG
                trivialPredecessor = null
            } else {
                // Check trivial predecessor
                if (trivialPredecessor != expectedPredecessor) return false
            }

            val nonTrivialPredecessors = nonTrivialPredecessors[label]
                ?: return trivialPredecessor != null

            return when {
                nonTrivialPredecessors.size > 1 ->
                    false
                nonTrivialPredecessors.size == 0 ->
                    trivialPredecessor == expectedPredecessor
                else ->
                    trivialPredecessor == null && nonTrivialPredecessors[0] == expectedPredecessor
            }
        }
    }


    private fun processLabels(cfg: ControlFlowGraph) {
        cfg.reset()

        val methodNode = cfg.methodNode
        val insnList = methodNode.instructions

        val usedLabels = HashSet<LabelNode>()
        val first = insnList.first
        if (first is LabelNode) {
            usedLabels.add(first)
        }
        val last = insnList.last
        if (last is LabelNode) {
            usedLabels.add(last)
        }

        fun addCfgEdgeToLabel(from: AbstractInsnNode, label: LabelNode) {
            usedLabels.add(label)
            cfg.addNonTrivialPredecessor(label, from)
        }

        fun addCfgEdgesToLabels(from: AbstractInsnNode, labels: Collection<LabelNode>) {
            usedLabels.addAll(labels)
            for (label in labels) {
                cfg.addNonTrivialPredecessor(label, from)
            }
        }

        for (insn in insnList) {
            when (insn.type) {
                AbstractInsnNode.LINE -> {
                    usedLabels.add((insn as LineNumberNode).start)
                }
                AbstractInsnNode.JUMP_INSN -> {
                    addCfgEdgeToLabel(insn, (insn as JumpInsnNode).label)
                }
                AbstractInsnNode.LOOKUPSWITCH_INSN -> {
                    val switchInsn = insn as LookupSwitchInsnNode
                    addCfgEdgeToLabel(insn, switchInsn.dflt)
                    addCfgEdgesToLabels(insn, switchInsn.labels)
                }
                AbstractInsnNode.TABLESWITCH_INSN -> {
                    val switchInsn = insn as TableSwitchInsnNode
                    addCfgEdgeToLabel(insn, switchInsn.dflt)
                    addCfgEdgesToLabels(insn, switchInsn.labels)
                }
            }
        }
        for (lv in methodNode.localVariables) {
            usedLabels.add(lv.start)
            usedLabels.add(lv.end)
        }
        for (tcb in methodNode.tryCatchBlocks) {
            usedLabels.add(tcb.start)
            usedLabels.add(tcb.end)
            addCfgEdgeToLabel(tcb.start, tcb.handler)
        }

        var insn = insnList.first
        while (insn != null) {
            insn = if (insn is LabelNode && insn !in usedLabels) {
                val next = insn.next
                insnList.remove(insn)
                next
            } else {
                insn.next
            }
        }
    }

    private fun optimizeTemporaryVals(cfg: ControlFlowGraph, temporaryVals: List<TemporaryVal>) {
        val insnList = cfg.methodNode.instructions

        for (tmp in temporaryVals) {
            if (tmp.loadInsns.size == 1) {
                // If there are no intervening instructions between store and load,
                // drop both store and load, just keep intermediate value on stack.
                val storeInsn = tmp.storeInsn
                val loadInsn = tmp.loadInsns[0]

                if (InsnSequence(storeInsn.next, loadInsn).any { it.isIntervening(cfg) }) continue

                insnList.remove(storeInsn)
                insnList.remove(loadInsn)
            }
        }
    }

    private fun peepholePass1(cfg: ControlFlowGraph) {
        val insnList = cfg.methodNode.instructions

        var maxStackIncrement = 0
        var needsDCE = false
        for (insn in insnList.toArray()) {
            if (insn.matchOpcodes(Opcodes.ALOAD, Opcodes.IFNULL, Opcodes.ALOAD)) {
                // Given instruction sequence:
                //      aload v
                //      ifnull L
                //      aload x
                //      { ... if non-null ... }
                //  L:
                //      { ... if null ... }
                // Rewrite to:
                //      aload v
                //      dup
                //      ifnull L
                //      { ... if non-null ... }
                //  L:
                //      pop
                //      { ... if null ... }
                // Since we don't remove any variable stores, we don't care whether 'v' is a temporary
                //
                // If 'v' actually was a temporary, this would usually allow us to optimize out immediate store-load for 'v'.
                // Also, it allows us to merge 'pop; aconst_null; goto LExit' branches later.

                val aLoad1 = insn as VarInsnNode
                val ifNull = insn.next as JumpInsnNode
                val aLoad2 = ifNull.next as VarInsnNode
                if (aLoad1.`var` != aLoad2.`var`) continue
                if (!cfg.hasSinglePredecessor(ifNull.label, ifNull)) continue

                insnList.insertBefore(ifNull, InsnNode(Opcodes.DUP))
                insnList.remove(aLoad2)
                insnList.insert(ifNull.label, InsnNode(Opcodes.POP))
                maxStackIncrement = max(maxStackIncrement, 1)
                continue
            }

            if (insn.opcode == Opcodes.NOP) {
                // Remove NOPs not preceded by LABEL or LINENUMBER instructions.
                val prev = insn.previous ?: continue
                if (prev.type == AbstractInsnNode.LABEL || prev.type == AbstractInsnNode.LINE) continue

                insnList.remove(insn)
                continue
            }

            if (insn.opcode == Opcodes.GOTO) {
                // If we have a GOTO instruction that leads to another GOTO, replace corresponding label.
                val jumpInsn = insn as JumpInsnNode
                val newLabel = jumpInsn.getFinalLabel()
                if (newLabel != jumpInsn.label) {
                    needsDCE = true
                }
                jumpInsn.label = newLabel
            }
        }

        if (needsDCE) {
            processLabels(cfg)
        }

        // Merge "null branches".
        // At this point we can have a bunch of "null branches" in the form:
        //      IFNULL LBranchI:
        //      ...
        //  LBranchI:
        //      POP
        //      ACONST_NULL
        //      GOTO LExit

        val nullBranches = HashMap<LabelNode, ArrayList<JumpInsnNode>>()
        for (insn in insnList.toArray()) {
            if (insn.opcode == Opcodes.IFNULL) {
                val ifNullInsn = insn as JumpInsnNode
                val branchLabel = ifNullInsn.label
                if (!cfg.hasSinglePredecessor(branchLabel, ifNullInsn)) continue
                val branchStart = branchLabel.next ?: continue
                if (!branchStart.matchOpcodes(Opcodes.POP, Opcodes.ACONST_NULL, Opcodes.GOTO)) continue
                val gotoInsn = branchStart.next.next as JumpInsnNode
                val exitLabel = gotoInsn.label
                nullBranches.getOrPut(exitLabel) { ArrayList() }.add(ifNullInsn)
            }
        }

        fun getMergedBranchLabel(exitLabel: LabelNode, ifNullInsns: List<JumpInsnNode>): LabelNode? {
            val prev1 = exitLabel.previous
            if (prev1 != null && prev1.opcode == Opcodes.ACONST_NULL) {
                val prev2 = prev1.previous
                if (prev2 != null && prev2.opcode == Opcodes.POP) {
                    val prev3 = prev2.previous
                    if (prev3 != null && prev3.type == AbstractInsnNode.LABEL) {
                        return prev3 as LabelNode
                    }
                    val newLabel = LabelNode()
                    insnList.insertBefore(prev2, newLabel)
                    return newLabel
                }
            }
            return ifNullInsns.firstOrNull()?.label
        }

        for ((exitLabel, ifNullInsns) in nullBranches.entries) {
            val newBranchLabel = getMergedBranchLabel(exitLabel, ifNullInsns)
            if (newBranchLabel != null) {
                needsDCE = true
                for (ifNull in ifNullInsns) {
                    ifNull.label = newBranchLabel
                }
            }
        }

        // Cleanup methodNode and CFG

        cfg.methodNode.maxStack += maxStackIncrement

        if (needsDCE) {
            deadCodeElimination.transform("<fake>", cfg.methodNode)
            processLabels(cfg)
        }
    }

    private fun JumpInsnNode.getFinalLabel(): LabelNode {
        var label = this.label
        var insn = label.next
        while (true) {
            when {
                insn.type == AbstractInsnNode.LABEL || insn.type == AbstractInsnNode.LINE -> {
                    insn = insn.next ?: break
                }
                insn.opcode == Opcodes.GOTO -> {
                    val newLabel = (insn as JumpInsnNode).label
                    if (newLabel == label) return newLabel
                    label = newLabel
                    insn = label.next ?: break
                }
                insn.opcode == Opcodes.NOP -> {
                    insn = insn.next ?: break
                }
                else -> break
            }
        }
        return label
    }

    private fun AbstractInsnNode.matchOpcodes(vararg opcodes: Int): Boolean {
        var insn = this
        for (i in opcodes.indices) {
            if (insn.opcode != opcodes[i]) return false
            insn = insn.next ?: return false
        }
        return true
    }

    private fun AbstractInsnNode.isIntervening(context: ControlFlowGraph): Boolean =
        when (this.type) {
            AbstractInsnNode.LINE, AbstractInsnNode.FRAME ->
                false
            AbstractInsnNode.LABEL ->
                context.hasNonTrivialPredecessors(this as LabelNode)
            AbstractInsnNode.INSN ->
                this.opcode != Opcodes.NOP
            else ->
                true
        }

}