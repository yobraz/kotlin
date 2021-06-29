/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.lower

import org.jetbrains.kotlin.backend.common.BackendContext
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.ir.addChild
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.ir.createImplicitParameterDeclarationWithWrappedDescriptor
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrSetFieldImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.isKClass
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.Name

val ANNOTATION_IMPLEMENTATION = object : IrDeclarationOriginImpl("ANNOTATION_IMPLEMENTATION", isSynthetic = true) {}

open class AnnotationImplementationLowering(val context: BackendContext) : FileLoweringPass, IrElementTransformerVoidWithContext() {
    private val implementations: MutableMap<IrClass, IrClass> = mutableMapOf()

    private var startOffset = UNDEFINED_OFFSET
    private var endOffset = UNDEFINED_OFFSET

    private lateinit var curFile: IrFile

    override fun lower(irFile: IrFile) {
        implementations.clear()
        curFile = irFile
        irFile.transformChildrenVoid()
        // reassign startOffset, endOffset appropriately?
        implementations.values.forEach { irFile.addChild(it) }
    }

    override fun visitConstructorCall(expression: IrConstructorCall): IrExpression {
        val constructedClass = expression.type.classOrNull?.owner ?: return expression
        if (!constructedClass.isAnnotationClass) return expression
        if (constructedClass.typeParameters.isNotEmpty()) return expression // Not supported yet

        val implClass = implementations.getOrPut(constructedClass) { createAnnotationImplementation(constructedClass) }
        val ctor = implClass.constructors.single()
        val newCall = IrConstructorCallImpl.fromSymbolOwner(
            expression.startOffset,
            expression.endOffset,
            implClass.defaultType,
            ctor.symbol,
        )
        newCall.copyTypeAndValueArgumentsFrom(expression)
        newCall.transformChildrenVoid() // for annotations in annotations
        return newCall
    }

    private fun createAnnotationImplementation(annotationClass: IrClass): IrClass {
        val wrapperName = Name.identifier(annotationClass.fqNameWhenAvailable!!.asString().replace('.', '_') + "Impl")
        val subclass = context.irFactory.buildClass {
            name = wrapperName
            origin = ANNOTATION_IMPLEMENTATION
            visibility = DescriptorVisibilities.PRIVATE
        }.apply {
            parent = curFile
            createImplicitParameterDeclarationWithWrappedDescriptor()
            superTypes = listOf(annotationClass.defaultType)
        }

        val ctor = subclass.addConstructor {
            visibility = DescriptorVisibilities.PUBLIC
        }
        val props = implementAnnotationProperties(subclass, annotationClass, ctor)
        implementEqualsAndHashCode(annotationClass, subclass, props)
        implementPlatformSpecificParts(annotationClass, subclass)
        return subclass
    }

    fun implementAnnotationProperties(implClass: IrClass, annotationClass: IrClass, generatedConstructor: IrConstructor): List<IrProperty> {
        val ctorBody = context.irFactory.createBlockBody(
            startOffset, endOffset, listOf(
                IrDelegatingConstructorCallImpl(
                    startOffset, endOffset, context.irBuiltIns.unitType, context.irBuiltIns.anyClass.constructors.single(),
                    typeArgumentsCount = 0, valueArgumentsCount = 0
                )
            )
        )

        generatedConstructor.body = ctorBody

        val properties = annotationClass.declarations.filterIsInstance<IrProperty>()

        return properties.map { property ->

            val propType = property.getter!!.returnType
            val propName = property.name
            val field = context.irFactory.buildField {
                name = propName
                type = propType
                origin = ANNOTATION_IMPLEMENTATION
                isFinal = true
                visibility = DescriptorVisibilities.PRIVATE
            }.also { it.parent = implClass }

            val parameter = generatedConstructor.addValueParameter(propName.asString(), propType)

            ctorBody.statements += IrSetFieldImpl(
                startOffset, endOffset, field.symbol,
                IrGetValueImpl(startOffset, endOffset, implClass.thisReceiver!!.symbol),
                IrGetValueImpl(startOffset, endOffset, parameter.symbol),
                context.irBuiltIns.unitType,
            )

            val prop = implClass.addProperty {
                name = propName
                isVar = false
                origin = ANNOTATION_IMPLEMENTATION
            }.apply {
                backingField = field// .also { it.parent = this }
                parent = implClass
            }

            prop.addGetter {
                name = propName  // Annotation value getter should be named 'x', not 'getX'
                returnType = propType.kClassToJClassIfNeeded() // On JVM, annotation store j.l.Class even if declared with KClass
                origin = ANNOTATION_IMPLEMENTATION
                visibility = property.visibility
                modality = Modality.FINAL
            }.apply {
                dispatchReceiverParameter = implClass.thisReceiver!!.copyTo(this)
                body = context.createIrBuilder(symbol).irBlockBody {
                    var value: IrExpression = irGetField(irGet(dispatchReceiverParameter!!), field)
                    if (propType.isKClass()) value = this.kClassExprToJClassIfNeeded(value)
                    +irReturn(value)
                }
            }

            prop
        }

    }

    open fun IrType.kClassToJClassIfNeeded(): IrType = this

