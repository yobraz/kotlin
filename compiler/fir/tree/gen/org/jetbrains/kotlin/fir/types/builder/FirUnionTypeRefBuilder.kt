/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.types.builder

import kotlin.contracts.*
import org.jetbrains.kotlin.fir.FirSourceElement
import org.jetbrains.kotlin.fir.builder.FirAnnotationContainerBuilder
import org.jetbrains.kotlin.fir.builder.FirBuilderDsl
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.FirUnionTypeRef
import org.jetbrains.kotlin.fir.types.impl.FirUnionTypeRefImpl
import org.jetbrains.kotlin.fir.visitors.*

/*
 * This file was generated automatically
 * DO NOT MODIFY IT MANUALLY
 */

@FirBuilderDsl
class FirUnionTypeRefBuilder : FirAnnotationContainerBuilder {
    override var source: FirSourceElement? = null
    override val annotations: MutableList<FirAnnotationCall> = mutableListOf()
    val nestedTypes: MutableList<FirTypeRef> = mutableListOf()

    override fun build(): FirUnionTypeRef {
        return FirUnionTypeRefImpl(
            source,
            annotations,
            nestedTypes,
        )
    }

}

@OptIn(ExperimentalContracts::class)
inline fun buildUnionTypeRef(init: FirUnionTypeRefBuilder.() -> Unit = {}): FirUnionTypeRef {
    contract {
        callsInPlace(init, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    return FirUnionTypeRefBuilder().apply(init).build()
}

@OptIn(ExperimentalContracts::class)
inline fun buildUnionTypeRefCopy(original: FirUnionTypeRef, init: FirUnionTypeRefBuilder.() -> Unit = {}): FirUnionTypeRef {
    contract {
        callsInPlace(init, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    val copyBuilder = FirUnionTypeRefBuilder()
    copyBuilder.source = original.source
    copyBuilder.annotations.addAll(original.annotations)
    copyBuilder.nestedTypes.addAll(original.nestedTypes)
    return copyBuilder.apply(init).build()
}
