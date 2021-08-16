/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.analysis.diagnostics.reportOn
import org.jetbrains.kotlin.fir.declarations.FirBackingField
import org.jetbrains.kotlin.fir.declarations.utils.visibility

object FirBackingFieldVisibilityChecker : FirBackingFieldChecker() {
    override fun check(declaration: FirBackingField, context: CheckerContext, reporter: DiagnosticReporter) {
        if (
            declaration.visibility != Visibilities.Private &&
            declaration.visibility != Visibilities.Internal
        ) {
            reporter.reportOn(declaration.source, FirErrors.INAPPLICABLE_BACKING_FIELD_VISIBILITY, context)
        }
    }
}
