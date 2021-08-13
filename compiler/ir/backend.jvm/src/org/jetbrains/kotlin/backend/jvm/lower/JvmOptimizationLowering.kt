/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irBlock
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.ir.createJvmIrBuilder
import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrPublicSymbolBase
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.types.isNullableAny
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer

internal val jvmOptimizationLoweringPhase = makeIrFilePhase(
    ::JvmOptimizationLowering,
    name = "JvmOptimizationLowering",
    description = "Optimize code for JVM code generation"
)

class JvmOptimizationLowering(val context: JvmBackendContext) : FileLoweringPass {

    companion object {
        fun isNegation(expression: IrExpression, context: JvmBackendContext): Boolean =
            expression is IrCall &&
                    (expression.symbol as? IrPublicSymbolBase<*>)?.signature == context.irBuiltIns.booleanNotSymbol.signature
    }

    private val IrFunction.isObjectEquals
        get() = name.asString() == "equals" &&
                valueParameters.count() == 1 &&
                valueParameters[0].type.isNullableAny() &&
                extensionReceiverParameter == null &&
                dispatchReceiverParameter != null


    private fun getOperandsIfCallToEQEQOrEquals(call: IrCall): Pair<IrExpression, IrExpression>? =
        when {
            call.symbol == context.irBuiltIns.eqeqSymbol -> {
                val left = call.getValueArgument(0)!!
                val right = call.getValueArgument(1)!!
                left to right
            }

            call.symbol.owner.isObjectEquals -> {
                val left = call.dispatchReceiver!!
                val right = call.getValueArgument(0)!!
                left to right
            }

            else -> null
        }

    private class InvertedSafeCall(
        val scopeSymbol: IrSymbol,
        val tmpVal: IrVariable?,
        val eqeqNullCall: IrCall,
        val ifNotNullBranch: IrBranch
    )

    private fun parseInvertedSafeCall(expression: IrExpression): InvertedSafeCall? {
        val block = expression as? IrBlock ?: return null
        if (block.statements.size > 2) return null

        val tmpVal = block.statements.first() as? IrVariable

        val whenExpr = block.statements.last() as? IrWhen ?: return null
        if (whenExpr.branches.size != 2) return null

        val branch0 = whenExpr.branches[0]
        val branch0Condition = branch0.condition
        if (branch0Condition !is IrCall) return null
        if (branch0Condition.symbol != context.irBuiltIns.booleanNotSymbol) return null
        val eqeqNullCall = branch0Condition.dispatchReceiver ?: return null
        if (eqeqNullCall !is IrCall || eqeqNullCall.symbol != context.irBuiltIns.eqeqSymbol) return null
        val arg0 = eqeqNullCall.getValueArgument(0)
        if (arg0 !is IrGetValue) return null
        if (tmpVal != null && arg0.symbol != tmpVal.symbol) return null
        val argValSymbol = tmpVal?.symbol ?: arg0.symbol
        val arg1 = eqeqNullCall.getValueArgument(1)
        if (arg1 !is IrConst<*> || arg1.value != null) return null
        val branch1Result = whenExpr.branches[1].result
        if (branch1Result !is IrConst<*> || branch1Result.value != null) return null

        val scopeSymbol = (argValSymbol.owner.parent as IrDeclaration).symbol
        return InvertedSafeCall(scopeSymbol, tmpVal, eqeqNullCall, branch0)
    }

    private fun isIfTemporaryEqualsNull(whenExpr: IrWhen): Boolean {
        if (whenExpr.branches.size != 2) return false
        if (whenExpr.origin == IrStatementOrigin.OROR || whenExpr.origin == IrStatementOrigin.ANDAND) return false
        val ifNullBranch = whenExpr.branches[0]
        val ifNullBranchCondition = ifNullBranch.condition
        if (ifNullBranchCondition !is IrCall) return false
        if (ifNullBranchCondition.symbol != context.irBuiltIns.eqeqSymbol) return false
        val arg0 = ifNullBranchCondition.getValueArgument(0)
        if (arg0 !is IrGetValue || !arg0.symbol.owner.isTemporaryVal()) return false
        val arg1 = ifNullBranchCondition.getValueArgument(1)
        if (arg1 !is IrConst<*> || arg1.value != null) return false
        return true
    }

