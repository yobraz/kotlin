/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.declarations.lazy

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.util.DeclarationStubGenerator
import org.jetbrains.kotlin.ir.util.TypeTranslator
import kotlin.properties.ReadWriteProperty

interface IrLazyDeclarationBase {
    val descriptor: DeclarationDescriptor

    val stubGenerator: DeclarationStubGenerator

    val typeTranslator: TypeTranslator

    val factory: IrFactory

    val origin: IrDeclarationOrigin
}

internal fun IrLazyDeclarationBase.generateReceiverParameterStub(receiver: ReceiverParameterDescriptor): IrValueParameter =
    factory.createValueParameter(
        UNDEFINED_OFFSET,
        UNDEFINED_OFFSET,
        origin,
        IrValueParameterSymbolImpl(receiver),
        receiver.name,
        -1,
        typeTranslator.translateType(receiver.type),
        null,
        isCrossinline = false,
        isNoinline = false,
        isHidden = false,
        isAssignable = false
    )

internal fun IrLazyDeclarationBase.createLazyAnnotations(): ReadWriteProperty<Any?, List<IrConstructorCall>> = lazyVar(stubGenerator.lock) {
    descriptor.annotations.mapNotNull(typeTranslator.constantValueGenerator::generateAnnotationConstructorCall).toMutableList()
}

internal fun IrLazyDeclarationBase.createLazyParent(): ReadWriteProperty<Any?, IrDeclarationParent> = lazyVar(stubGenerator.lock) {
    val currentDescriptor = descriptor

    val containingDeclaration =
        ((currentDescriptor as? PropertyAccessorDescriptor)?.correspondingProperty ?: currentDescriptor).containingDeclaration

    when (containingDeclaration) {
        is PackageFragmentDescriptor -> run {
            val parent = this.takeUnless { it is IrClass }?.let {
                stubGenerator.generateOrGetFacadeClass(descriptor)
            } ?: stubGenerator.generateOrGetEmptyExternalPackageFragmentStub(containingDeclaration)
            parent.declarations.add(this as IrDeclaration)
            parent
        }
        is ClassDescriptor -> stubGenerator.generateClassStub(containingDeclaration)
        is FunctionDescriptor -> stubGenerator.generateFunctionStub(containingDeclaration)
        is PropertyDescriptor -> stubGenerator.generateFunctionStub(containingDeclaration.run { getter ?: setter!! })
        else -> throw AssertionError("Package or class expected: $containingDeclaration; for $currentDescriptor")
    }
}
