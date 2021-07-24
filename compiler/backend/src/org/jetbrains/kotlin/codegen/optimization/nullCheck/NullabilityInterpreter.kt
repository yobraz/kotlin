/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.codegen.optimization.nullCheck

import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.codegen.inline.ReifiedTypeInliner
import org.jetbrains.kotlin.codegen.inline.insnOpcodeText
import org.jetbrains.kotlin.codegen.inline.insnText
import org.jetbrains.kotlin.codegen.inline.operationKind
import org.jetbrains.kotlin.codegen.optimization.boxing.isBoxing
import org.jetbrains.kotlin.codegen.pseudoInsns.PseudoInsn
import org.jetbrains.kotlin.codegen.pseudoInsns.isPseudo
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.resolve.jvm.AsmTypes
import org.jetbrains.org.objectweb.asm.Handle
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.tree.*
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException
import org.jetbrains.org.objectweb.asm.tree.analysis.Interpreter
import java.lang.AssertionError

class NullabilityInterpreter(
    private val generationState: GenerationState
) : Interpreter<NullabilityValue>(Opcodes.API_VERSION) {

    private val notNullBooleanArray = NullabilityValue.NotNull(Type.getType("[Z"))
    private val notNullCharArray = NullabilityValue.NotNull(Type.getType("[C"))
    private val notNullByteArray = NullabilityValue.NotNull(Type.getType("[B"))
    private val notNullShortArray = NullabilityValue.NotNull(Type.getType("[S"))
    private val notNullIntArray = NullabilityValue.NotNull(Type.getType("[I"))
    private val notNullFloatArray = NullabilityValue.NotNull(Type.getType("[F"))
    private val notNullDoubleArray = NullabilityValue.NotNull(Type.getType("[D"))
    private val notNullLongArray = NullabilityValue.NotNull(Type.getType("[J"))

    private val notNullString = NullabilityValue.NotNull(Type.getObjectType("java/lang/String"))
    private val notNullClass = NullabilityValue.NotNull(Type.getObjectType("java/lang/Class"))
    private val notNullMethod = NullabilityValue.NotNull(Type.getObjectType("java/lang/invoke/MethodType"))
    private val notNullMethodHandle = NullabilityValue.NotNull(Type.getObjectType("java/lang/invoke/MethodHandle"))

    override fun newValue(type: Type?): NullabilityValue? =
        if (type == null) {
            NullabilityValue.Any
        } else when (type.sort) {
            Type.VOID ->
                null
            Type.LONG, Type.DOUBLE ->
                NullabilityValue.Primitive2
            Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT, Type.FLOAT ->
                NullabilityValue.Primitive1
            Type.OBJECT, Type.ARRAY, Type.METHOD ->
                NullabilityValue.Nullable(type)
            else ->
                throw IllegalArgumentException("Unknown type sort " + type.sort)
        }


    override fun newOperation(insn: AbstractInsnNode): NullabilityValue =
        when (insn.opcode) {
            Opcodes.ACONST_NULL ->
                NullabilityValue.Null
            Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5 ->
                NullabilityValue.Primitive1
            Opcodes.LCONST_0, Opcodes.LCONST_1 ->
                NullabilityValue.Primitive2
            Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2 ->
                NullabilityValue.Primitive1
            Opcodes.DCONST_0, Opcodes.DCONST_1 ->
                NullabilityValue.Primitive2
            Opcodes.BIPUSH, Opcodes.SIPUSH ->
                NullabilityValue.Primitive1
            Opcodes.LDC -> {
                when (val cst = (insn as LdcInsnNode).cst) {
                    is Int, is Float ->
                        NullabilityValue.Primitive1
                    is Long, is Double ->
                        NullabilityValue.Primitive2
                    is String ->
                        notNullString
                    is Type -> {
                        when (cst.sort) {
                            Type.OBJECT, Type.ARRAY ->
                                notNullClass
                            Type.METHOD ->
                                notNullMethod
                            else ->
                                throw IllegalArgumentException("Illegal LDC constant $cst")
                        }
                    }
                    is Handle ->
                        notNullMethodHandle
                    else ->
                        throw IllegalArgumentException("Illegal LDC constant $cst")
                }
            }
            Opcodes.GETSTATIC ->
                newValue(Type.getType((insn as FieldInsnNode).desc))
                    ?: throw AssertionError("Unexpected void value: ${insn.insnText}")
            Opcodes.NEW ->
                NullabilityValue.NotNull(Type.getObjectType((insn as TypeInsnNode).desc))
            else ->
                throw IllegalArgumentException("Unexpected instruction: " + insn.insnOpcodeText)
        }

    override fun copyOperation(insn: AbstractInsnNode, value: NullabilityValue?): NullabilityValue? {
        return value
    }

    override fun binaryOperation(
        insn: AbstractInsnNode,
        value1: NullabilityValue,
        value2: NullabilityValue
    ): NullabilityValue? {
        if (insn.opcode == Opcodes.AALOAD && value1 is NullabilityValue.Ref) {
            val arrayType = value1.type
            if (arrayType.sort == Type.ARRAY) {
                return NullabilityValue.Nullable(AsmUtil.correctElementType(arrayType))
            }
        }

        return when (insn.opcode) {
            Opcodes.IALOAD, Opcodes.BALOAD, Opcodes.CALOAD, Opcodes.SALOAD, Opcodes.IADD, Opcodes.ISUB,
            Opcodes.IMUL, Opcodes.IDIV, Opcodes.IREM, Opcodes.ISHL, Opcodes.ISHR, Opcodes.IUSHR,
            Opcodes.IAND, Opcodes.IOR, Opcodes.IXOR ->
                NullabilityValue.Primitive1
            Opcodes.FALOAD, Opcodes.FADD, Opcodes.FSUB, Opcodes.FMUL, Opcodes.FDIV, Opcodes.FREM ->
                NullabilityValue.Primitive1
            Opcodes.LALOAD, Opcodes.LADD, Opcodes.LSUB, Opcodes.LMUL, Opcodes.LDIV, Opcodes.LREM,
            Opcodes.LSHL, Opcodes.LSHR, Opcodes.LUSHR, Opcodes.LAND, Opcodes.LOR, Opcodes.LXOR ->
                NullabilityValue.Primitive2
            Opcodes.DALOAD, Opcodes.DADD, Opcodes.DSUB, Opcodes.DMUL, Opcodes.DDIV, Opcodes.DREM ->
                NullabilityValue.Primitive2
            Opcodes.AALOAD ->
                NullabilityValue.Nullable(AsmTypes.OBJECT_TYPE)
            Opcodes.LCMP, Opcodes.FCMPL, Opcodes.FCMPG, Opcodes.DCMPL, Opcodes.DCMPG ->
                NullabilityValue.Primitive1
            Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE,
            Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE, Opcodes.PUTFIELD ->
                null
            else ->
                throw IllegalArgumentException("Unexpected instruction: " + insn.insnOpcodeText)
        }
    }

    override fun ternaryOperation(
        insn: AbstractInsnNode,
        value1: NullabilityValue,
        value2: NullabilityValue,
        value3: NullabilityValue
    ): NullabilityValue? =
        null

    override fun naryOperation(insn: AbstractInsnNode, values: List<NullabilityValue>): NullabilityValue? =
        when (insn.opcode) {
            Opcodes.MULTIANEWARRAY ->
                NullabilityValue.NotNull(Type.getType((insn as MultiANewArrayInsnNode).desc))
            Opcodes.INVOKEDYNAMIC ->
                NullabilityValue.NotNull(Type.getReturnType((insn as InvokeDynamicInsnNode).desc))
            else ->
                when {
                    insn.isBoxing(generationState) ->
                        NullabilityValue.NotNull(Type.getReturnType((insn as MethodInsnNode).desc))
                    insn.isPseudo(PseudoInsn.AS_NOT_NULL) ->
                        when (val v0 = values[0]) {
                            is NullabilityValue.Null ->
                                NullabilityValue.NotNull(AsmTypes.OBJECT_TYPE)
                            is NullabilityValue.NotNull ->
                                v0
                            is NullabilityValue.Nullable ->
                                NullabilityValue.NotNull(v0.type)
                            else ->
                                v0
                        }
                    else ->
                        newValue(Type.getReturnType((insn as MethodInsnNode).desc))
                }
        }

    override fun returnOperation(insn: AbstractInsnNode, value: NullabilityValue, expected: NullabilityValue) {
    }

    override fun unaryOperation(insn: AbstractInsnNode, value: NullabilityValue): NullabilityValue? =
        when (insn.opcode) {
            Opcodes.INEG, Opcodes.IINC, Opcodes.L2I, Opcodes.F2I, Opcodes.D2I, Opcodes.I2B, Opcodes.I2C, Opcodes.I2S ->
                NullabilityValue.Primitive1
            Opcodes.FNEG, Opcodes.I2F, Opcodes.L2F, Opcodes.D2F ->
                NullabilityValue.Primitive1
            Opcodes.LNEG, Opcodes.I2L, Opcodes.F2L, Opcodes.D2L ->
                NullabilityValue.Primitive2
            Opcodes.DNEG, Opcodes.I2D, Opcodes.L2D, Opcodes.F2D ->
                NullabilityValue.Primitive2
            Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE,
            Opcodes.TABLESWITCH, Opcodes.LOOKUPSWITCH,
            Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN, Opcodes.PUTSTATIC ->
                null
            Opcodes.GETFIELD ->
                newValue(Type.getType((insn as FieldInsnNode).desc))
                    ?: throw AssertionError("Unexpected void value: ${insn.insnText}")
            Opcodes.NEWARRAY ->
                when (val operand = (insn as IntInsnNode).operand) {
                    Opcodes.T_BOOLEAN ->
                        notNullBooleanArray
                    Opcodes.T_CHAR ->
                        notNullCharArray
                    Opcodes.T_BYTE ->
                        notNullByteArray
                    Opcodes.T_SHORT ->
                        notNullShortArray
                    Opcodes.T_INT ->
                        notNullIntArray
                    Opcodes.T_FLOAT ->
                        notNullFloatArray
                    Opcodes.T_DOUBLE ->
                        notNullDoubleArray
                    Opcodes.T_LONG ->
                        notNullLongArray
                    else ->
                        throw AnalyzerException(insn, "Invalid array type: $operand")
                }
            Opcodes.ANEWARRAY ->
                NullabilityValue.NotNull(Type.getType("[" + Type.getObjectType((insn as TypeInsnNode).desc)))
            Opcodes.ARRAYLENGTH ->
                NullabilityValue.Primitive1
            Opcodes.ATHROW ->
                null
            Opcodes.CHECKCAST -> {
                val castType = Type.getObjectType((insn as TypeInsnNode).desc)
                if (insn.isReifiedSafeAs())
                    NullabilityValue.Nullable(castType)
                else {
                    when (value) {
                        is NullabilityValue.NotNull ->
                            NullabilityValue.NotNull(castType)
                        is NullabilityValue.Nullable ->
                            NullabilityValue.Nullable(castType)
                        else ->
                            value
                    }
                }
            }
            Opcodes.INSTANCEOF ->
                NullabilityValue.Primitive1
            Opcodes.MONITORENTER, Opcodes.MONITOREXIT, Opcodes.IFNULL, Opcodes.IFNONNULL ->
                null
            else ->
                throw IllegalArgumentException("Unexpected instruction: " + insn.insnOpcodeText)
        }

    private fun AbstractInsnNode.isReifiedSafeAs(): Boolean {
        val marker = previous as? MethodInsnNode ?: return false
        return ReifiedTypeInliner.isOperationReifiedMarker(marker)
                && marker.operationKind == ReifiedTypeInliner.OperationKind.SAFE_AS
    }

    override fun merge(v: NullabilityValue, w: NullabilityValue): NullabilityValue =
        when (v) {
            NullabilityValue.Primitive1 ->
                if (w == NullabilityValue.Primitive1)
                    NullabilityValue.Primitive1
                else
                    NullabilityValue.Any
            NullabilityValue.Primitive2 ->
                if (w == NullabilityValue.Primitive2)
                    NullabilityValue.Primitive2
                else
                    NullabilityValue.Any
            NullabilityValue.Null ->
                when (w) {
                    NullabilityValue.Primitive1, NullabilityValue.Primitive2, NullabilityValue.Any ->
                        NullabilityValue.Any
                    NullabilityValue.Null ->
                        NullabilityValue.Null
                    is NullabilityValue.NotNull ->
                        NullabilityValue.Nullable(w.type)
                    is NullabilityValue.Nullable ->
                        w
                }
            is NullabilityValue.NotNull ->
                when (w) {
                    NullabilityValue.Primitive1, NullabilityValue.Primitive2, NullabilityValue.Any ->
                        NullabilityValue.Any
                    NullabilityValue.Null ->
                        NullabilityValue.Nullable(v.type)
                    is NullabilityValue.NotNull ->
                        if (v.type == w.type)
                            v
                        else
                            NullabilityValue.NotNull(AsmTypes.OBJECT_TYPE)
                    is NullabilityValue.Nullable ->
                        if (v.type == w.type)
                            w
                        else
                            NullabilityValue.Nullable(AsmTypes.OBJECT_TYPE)
                }
            is NullabilityValue.Nullable ->
                when (w) {
                    NullabilityValue.Primitive1, NullabilityValue.Primitive2, NullabilityValue.Any ->
                        NullabilityValue.Any
                    NullabilityValue.Null ->
                        v
                    is NullabilityValue.NotNull ->
                        if (v.type == w.type)
                            v
                        else
                            NullabilityValue.Nullable(AsmTypes.OBJECT_TYPE)
                    is NullabilityValue.Nullable ->
                        if (v.type == w.type)
                            v
                        else
                            NullabilityValue.Nullable(AsmTypes.OBJECT_TYPE)
                }
            NullabilityValue.Any ->
                NullabilityValue.Any
        }

}
