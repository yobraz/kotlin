/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.lazy

import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.backend.Fir2IrComponents
import org.jetbrains.kotlin.fir.backend.toIrType
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.symbols.Fir2IrBindableSymbol
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.declarations.lazy.lazyVar
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import kotlin.properties.ReadWriteProperty

interface AbstractFir2IrLazyDeclaration<F, D : IrDeclaration> :
    IrDeclarationParent, Fir2IrComponents where F : FirMemberDeclaration, F : FirAnnotationContainer {

    val fir: F

    val symbol: Fir2IrBindableSymbol<*, D>

    var typeParameters: List<IrTypeParameter>
}

internal fun <F, D : IrDeclaration> AbstractFir2IrLazyDeclaration<F, D>.prepareTypeParameters()
        where F : FirMemberDeclaration, F : FirAnnotationContainer {
    typeParameters = fir.typeParameters.mapIndexedNotNull { index, typeParameter ->
        if (typeParameter !is FirTypeParameter) return@mapIndexedNotNull null
        classifierStorage.getIrTypeParameter(typeParameter, index).apply {
            parent = this@prepareTypeParameters
            if (superTypes.isEmpty()) {
                superTypes = typeParameter.bounds.map { it.toIrType(typeConverter) }
            }
        }
    }
}

internal fun <F, D : IrDeclaration> AbstractFir2IrLazyDeclaration<F, D>.createLazyAnnotations():
        ReadWriteProperty<Any?, List<IrConstructorCall>> where F : FirMemberDeclaration, F : FirAnnotationContainer = lazyVar(lock) {
    fir.annotations.mapNotNull {
        callGenerator.convertToIrConstructorCall(it) as? IrConstructorCall
    }
}