    open fun IrBuilderWithScope.kClassExprToJClassIfNeeded(irExpression: IrExpression): IrExpression = irExpression

    open fun generatedEquals(irBuilder: IrBlockBodyBuilder, type: IrType, arg1: IrExpression, arg2: IrExpression): IrExpression {
        return irBuilder.irEquals(arg1, arg2)
    }

    @Suppress("UNUSED_VARIABLE")
    fun implementEqualsAndHashCode(annotationClass: IrClass, implClass: IrClass, props: List<IrProperty>) {
        val creator = MethodsFromAnyGeneratorForLowerings(context, implClass, ANNOTATION_IMPLEMENTATION)
        val generator =
            creator.LoweringDataClassMemberGenerator("@" + annotationClass.fqNameWhenAvailable!!.asString()) { type, a, b ->
                generatedEquals(this, type, a, b)
            }

        val eqFun = creator.createEqualsMethodDeclaration()
        generator.generateEqualsMethod(eqFun, props)

        val hcFun = creator.createHashCodeMethodDeclaration()
        generator.generateHashCodeMethod(hcFun, props)

        val toStringFun = creator.createToStringMethodDeclaration()
        generator.generateToStringMethod(toStringFun, props)
    }

    open fun implementPlatformSpecificParts(annotationClass: IrClass, implClass: IrClass) {}
}

class MethodsFromAnyGeneratorForLowerings(val context: BackendContext, val irClass: IrClass, val origin: IrDeclarationOrigin? = null) {
    fun createToStringMethodDeclaration(): IrSimpleFunction = irClass.addFunction("toString", context.irBuiltIns.stringType).apply {
        overriddenSymbols = irClass.collectOverridenSymbols { it.isToString() }
    }

    fun createHashCodeMethodDeclaration(): IrSimpleFunction = irClass.addFunction("hashCode", context.irBuiltIns.intType).apply {
        overriddenSymbols = irClass.collectOverridenSymbols { it.isHashCode() }
    }

    fun createEqualsMethodDeclaration(): IrSimpleFunction = irClass.addFunction("equals", context.irBuiltIns.booleanType).apply {
        overriddenSymbols = irClass.collectOverridenSymbols { it.isEquals(context) }
        addValueParameter("other", context.irBuiltIns.anyNType)
    }

    inner class LoweringDataClassMemberGenerator(
        val nameForToString: String,
        val selectEquals: IrBlockBodyBuilder.(IrType, IrExpression, IrExpression) -> IrExpression,
    ) :
        DataClassMembersGenerator(
            IrLoweringContext(context),
            context.ir.symbols.externalSymbolTable,
            irClass,
            origin ?: IrDeclarationOrigin.DEFINED
        ) {

        constructor(nameForToString: String = this@MethodsFromAnyGeneratorForLowerings.irClass.name.asString()) : this(
            nameForToString,
            selectEquals = { _, arg1, arg2 ->
                irNotEquals(
                    arg1,
                    arg2
                )
            })

        override fun declareSimpleFunction(startOffset: Int, endOffset: Int, functionDescriptor: FunctionDescriptor): IrFunction {
            error("Descriptor API shouldn't be used in lowerings")
        }

        override fun generateSyntheticFunctionParameterDeclarations(irFunction: IrFunction) {
            // no-op — irFunction from lowering should already have necessary parameters
        }

        override fun getProperty(parameter: ValueParameterDescriptor?, irValueParameter: IrValueParameter?): IrProperty? {
            error("Descriptor API shouldn't be used in lowerings")
        }

        override fun transform(typeParameterDescriptor: TypeParameterDescriptor): IrType {
            error("Descriptor API shouldn't be used in lowerings")
        }

        override fun getHashCodeFunctionInfo(type: IrType): DataClassMembersGenerator.HashCodeFunctionInfo {
            val symbol = getHashCodeFunctionSymbol(type)
            return object : HashCodeFunctionInfo {
                override val symbol: IrSimpleFunctionSymbol = symbol

                override fun commitSubstituted(irMemberAccessExpression: IrMemberAccessExpression<*>) {}
            }
        }

        override fun IrBlockBodyBuilder.notEqualsExpression(type: IrType, arg1: IrExpression, arg2: IrExpression): IrExpression {
            return irNot(selectEquals(type, arg1, arg2))
        }

        override fun IrClass.classNameForToString(): String = nameForToString
    }

    companion object {
        fun IrFunction.isToString(): Boolean =
            name.asString() == "toString" && extensionReceiverParameter == null && valueParameters.isEmpty()

        fun IrFunction.isHashCode() =
            name.asString() == "hashCode" && extensionReceiverParameter == null && valueParameters.isEmpty()

        fun IrFunction.isEquals(context: BackendContext) =
            name.asString() == "equals" &&
                    extensionReceiverParameter == null &&
                    valueParameters.singleOrNull()?.type == context.irBuiltIns.anyNType


        fun IrClass.collectOverridenSymbols(predicate: (IrFunction) -> Boolean): List<IrSimpleFunctionSymbol> =
            superTypes.mapNotNull { it.getClass()?.functions?.singleOrNull(predicate)?.symbol }

    }
}
