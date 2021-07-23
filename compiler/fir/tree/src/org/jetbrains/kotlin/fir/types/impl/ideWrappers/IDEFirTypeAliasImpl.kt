/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.declarations.impl

import org.jetbrains.kotlin.fir.FirModuleData
import org.jetbrains.kotlin.fir.FirSourceElement
import org.jetbrains.kotlin.fir.declarations.DeprecationsPerUseSite
import org.jetbrains.kotlin.fir.declarations.FirDeclarationAttributes
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirTypeAlias
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeAliasSymbol
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.impl.ideWrappers.IDEFirDeclarationMarker
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.fir.visitors.*
import java.util.concurrent.atomic.AtomicReference

/*
 * This file was generated manually
 * DO MODIFY IT AUTOMATICALLY
 */

internal class IDEFirTypeAliasImpl(
    override val source: FirSourceElement?,
    override val moduleData: FirModuleData,
    @Volatile
    override var resolvePhase: FirResolvePhase,
    override val origin: FirDeclarationOrigin,
    override val attributes: FirDeclarationAttributes,
    override var deprecation: DeprecationsPerUseSite?,
    private val originalStatus: FirDeclarationStatus,
    override val typeParameters: MutableList<FirTypeParameter>,
    override val name: Name,
    override val symbol: FirTypeAliasSymbol,
    private val originalExpandedTypeRef: FirTypeRef,
    override val annotations: MutableList<FirAnnotationCall>,
) : FirTypeAlias(), IDEFirDeclarationMarker {

    private val atomicStatus: AtomicReference<FirDeclarationStatus> = AtomicReference<FirDeclarationStatus>(originalStatus)
    override val status: FirDeclarationStatus get() = atomicStatus.get()

    private val atomicExpandedTypeRef: AtomicReference<FirTypeRef> = AtomicReference<FirTypeRef>(originalExpandedTypeRef)
    override val expandedTypeRef: FirTypeRef get() = atomicExpandedTypeRef.get()

    init {
        symbol.bind(this)
    }

    override fun <R, D> acceptChildren(visitor: FirVisitor<R, D>, data: D) {
        status.accept(visitor, data)
        typeParameters.forEach { it.accept(visitor, data) }
        expandedTypeRef.accept(visitor, data)
        annotations.forEach { it.accept(visitor, data) }
    }

    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): IDEFirTypeAliasImpl {
        transformStatus(transformer, data)
        transformTypeParameters(transformer, data)
        transformExpandedTypeRef(transformer, data)
        transformAnnotations(transformer, data)
        return this
    }

    override fun <D> transformStatus(transformer: FirTransformer<D>, data: D): IDEFirTypeAliasImpl {
        atomicStatus.compareAndSet(originalStatus, status.transform(transformer, data))
        return this
    }

    override fun <D> transformTypeParameters(transformer: FirTransformer<D>, data: D): IDEFirTypeAliasImpl {
        typeParameters.transformInplace(transformer, data)
        return this
    }

    override fun <D> transformExpandedTypeRef(transformer: FirTransformer<D>, data: D): IDEFirTypeAliasImpl {
        atomicExpandedTypeRef.compareAndSet(originalExpandedTypeRef, expandedTypeRef.transform(transformer, data))
        return this
    }

    override fun <D> transformAnnotations(transformer: FirTransformer<D>, data: D): IDEFirTypeAliasImpl {
        annotations.transformInplace(transformer, data)
        return this
    }

    override fun replaceResolvePhase(newResolvePhase: FirResolvePhase) {
        resolvePhase = newResolvePhase
    }

    override fun replaceDeprecation(newDeprecation: DeprecationsPerUseSite?) {
        deprecation = newDeprecation
    }

    override fun replaceExpandedTypeRef(newExpandedTypeRef: FirTypeRef) {
        atomicExpandedTypeRef.compareAndSet(originalExpandedTypeRef, newExpandedTypeRef)
    }
}
