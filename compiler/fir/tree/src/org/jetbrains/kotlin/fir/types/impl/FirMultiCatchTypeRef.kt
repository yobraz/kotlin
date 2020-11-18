/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.types.impl

import org.jetbrains.kotlin.fir.FirSourceElement
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.FirVisitor

class FirMultiCatchTypeRef(
    val types: MutableList<FirTypeRef>,
    override val source: FirSourceElement?,
    override val annotations: List<FirAnnotationCall> = mutableListOf(),
) : FirTypeRef() {
    override fun <D> transformAnnotations(transformer: FirTransformer<D>, data: D): FirMultiCatchTypeRef {
        types.forEach { it.transformAnnotations(transformer, data) }
        return this
    }

    override fun <R, D> acceptChildren(visitor: FirVisitor<R, D>, data: D) {
        types.forEach { it.acceptChildren(visitor, data) }
    }

    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirMultiCatchTypeRef {
        types.forEach { it.transformChildren(transformer, data) }
        return this
    }
}