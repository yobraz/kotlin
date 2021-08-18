/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.interpreter

import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrDelegatingConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrEnumConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.interpreter.proxy.Proxy
import org.jetbrains.kotlin.ir.interpreter.stack.CallStack
import org.jetbrains.kotlin.ir.interpreter.state.Common
import org.jetbrains.kotlin.ir.interpreter.state.Complex
import org.jetbrains.kotlin.ir.interpreter.state.Primitive
import org.jetbrains.kotlin.ir.interpreter.state.State
import org.jetbrains.kotlin.ir.interpreter.state.Wrapper
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.isSubclassOf
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.ir.util.nameForIrSerialization
import org.jetbrains.kotlin.name.Name

class IrInterpreterEnvironment(
    val irBuiltIns: IrBuiltIns,
    val configuration: IrInterpreterConfiguration = IrInterpreterConfiguration(),
) {
    internal val callStack: CallStack = CallStack()
    internal val irExceptions = mutableListOf<IrClass>()
    internal var mapOfEnums = mutableMapOf<IrSymbol, Complex>()
    internal var mapOfObjects = mutableMapOf<IrSymbol, Complex>()
    internal var javaClassToIrClass = mutableMapOf<Class<*>, IrClass>()
    private var functionCache = mutableMapOf<CacheFunctionSignature, IrFunctionSymbol>()

    internal val kTypeParameterClass by lazy { irBuiltIns.kClassClass.getIrClassOfReflectionFromList("typeParameters")!! }
    internal val kParameterClass by lazy { irBuiltIns.kFunctionClass.getIrClassOfReflectionFromList("parameters")!! }
    internal val kTypeProjectionClass by lazy { kTypeClass.getIrClassOfReflectionFromList("arguments")!! }
    internal val kTypeClass: IrClassSymbol by lazy {
        // here we use fallback to `Any` because `KType` cannot be found on JS/Native by this way
        // but still this class is used to represent type arguments in interpreter
        irBuiltIns.kClassClass.getIrClassOfReflectionFromList("supertypes") ?: irBuiltIns.anyClass
    }

    init {
        mapOfObjects[irBuiltIns.unitClass] = Common(irBuiltIns.unitClass.owner)
    }

    private data class CacheFunctionSignature(
        val symbol: IrFunctionSymbol,

        // must create different invoke function for function expression with and without receivers
        val hasDispatchReceiver: Boolean,
        val hasExtensionReceiver: Boolean,

        // must create different default functions for constructor call and delegating call;
        // their symbols are the same but calls are different, so default function must return different calls
        val fromDelegatingCall: Boolean
    )

    private constructor(environment: IrInterpreterEnvironment) : this(environment.irBuiltIns, configuration = environment.configuration) {
        irExceptions.addAll(environment.irExceptions)
        mapOfEnums = environment.mapOfEnums
        mapOfObjects = environment.mapOfObjects
    }

    constructor(irModule: IrModuleFragment) : this(irModule.irBuiltins) {
        irExceptions.addAll(
            irModule.files
                .flatMap { it.declarations }
                .filterIsInstance<IrClass>()
                .filter { it.isSubclassOf(irBuiltIns.throwableClass.owner) }
        )
    }

    fun copyWithNewCallStack(): IrInterpreterEnvironment {
        return IrInterpreterEnvironment(this)
    }

    internal fun getCachedFunction(
        symbol: IrFunctionSymbol,
        hasDispatchReceiver: Boolean = false,
        hasExtensionReceiver: Boolean = false,
        fromDelegatingCall: Boolean = false
    ): IrFunctionSymbol? {
        return functionCache[CacheFunctionSignature(symbol, hasDispatchReceiver, hasExtensionReceiver, fromDelegatingCall)]
    }

    internal fun setCachedFunction(
        symbol: IrFunctionSymbol,
        hasDispatchReceiver: Boolean = false,
        hasExtensionReceiver: Boolean = false,
        fromDelegatingCall: Boolean = false,
        newFunction: IrFunctionSymbol
    ): IrFunctionSymbol {
        functionCache[CacheFunctionSignature(symbol, hasDispatchReceiver, hasExtensionReceiver, fromDelegatingCall)] = newFunction
        return newFunction
    }

    internal fun getOrCreateCallWithDefaults(expression: IrFunctionAccessExpression): IrCall {
        getCachedFunction(expression.symbol, fromDelegatingCall = expression is IrDelegatingConstructorCall)?.let {
            return it.owner.createCall().apply { irBuiltIns.copyArgs(expression, this) }
        }

        // if some arguments are not defined, then it is necessary to create temp function where defaults will be evaluated
        val actualParameters = MutableList<IrValueDeclaration?>(expression.valueArgumentsCount) { null }
        val ownerWithDefaults = expression.getFunctionThatContainsDefaults()
        val visibility = when (expression) {
            is IrEnumConstructorCall, is IrDelegatingConstructorCall -> DescriptorVisibilities.LOCAL
            else -> ownerWithDefaults.visibility
        }

        val defaultFun = createTempFunction(
            Name.identifier(ownerWithDefaults.name.asString() + "\$default"), ownerWithDefaults.returnType,
            origin = IrDeclarationOrigin.FUNCTION_FOR_DEFAULT_PARAMETER, visibility
        ).apply {
            this.parent = ownerWithDefaults.parent
            this.dispatchReceiverParameter = ownerWithDefaults.dispatchReceiverParameter?.deepCopyWithSymbols(this)
            this.extensionReceiverParameter = ownerWithDefaults.extensionReceiverParameter?.deepCopyWithSymbols(this)
            (0 until expression.valueArgumentsCount).forEach { index ->
                val originalParameter = ownerWithDefaults.valueParameters[index]
                val copiedParameter = originalParameter.deepCopyWithSymbols(this)
                this.valueParameters += copiedParameter
                actualParameters[index] = if (copiedParameter.defaultValue != null || copiedParameter.isVararg) {
                    copiedParameter.type = copiedParameter.type.makeNullable() // make nullable type to keep consistency; parameter can be null if it is missing
                    val irGetParameter = copiedParameter.createGetValue()
                    // if parameter is vararg and it is missing, then create constructor call for empty array
                    val defaultInitializer = originalParameter.getDefaultWithActualParameters(this@apply, actualParameters)
                        ?: irBuiltIns.emptyArrayConstructor(expression.getVarargType(index)!!.getTypeIfReified(callStack))

                    copiedParameter.createTempVariable().apply variable@{
                        this@variable.initializer = irBuiltIns.irIfNullThenElse(irGetParameter, defaultInitializer, irGetParameter)
                    }
                } else {
                    copiedParameter
                }
            }
        }

        val callWithAllArgs = expression.shallowCopy() // just a copy of given call, but with all arguments in place
        expression.dispatchReceiver?.let { callWithAllArgs.dispatchReceiver = defaultFun.dispatchReceiverParameter!!.createGetValue() }
        expression.extensionReceiver?.let { callWithAllArgs.extensionReceiver = defaultFun.extensionReceiverParameter!!.createGetValue() }
        (0 until expression.valueArgumentsCount).forEach { callWithAllArgs.putValueArgument(it, actualParameters[it]?.createGetValue()) }
        defaultFun.body = (actualParameters.filterIsInstance<IrVariable>() + defaultFun.createReturn(callWithAllArgs)).wrapWithBlockBody()

        return setCachedFunction(
            expression.symbol, fromDelegatingCall = expression is IrDelegatingConstructorCall, newFunction = defaultFun.symbol
        ).owner.createCall().apply { irBuiltIns.copyArgs(expression, this) }
    }

    /**
     * Convert object from outer world to state
     */
    internal fun convertToState(value: Any?, irType: IrType): State {
        return when (value) {
            is Proxy -> value.state
            is State -> value
            is Boolean, is Char, is Byte, is Short, is Int, is Long, is String, is Float, is Double, is Array<*>, is ByteArray,
            is CharArray, is ShortArray, is IntArray, is LongArray, is FloatArray, is DoubleArray, is BooleanArray -> Primitive(value, irType)
            null -> Primitive.nullStateOfType(irType)
            else -> irType.classOrNull?.owner?.let { Wrapper(value, it, this) }
                ?: Wrapper(value, this.javaClassToIrClass[value::class.java]!!, this)
        }
    }

    private fun IrClassSymbol.getIrClassOfReflectionFromList(name: String): IrClassSymbol? {
        val property = this.owner.declarations.singleOrNull { it.nameForIrSerialization.asString() == name } as? IrProperty
        val list = property?.getter?.returnType as? IrSimpleType
        return list?.arguments?.single()?.typeOrNull?.classOrNull
    }
}
