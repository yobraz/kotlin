/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.declarations.impl

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirModuleData
import org.jetbrains.kotlin.fir.FirSourceElement
import org.jetbrains.kotlin.fir.declarations.DeprecationsPerUseSite
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationAttributes
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirTypeParameterRef
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.references.FirControlFlowGraphReference
import org.jetbrains.kotlin.fir.scopes.FirScopeProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.impl.ideWrappers.IDEFirDeclarationMarker
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.fir.visitors.*
import java.util.concurrent.atomic.AtomicReference

/*
 * This file was generated manually
 * DO MODIFY IT AUTOMATICALLY
 */

internal class IDEFirRegularClassImpl(
    override val source: FirSourceElement?,
    override val moduleData: FirModuleData,
    @Volatile
    override var resolvePhase: FirResolvePhase,
    override val origin: FirDeclarationOrigin,
    override val attributes: FirDeclarationAttributes,
    override var deprecation: DeprecationsPerUseSite?,
    override val typeParameters: MutableList<FirTypeParameterRef>,
    override val classKind: ClassKind,
    override val declarations: MutableList<FirDeclaration>,
    override val annotations: MutableList<FirAnnotationCall>,
    override val scopeProvider: FirScopeProvider,
    private val originalStatus: FirDeclarationStatus,
    override val name: Name,
    override val symbol: FirRegularClassSymbol,
    override var companionObject: FirRegularClass?,
    private val originalSuperTypeRefs: List<FirTypeRef>,
) : FirRegularClass(), IDEFirDeclarationMarker {
    override var controlFlowGraphReference: FirControlFlowGraphReference? = null
    override val hasLazyNestedClassifiers: Boolean get() = false

    private val atomicStatus: AtomicReference<FirDeclarationStatus> = AtomicReference<FirDeclarationStatus>(originalStatus)
    override val status: FirDeclarationStatus get() = atomicStatus.get()

    private val atomicSuperTypeRefs: AtomicReference<List<FirTypeRef>> = AtomicReference<List<FirTypeRef>>(originalSuperTypeRefs)
    override val superTypeRefs: List<FirTypeRef> get() = atomicSuperTypeRefs.get()

    init {
        symbol.bind(this)
    }

    override fun <R, D> acceptChildren(visitor: FirVisitor<R, D>, data: D) {
        typeParameters.forEach { it.accept(visitor, data) }
        declarations.forEach { it.accept(visitor, data) }
        annotations.forEach { it.accept(visitor, data) }
        status.accept(visitor, data)
        controlFlowGraphReference?.accept(visitor, data)
        superTypeRefs.forEach { it.accept(visitor, data) }
    }

    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): IDEFirRegularClassImpl {
        transformTypeParameters(transformer, data)
        transformDeclarations(transformer, data)
        transformAnnotations(transformer, data)
        transformStatus(transformer, data)
        controlFlowGraphReference = controlFlowGraphReference?.transform(transformer, data)
        companionObject = declarations.asSequence().filterIsInstance<FirRegularClass>().firstOrNull { it.status.isCompanion }
        transformSuperTypeRefs(transformer, data)
        return this
    }

    override fun <D> transformTypeParameters(transformer: FirTransformer<D>, data: D): IDEFirRegularClassImpl {
        typeParameters.transformInplace(transformer, data)
        return this
    }

    override fun <D> transformDeclarations(transformer: FirTransformer<D>, data: D): IDEFirRegularClassImpl {
        declarations.transformInplace(transformer, data)
        return this
    }

    override fun <D> transformAnnotations(transformer: FirTransformer<D>, data: D): IDEFirRegularClassImpl {
        annotations.transformInplace(transformer, data)
        return this
    }

    override fun <D> transformStatus(transformer: FirTransformer<D>, data: D): IDEFirRegularClassImpl {
        atomicStatus.compareAndSet(originalStatus, status.transform(transformer, data))
        return this
    }

    override fun <D> transformCompanionObject(transformer: FirTransformer<D>, data: D): IDEFirRegularClassImpl {
        companionObject = companionObject?.transform(transformer, data)
        return this
    }

    override fun <D> transformSuperTypeRefs(transformer: FirTransformer<D>, data: D): IDEFirRegularClassImpl {
        superTypeRefs.transformNoInplace(transformer, data)
        return this
    }

    override fun replaceResolvePhase(newResolvePhase: FirResolvePhase) {
        resolvePhase = newResolvePhase
    }

    override fun replaceDeprecation(newDeprecation: DeprecationsPerUseSite?) {
        deprecation = newDeprecation
    }

    override fun replaceControlFlowGraphReference(newControlFlowGraphReference: FirControlFlowGraphReference?) {
        controlFlowGraphReference = newControlFlowGraphReference
    }

    override fun replaceSuperTypeRefs(newSuperTypeRefs: List<FirTypeRef>) {
        atomicSuperTypeRefs.compareAndSet(originalSuperTypeRefs, newSuperTypeRefs)
    }
}
