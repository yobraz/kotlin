/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.FirSourceElement
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.analysis.diagnostics.reportOn
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.resolve.toFirRegularClass
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.model.TypeArgumentMarker

object FirFiniteBoundRestrictionChecker : FirRegularClassChecker() {
    override fun check(declaration: FirRegularClass, context: CheckerContext, reporter: DiagnosticReporter) {
        val processedDeclarations = mutableMapOf<Name, DeclarationRecord>()
        buildGraph(declaration, processedDeclarations, context.session)

        fun check(currentDeclarationRecord: DeclarationRecord, isReport: Boolean): Boolean {
            var result = false
            val problemTypeParameters = mutableSetOf<ParameterPosition>()
            for ((index, typeParameter) in currentDeclarationRecord.typeParameters.withIndex()) {
                val visitedNameIndexes = mutableSetOf<ParameterPosition>()
                if (!problemTypeParameters.contains(ParameterPosition(currentDeclarationRecord.name, index)) &&
                    findCycles(currentDeclarationRecord, index, currentDeclarationRecord, index, processedDeclarations, visitedNameIndexes)
                ) {
                    if (isReport) {
                        reporter.reportOn(typeParameter.source, FirErrors.FINITE_BOUNDS_VIOLATION, context)
                    }
                    result = true
                    problemTypeParameters.addAll(visitedNameIndexes)
                }
            }
            return result
        }

        val currentDeclarationRecord = processedDeclarations[declaration.name] ?: return
        check(currentDeclarationRecord, true)

        for (declarationRecord in processedDeclarations.values) {
            if (declarationRecord.isJavaClass && check(declarationRecord, false)) {
                val declarationSource = declaration.source
                if (declarationSource != null) {
                    reporter.report(
                        FirErrors.FINITE_BOUNDS_VIOLATION_IN_JAVA.on(declarationSource, declarationRecord.name.identifier), context
                    )
                    break
                }
            }
        }
    }

    private fun findCycles(
        originDeclaration: DeclarationRecord,
        originIndex: Int,
        currentDeclaration: DeclarationRecord,
        currentIndex: Int,
        declarations: Map<Name, DeclarationRecord>,
        visitedParameterPositions: MutableSet<ParameterPosition>
    ) : Boolean {
        if (!visitedParameterPositions.add(ParameterPosition(currentDeclaration.name, currentIndex))) {
            return originDeclaration.name == currentDeclaration.name && originIndex == currentIndex
        }

        val bounds = currentDeclaration.typeParameters.elementAt(currentIndex).bounds
        for (bound in bounds) {
            val declParameterRecords = mutableListOf<DeclarationParameterRecord>()
            searchDeclParameterRecords(bound, currentDeclaration, declParameterRecords, visitedParameterPositions)

            for (declParameterRecord in declParameterRecords) {
                for ((index, typeArgument) in declParameterRecord.typeArguments.withIndex()) {
                    if (typeArgument.projectionKind != ProjectionKind.INVARIANT) {
                        if (findCycles(originDeclaration,
                                       originIndex,
                                       declarations[declParameterRecord.name]!!,
                                       index,
                                       declarations,
                                       visitedParameterPositions)
                        ) {
                            return true
                        }
                    }
                }
            }
        }

        return false
    }

    fun searchDeclParameterRecords(
        boundRecord: BoundRecord,
        declaration: DeclarationRecord,
        declarationParameterRecords: MutableList<DeclarationParameterRecord>,
        visitedParameterPositions: MutableSet<ParameterPosition>,
    ) {
        // Only references on declarations are relevant not on type parameters
        // Consider the following fragment: D1<<T, U> where T : U, U: D1<*, U>
        // This code returns D<*, U> for the first T parameter and skips U

        if (boundRecord is DeclarationParameterRecord) {
            declarationParameterRecords.add(boundRecord)
            return
        }

        for ((index, typeParameter) in declaration.typeParameters.withIndex()) {
            if (typeParameter.name == boundRecord.name) {
                visitedParameterPositions.add(ParameterPosition(declaration.name, index))
                for (bound in typeParameter.bounds) {
                    searchDeclParameterRecords(bound, declaration, declarationParameterRecords, visitedParameterPositions)
                }
            }
        }
    }