    private fun IrValueDeclaration.isTemporaryVal() =
        this.origin == IrDeclarationOrigin.IR_TEMPORARY_VARIABLE &&
                !(this as IrVariable).isVar

    private fun IrType.isJvmPrimitive(): Boolean =
        // TODO get rid of type mapper (take care of '@EnhancedNullability', maybe some other stuff).
        AsmUtil.isPrimitive(context.typeMapper.mapType(this))

    override fun lower(irFile: IrFile) {
        irFile.transformChildren(Transformer(), null)
    }

    private inner class Transformer : IrElementTransformer<IrClass?> {

        // Thread the current class through the transformations in order to replace
        // final default accessor calls with direct backing field access when
        // possible.
        override fun visitClass(declaration: IrClass, data: IrClass?): IrStatement {
            declaration.transformChildren(this, declaration)
            return declaration
        }

        // For some functions, we clear the current class field since the code could end up
        // in another class then the one it is nested under in the IR.
        // TODO: Loosen this up for local functions for lambdas passed as an inline lambda
        //  argument to an inline function. In that case the code does end up in the current class.
        override fun visitFunction(declaration: IrFunction, data: IrClass?): IrStatement {
            val codeMightBeGeneratedInDifferentClass = declaration.isSuspend ||
                    declaration.isInline ||
                    declaration.origin == IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
            declaration.transformChildren(this, data.takeUnless { codeMightBeGeneratedInDifferentClass })
            return declaration
        }

        override fun visitCall(expression: IrCall, data: IrClass?): IrExpression {
            expression.transformChildren(this, data)

            if (expression.symbol.owner.origin == IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR) {
                if (data == null) return expression
                val simpleFunction = (expression.symbol.owner as? IrSimpleFunction) ?: return expression
                val property = simpleFunction.correspondingPropertySymbol?.owner ?: return expression
                if (property.isLateinit) return expression
                return optimizePropertyAccess(expression, simpleFunction, property, data)
            }

            if (isNegation(expression, context) && isNegation(expression.dispatchReceiver!!, context)) {
                return (expression.dispatchReceiver as IrCall).dispatchReceiver!!
            }

            getOperandsIfCallToEQEQOrEquals(expression)?.let { (left, right) ->
                if (left.isNullConst() && right.isNullConst())
                    return IrConstImpl.constTrue(expression.startOffset, expression.endOffset, context.irBuiltIns.booleanType)

                if (left.isNullConst() && right is IrConst<*> || right.isNullConst() && left is IrConst<*>)
                    return IrConstImpl.constFalse(expression.startOffset, expression.endOffset, context.irBuiltIns.booleanType)

                if (expression.symbol == context.irBuiltIns.eqeqSymbol) {
                    if (right.type.isJvmPrimitive()) {
                        parseInvertedSafeCall(left)?.let {
                            return rewriteSafeCallEqeqPrimitive(it, expression)
                        }
                    }
                    if (left.type.isJvmPrimitive()) {
                        parseInvertedSafeCall(right)?.let {
                            return rewritePrimitiveEqeqSafeCall(it, expression)
                        }
                    }
                }
            }

            return expression
        }


        private fun IrBuilderWithScope.ifSafeHelper(safeCall: InvertedSafeCall, expr: IrExpression): IrExpression =
            irBlock {
                safeCall.tmpVal?.let { +it }
                +irIfThenElse(expr.type, safeCall.eqeqNullCall, irFalse(), expr)
            }

        // Fuse safe call with primitive equality to avoid boxing the primitive. `a?.x == p`:
        //     { val tmp = a; if (tmp != null) tmp.x else null } == p
        // is transformed to:
        //     { val tmp = a; if (tmp == null) false else tmp.x == p }
        // Note that the original IR implied that `p` is always evaluated, but the rewritten version
        // only does so if `a` is not null. This is how the old backend does it, and it's consistent
        // with `a?.x?.equals(p)`.
        private fun rewriteSafeCallEqeqPrimitive(safeCall: InvertedSafeCall, eqeqCall: IrCall): IrExpression =
            context.createJvmIrBuilder(safeCall.scopeSymbol).run {
                ifSafeHelper(
                    safeCall,
                    eqeqCall.apply {
                        putValueArgument(0, safeCall.ifNotNullBranch.result)
                    }
                )
            }

        // Fuse safe call with primitive equality to avoid boxing the primitive. 'p == a?.x':
        //     p == { val tmp = a; if (tmp != null) tmp.x else null }
        // is transformed to:
        //     { val tmp_p = p; { val tmp = a; if (tmp == null) false else p == tmp.x } }
        // Note that `p` is evaluated even if `a` is null, which is again consistent with both the old backend
        // and `p.equals(a?.x)`.
        private fun rewritePrimitiveEqeqSafeCall(safeCall: InvertedSafeCall, eqeqCall: IrCall): IrExpression =
            context.createJvmIrBuilder(safeCall.scopeSymbol).run {
                ifSafeHelper(
                    safeCall,
                    eqeqCall.apply {
                        putValueArgument(1, safeCall.ifNotNullBranch.result)
                    }
                )
            }

        private fun optimizePropertyAccess(
            expression: IrCall,
            accessor: IrSimpleFunction,
            property: IrProperty,
            currentClass: IrClass
        ): IrExpression {
            if (accessor.parentAsClass == currentClass &&
                property.backingField?.parentAsClass == currentClass &&
                accessor.modality == Modality.FINAL &&
                !accessor.isExternal
            ) {
                val backingField = property.backingField!!
                val receiver = expression.dispatchReceiver
                return context.createIrBuilder(expression.symbol, expression.startOffset, expression.endOffset).irBlock(expression) {
                    if (backingField.isStatic && receiver != null && receiver !is IrGetValue) {
                        // If the field is static, evaluate the receiver for potential side effects.
                        +receiver
                    }
                    if (accessor.valueParameters.isNotEmpty()) {
                        +irSetField(
                            receiver.takeUnless { backingField.isStatic },
                            backingField,
                            expression.getValueArgument(expression.valueArgumentsCount - 1)!!
                        )
                    } else {
                        +irGetField(receiver.takeUnless { backingField.isStatic }, backingField)
                    }
                }
            }
            return expression
        }

        override fun visitWhen(expression: IrWhen, data: IrClass?): IrExpression {
            val isCompilerGenerated = expression.origin == null

            // Invert condition in safe call-like expressions:
            //      if (<TEMPORARY_VAL> == null) null else <SAFE_CALL_BODY>
            //      => if (<TEMPORARY_VAL> != null) <SAFE_CALL_BODY> else null
            // Thus we can generate more compact bytecode for safe call chains by reusing the `else null` branches.
            // NB we do this before rewriting subexpressions for `when`, because temporary variables might be rewritten.
            if (isIfTemporaryEqualsNull(expression)) {
                val originalCondition = expression.branches[0].condition
                val originalNullResult = expression.branches[0].result
                val originalAlternativeResult = expression.branches[1].result

                expression.branches[0].condition =
                    IrCallImpl.fromSymbolOwner(
                        expression.startOffset,
                        expression.endOffset,
                        context.irBuiltIns.booleanNotSymbol
                    ).apply {
                        dispatchReceiver = originalCondition
                    }
                expression.branches[0].result = originalAlternativeResult
                expression.branches[1].result = originalNullResult
            }

            expression.transformChildren(this, data)
            // Remove all branches with constant false condition.
            expression.branches.removeIf {
                it.condition.isFalseConst() && isCompilerGenerated
            }
            if (expression.origin == IrStatementOrigin.ANDAND) {
                assert(
                    expression.type.isBoolean()
                            && expression.branches.size == 2
                            && expression.branches[1].condition.isTrueConst()
                            && expression.branches[1].result.isFalseConst()
                ) {
                    "ANDAND condition should have an 'if true then false' body on its second branch. " +
                            "Failing expression: ${expression.dump()}"
                }
                // Replace conjunction condition with intrinsic "and" function call
                return IrCallImpl.fromSymbolOwner(
                    expression.startOffset,
                    expression.endOffset,
                    context.irBuiltIns.andandSymbol
                ).apply {
                    putValueArgument(0, expression.branches[0].condition)
                    putValueArgument(1, expression.branches[0].result)
                }
            }

            if (expression.origin == IrStatementOrigin.OROR) {
                assert(
                    expression.type.isBoolean()
                            && expression.branches.size == 2
                            && expression.branches[0].result.isTrueConst()
                            && expression.branches[1].condition.isTrueConst()
                ) {
                    "OROR condition should have an 'if a then true' body on its first branch, " +
                            "and an 'if true then b' body on its second branch. " +
                            "Failing expression: ${expression.dump()}"
                }
                return IrCallImpl.fromSymbolOwner(
                    expression.startOffset,
                    expression.endOffset,
                    context.irBuiltIns.ororSymbol
                ).apply {
                    putValueArgument(0, expression.branches[0].condition)
                    putValueArgument(1, expression.branches[1].result)
                }
            }

            // If there are no conditions left, replace `when` with an empty block.
            if (expression.branches.size == 0) {
                return IrBlockImpl(expression.startOffset, expression.endOffset, context.irBuiltIns.unitType)
            }

            // If the only condition that is left has a constant true condition, replace `when` with corresponding branch result.
            val firstBranch = expression.branches.first()
            if (firstBranch.condition.isTrueConst() && isCompilerGenerated) {
                return firstBranch.result
            }

            return expression
        }

        private fun IrVariable.getReplacementExpressionForTemporaryValOrNull(): IrExpression? {
            if (this.origin != IrDeclarationOrigin.IR_TEMPORARY_VARIABLE || this.isVar)
                return null
            return when (val initializer = this.initializer) {
                is IrConst<*> ->
                    initializer
                is IrGetValue -> {
                    val value = initializer.symbol.owner
                    if (value !is IrVariable)
                        initializer
                    else if (value.isVar)
                        null
                    else
                        value.getReplacementExpressionForTemporaryValOrNull()
                            ?: initializer
                }
                else ->
                    null
            }
        }

        private fun removeUnnecessaryTemporaryVariables(statements: MutableList<IrStatement>) {
            // Remove declarations for temporary vals that can be replaced with their initializers.
            // See also: visitGetValue
            statements.removeIf {
                it is IrVariable && it.getReplacementExpressionForTemporaryValOrNull() != null
            }

            // Remove a block that contains only two statements:
            // (1) declaration of a temporary variable
            // (2) load of the value of that temporary variable
            // with just the initializer for the temporary variable.
            if (statements.size == 2) {
                val first = statements[0]
                val second = statements[1]
                if (first is IrVariable
                    && first.origin == IrDeclarationOrigin.IR_TEMPORARY_VARIABLE
                    && second is IrGetValue
                    && first.symbol == second.symbol
                ) {
                    statements.clear()
                    first.initializer?.let { statements.add(it) }
                }
            }
        }

        override fun visitBlockBody(body: IrBlockBody, data: IrClass?): IrBody {
            body.transformChildren(this, data)
            removeUnnecessaryTemporaryVariables(body.statements)
            return body
        }

        override fun visitContainerExpression(expression: IrContainerExpression, data: IrClass?): IrExpression {
            expression.transformChildren(this, data)
            removeUnnecessaryTemporaryVariables(expression.statements)
            return expression
        }

        override fun visitGetValue(expression: IrGetValue, data: IrClass?): IrExpression {
            // Replace temporary val read with an initializer if possible.
            val variable = expression.symbol.owner
            if (variable is IrVariable) {
                when (val replacement = variable.getReplacementExpressionForTemporaryValOrNull()) {
                    is IrConst<*> ->
                        return replacement.copyWithOffsets(expression.startOffset, expression.endOffset)
                    is IrGetValue ->
                        return IrGetValueImpl(
                            expression.startOffset, expression.endOffset,
                            replacement.type, replacement.symbol, replacement.origin
                        )
                }
            }
            return expression
        }
    }
}
