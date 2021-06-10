/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.expression

import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.declarations.FirAnnotatedDeclaration
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccess
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference

object FirOptInUsageAccessChecker : FirQualifiedAccessChecker() {
    override fun check(expression: FirQualifiedAccess, context: CheckerContext, reporter: DiagnosticReporter) {
        val reference = expression.calleeReference as? FirResolvedNamedReference ?: return
        val fir = reference.resolvedSymbol.fir as? FirAnnotatedDeclaration ?: return
        with(FirOptInUsageBaseChecker) {
            if (expression is FirVariableAssignment && fir is FirProperty) {
                val experimentalities = fir.loadExperimentalities(context, fromSetter = true) +
                        loadExperimentalitiesFromTypeArguments(context, expression.typeArguments)
                reportNotAcceptedExperimentalities(experimentalities, expression.lValue, context, reporter)
                return
            }
            val experimentalities = fir.loadExperimentalities(context) +
                    loadExperimentalitiesFromTypeArguments(context, expression.typeArguments)
            reportNotAcceptedExperimentalities(experimentalities, expression, context, reporter)
        }
    }
}