    private fun buildGraph(declaration: FirRegularClass, processedDeclarations: MutableMap<Name, DeclarationRecord>, session: FirSession) {
        val processedDeclaration = processedDeclarations[declaration.name]
        if (processedDeclaration == null) {
            val parameterRecords = mutableListOf<ParameterRecord>()
            processedDeclarations[declaration.name] = DeclarationRecord(declaration.name, parameterRecords, declaration.origin is FirDeclarationOrigin.Java)
            for (typeParameter in declaration.typeParameters.withIndex()) {
                val typeParameterValue = typeParameter.value
                if (typeParameterValue is FirTypeParameter) {
                    val boundRecords = mutableListOf<BoundRecord>()
                    for (bound in typeParameterValue.bounds) {
                        boundRecords.add(extractBoundRecord(bound.coneType, processedDeclarations, session))
                    }
                    parameterRecords.add(ParameterRecord(typeParameterValue.name, typeParameterValue.source, boundRecords))
                }
            }
        }
    }

    private fun extractBoundRecord(typeArgumentMarker: TypeArgumentMarker,
                                   processedDeclarations: MutableMap<Name, DeclarationRecord>,
                                   session: FirSession) : BoundRecord {
        fun extractProjectionBound(
            typeArgumentMarker: ConeKotlinTypeProjection,
            projectionKind: ProjectionKind
        ): BoundRecord {
            val bound = extractBoundRecord(typeArgumentMarker.type, processedDeclarations, session)
            return if (bound is TypeParameterRecord) {
                TypeParameterRecord(bound.name, projectionKind)
            } else {
                bound as DeclarationParameterRecord
                DeclarationParameterRecord(bound.name, projectionKind, bound.typeArguments)
            }
        }

        when (typeArgumentMarker) {
            is ConeStarProjection -> return DeclarationParameterRecord(starName, typeArgumentMarker.kind, mutableListOf())
            is ConeKotlinTypeProjectionOut -> return extractProjectionBound(typeArgumentMarker, ProjectionKind.OUT)
            is ConeKotlinTypeProjectionIn -> return extractProjectionBound(typeArgumentMarker, ProjectionKind.IN)
            is ConeTypeParameterType -> return TypeParameterRecord(typeArgumentMarker.lookupTag.name, typeArgumentMarker.kind)
            is ConeRawType -> return extractBoundRecord(typeArgumentMarker.lowerBound, processedDeclarations, session)
            is ConeFlexibleType -> return extractBoundRecord(typeArgumentMarker.lowerBound, processedDeclarations, session)
        }

        typeArgumentMarker as ConeClassLikeType
        val arguments = mutableListOf<BoundRecord>()
        for (typeArgument in typeArgumentMarker.typeArguments) {
            arguments.add(extractBoundRecord(typeArgument, processedDeclarations, session))
        }

        val lookupTag = typeArgumentMarker.lookupTag
        val name = lookupTag.name
        val firDecl = lookupTag.toFirRegularClass(session)
        if (firDecl is FirRegularClass) {
            buildGraph(firDecl, processedDeclarations, session)
        }
        return DeclarationParameterRecord(name, typeArgumentMarker.kind, arguments)
    }

    data class ParameterPosition(val declarationName: Name, val parameterIndex: Int)

    // This and derived classes are intended to simplify bypass logic
    abstract class Record(val name: Name) // toString() overrides are implemented for debug purpose

    class DeclarationRecord(name: Name, val typeParameters: List<ParameterRecord>, val isJavaClass: Boolean) : Record(name) {
        override fun toString(): String {
            return if (typeParameters.isEmpty()) name.identifier else "$name<${typeParameters.joinToString(", ")}>"
        }
    }

    class ParameterRecord(name: Name, val source: FirSourceElement?, val bounds: List<BoundRecord>) : Record(name) {
        override fun toString(): String {
            return if (bounds.isEmpty()) name.identifier else "$name : ${bounds.joinToString(", ")}"
        }
    }

    abstract class BoundRecord(name: Name, val projectionKind: ProjectionKind) : Record(name) {
        val projectionPrefix: String
            get() {
                return if (projectionKind == ProjectionKind.IN) "in " else if (projectionKind == ProjectionKind.OUT) "out " else ""
            }
    }

    class TypeParameterRecord(name: Name, projectionKind: ProjectionKind) : BoundRecord(name, projectionKind) {
        override fun toString(): String {
            return "$projectionPrefix$name"
        }
    }

    class DeclarationParameterRecord(name: Name, projectionKind: ProjectionKind, val typeArguments: List<BoundRecord>)
        : BoundRecord(name, projectionKind) {
        override fun toString(): String {
            return projectionPrefix + (if (typeArguments.isEmpty()) name.identifier else "$name<${typeArguments.joinToString(", ")}>")
        }
    }

    private val starName: Name = Name.identifier("*")
}
