/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.lower

import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.IrReturnableBlockSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name

/**
 * Replaces returnable blocks and `return`'s with loops and `break`'s correspondingly.
 *
 * Converts returnable blocks into regular composite blocks when the only `return` is the last statement.
 *
 * ```
 * block {
 *   ...
 *   return@block e
 *   ...
 * }
 * ```
 *
 * is transformed into
 *
 * ```
 * {
 *   val result
 *   loop@ do {
 *     ...
 *     {
 *       result = e
 *       break@loop
 *     }
 *     ...
 *   } while (false)
 *   result
 * }
 * ```
 *
 * When the only `return` for the block is the last statement:
 *
 * ```
 * block {
 *   ...
 *   return@block e
 * }
 * ```
 *
 * is transformed into
 *
 * {
 *   ...
 *   e
 * }
 *
 */
class ReturnableBlockLowering2(val context: CommonBackendContext) : BodyLoweringPass {
    override fun lower(irBody: IrBody, container: IrDeclaration) {
        container.transform(ReturnableBlockTransformer2(context), null)
    }
}

class ReturnableBlockTransformer2(val context: CommonBackendContext) : IrElementTransformerVoidWithContext() {
    private var labelCnt = 0
    /// TODO doesn't work well for case when return from deeply nested blocks
    private var retVariable: IrVariable? = null
//    private var map = mutableMapOf<IrReturnableBlockSymbol, IrVariable>()

    private val unitType = context.irBuiltIns.unitType
    private val unitValue get() = IrGetObjectValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, unitType, context.irBuiltIns.unitClass)

    override fun visitReturn(expression: IrReturn): IrExpression {
        expression.transformChildrenVoid()

        val targetSymbol = expression.returnTargetSymbol
        //TODO move to backend.js use `expression.value.isGetUnit()`
        if (targetSymbol !is IrReturnableBlockSymbol/* || targetSymbol.owner.type.isUnit()*/) return expression

        return IrCompositeImpl(
            -1,-1,
            context.irBuiltIns.nothingType,
            null,
            listOf(
                expression.run {
                    if (retVariable == null) {
                        retVariable = IrVariableImpl(
                            -1, -1, IrDeclarationOrigin.IR_TEMPORARY_VARIABLE, IrVariableSymbolImpl(), Name.identifier("tmp"),
                            targetSymbol.owner.type, isVar = true, isConst = false, isLateinit = false
                        ).apply {
                            parent = currentDeclarationParent ?: (allScopes.lastOrNull { (it.irElement as? IrDeclaration)?.parent is IrDeclarationParent }?.irElement as IrDeclaration).parent
                        }
                    }
//                    val variable = map.getOrPut(targetSymbol) {
//                        IrVariableImpl(
//                            -1, -1, IrDeclarationOrigin.IR_TEMPORARY_VARIABLE, IrVariableSymbolImpl(), Name.identifier("tmp" + labelCnt++),
//                            targetSymbol.owner.type, isVar = true, isConst = false, isLateinit = false)
//                    }

                    IrSetValueImpl(startOffset, endOffset, value.type, retVariable!!.symbol, value, null)
                },
                expression.run {
                    IrReturnImpl(-1, -1, type, returnTargetSymbol, unitValue)
                }
            )
        )
    }

    override fun visitBlock(expression: IrBlock): IrExpression {
        if (expression !is IrReturnableBlock) return super.visitBlock(expression)

        val before = retVariable
        retVariable= null

        expression.transformChildrenVoid()

        val variable = retVariable
        retVariable = before

//        val variable = map[expression.symbol]

        if (variable == null) return expression

//        map.remove(expression.symbol)

        expression.type = context.irBuiltIns.unitType
        return IrCompositeImpl(
            -1, -1,
            variable.type,
            null,
            listOf(
                variable,
                expression,
                IrGetValueImpl(-1, -1, variable.symbol)
            )
        )
    }
}
