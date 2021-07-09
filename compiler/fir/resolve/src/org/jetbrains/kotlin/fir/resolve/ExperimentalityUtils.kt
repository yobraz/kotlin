/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve

import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.FirConstExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.references.FirNamedReference
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.ensureResolved
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.checkers.Experimentality
import org.jetbrains.kotlin.resolve.checkers.OptInNames
import org.jetbrains.kotlin.utils.addIfNotNull


fun FirAnnotationContainer.calculateOwnExperimentalities(session: FirSession): List<Experimentality> {
    val result = mutableListOf<Experimentality>()
    for (annotation in annotations) {
        val annotationType = annotation.annotationTypeRef.coneTypeSafe<ConeClassLikeType>()
        result.addIfNotNull(
            (annotationType?.fullyExpandedType(session)?.lookupTag?.toSymbol(
                session
            ) as? FirRegularClassSymbol)?.loadExperimentalityForMarkerAnnotation()
        )
    }
    return result
}

fun ConeKotlinType?.loadExperimentalities(session: FirSession): List<Experimentality> =
    when (this) {
        !is ConeClassLikeType -> emptyList()
        else -> {
            val expandedType = fullyExpandedType(session)
            expandedType.lookupTag.toFirRegularClass(session)?.experimentalities.orEmpty() + expandedType.typeArguments.flatMap {
                if (it.isStarProjection) emptyList()
                else it.type?.loadExperimentalities(session).orEmpty()
            } + if (this !== expandedType) {
                lookupTag.toFirTypeAlias(session)?.experimentalities.orEmpty()
            } else emptyList()
        }
    }

fun FirRegularClassSymbol.loadExperimentalityForMarkerAnnotation(): Experimentality? {
    ensureResolved(FirResolvePhase.SUPER_TYPES)
    @OptIn(SymbolInternals::class)
    return fir.loadExperimentalityForMarkerAnnotation()
}

private fun FirRegularClass.loadExperimentalityForMarkerAnnotation(): Experimentality? {
    val experimental = getAnnotationByClassId(OptInNames.REQUIRES_OPT_IN_CLASS_ID) ?: return null

    val levelArgument = experimental.findArgumentByName(LEVEL) as? FirQualifiedAccessExpression
    val levelName = (levelArgument?.calleeReference as FirNamedReference?)?.name?.asString()
    val level = OptInLevel.values().firstOrNull { it.name == levelName } ?: OptInLevel.DEFAULT
    val message = (experimental.findArgumentByName(MESSAGE) as? FirConstExpression<*>)?.value as? String
    return Experimentality(symbol.classId.asSingleFqName(), level.severity, message)
}

private val LEVEL = Name.identifier("level")
private val MESSAGE = Name.identifier("message")

private enum class OptInLevel(val severity: Experimentality.Severity) {
    WARNING(Experimentality.Severity.WARNING),
    ERROR(Experimentality.Severity.ERROR),
    DEFAULT(Experimentality.DEFAULT_SEVERITY)
}
