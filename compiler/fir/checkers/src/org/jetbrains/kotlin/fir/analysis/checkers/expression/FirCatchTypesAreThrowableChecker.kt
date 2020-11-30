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
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.expressions.FirCatch
import org.jetbrains.kotlin.fir.expressions.FirTryExpression
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.symbols.StandardClassIds
import org.jetbrains.kotlin.fir.types.*

object FirCatchTypesAreThrowableChecker : FirTryExpressionChecker() {

    private lateinit var context: CheckerContext
    private lateinit var reporter: DiagnosticReporter
    private val throwable: FirRegularClass? by lazy {
        StandardClassIds.byName("Throwable")
            .constructClassLikeType(emptyArray(), false)
            .toRegularClass(context.session)
    }

    override fun check(expression: FirTryExpression, context: CheckerContext, reporter: DiagnosticReporter) {
        this.context = context
        this.reporter = reporter
        expression.catches.forEach { checkCatch(it) }
    }

    private fun checkCatch(catch: FirCatch) {
        val catchType = catch.parameter.returnTypeRef
        if (catchType is FirMultiCatchTypeRef) {
            catchType.types.forEach(::reportIfNotThrowable)
        } else {
            reportIfNotThrowable(catchType)
        }
    }

    private fun reportIfNotThrowable(type: FirTypeRef) {
        val coneType = type.coneType
        if (!typeIsThrowable(coneType, context))
            reporter.report(type.source, coneType)
    }

    private fun typeIsThrowable(type: ConeKotlinType, context: CheckerContext): Boolean {
        val classOfType = type
            .fullyExpandedType(context.session)
            .toRegularClass(context.session)
            ?: return false
        return throwable?.isSuperclassOf(classOfType) == true
                || classOfType.name == throwable?.name ?: false
    }

    private fun DiagnosticReporter.report(source: FirSourceElement?, type: ConeKotlinType) {
        source?.let {
            report(FirErrors.CATCH_NOT_THROWABLE.on(it, type))
        }
    }
}