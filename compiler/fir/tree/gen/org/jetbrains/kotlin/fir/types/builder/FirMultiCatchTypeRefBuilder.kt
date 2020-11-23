/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.types.builder

import kotlin.contracts.*
import org.jetbrains.kotlin.fir.FirSourceElement
import org.jetbrains.kotlin.fir.builder.FirAnnotationContainerBuilder
import org.jetbrains.kotlin.fir.builder.FirBuilderDsl
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.types.FirMultiCatchTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.impl.FirMultiCatchTypeRefImpl
import org.jetbrains.kotlin.fir.visitors.*

/*
 * This file was generated automatically
 * DO NOT MODIFY IT MANUALLY
 */

@FirBuilderDsl
class FirMultiCatchTypeRefBuilder : FirAnnotationContainerBuilder {
    override var source: FirSourceElement? = null
    override val annotations: MutableList<FirAnnotationCall> = mutableListOf()
    val types: MutableList<FirTypeRef> = mutableListOf()

    override fun build(): FirMultiCatchTypeRef {
        return FirMultiCatchTypeRefImpl(
            source,
            annotations,
            types,
        )
    }

}

@OptIn(ExperimentalContracts::class)
inline fun buildMultiCatchTypeRef(init: FirMultiCatchTypeRefBuilder.() -> Unit = {}): FirMultiCatchTypeRef {
    contract {
        callsInPlace(init, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    return FirMultiCatchTypeRefBuilder().apply(init).build()
}

@OptIn(ExperimentalContracts::class)
inline fun buildMultiCatchTypeRefCopy(original: FirMultiCatchTypeRef, init: FirMultiCatchTypeRefBuilder.() -> Unit = {}): FirMultiCatchTypeRef {
    contract {
        callsInPlace(init, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    val copyBuilder = FirMultiCatchTypeRefBuilder()
    copyBuilder.source = original.source
    copyBuilder.annotations.addAll(original.annotations)
    copyBuilder.types.addAll(original.types)
    return copyBuilder.apply(init).build()
}
