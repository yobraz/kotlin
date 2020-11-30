/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.expression

import org.jetbrains.kotlin.fir.FirSourceElement
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.isSuperclassOf
import org.jetbrains.kotlin.fir.analysis.checkers.toRegularClass
import org.jetbrains.kotlin.fir.analysis.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.expressions.FirCatch
import org.jetbrains.kotlin.fir.expressions.FirTryExpression
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirMultiCatchTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.coneType

object FirCatchTypesAreNotSubtypesChecker : FirTryExpressionChecker() {

    private lateinit var context: CheckerContext
    private lateinit var reporter: DiagnosticReporter

    override fun check(expression: FirTryExpression, context: CheckerContext, reporter: DiagnosticReporter) {
        this.context = context
        this.reporter = reporter
        expression.catches.forEach { checkCatch(it) }
    }

    private fun checkCatch(catch: FirCatch) {
        val catchType = catch.parameter.returnTypeRef
        if (catchType is FirMultiCatchTypeRef) {
            checkTypes(catchType.types.toSet())
        }
    }

    private fun checkTypes(setOfTypes: Set<FirTypeRef>) {
        for (possibleSubtype in setOfTypes) {
            val coneTypeOfPossibleSubtype = possibleSubtype.coneType
            val classOfPossibleSubtype = coneTypeOfPossibleSubtype
                .fullyExpandedType(context.session)
                .toRegularClass(context.session) ?: continue
            for (possibleParentType in setOfTypes) {
                val coneTypeOfPossibleParentType = possibleParentType.coneType
                val classOfPossibleParentType = coneTypeOfPossibleParentType
                    .fullyExpandedType(context.session)
                    .toRegularClass(context.session) ?: continue
                if (classOfPossibleParentType.isSuperclassOf(classOfPossibleSubtype)) {
                    reporter.report(
                        possibleSubtype.source,
                        coneTypeOfPossibleSubtype,
                        coneTypeOfPossibleParentType
                    )
                }
            }
        }
    }

    private fun DiagnosticReporter.report(
        source: FirSourceElement?,
        subType: ConeKotlinType,
        parentType: ConeKotlinType
    ) {
        source?.let {
            report(FirErrors.SUBTYPE_IN_CATCH.on(it, subType, parentType))
        }
    }
}