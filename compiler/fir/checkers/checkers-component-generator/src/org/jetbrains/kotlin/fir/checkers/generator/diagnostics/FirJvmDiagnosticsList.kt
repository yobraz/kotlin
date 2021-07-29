/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.checkers.generator.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.fir.PrivateForInline
import org.jetbrains.kotlin.fir.checkers.generator.diagnostics.model.*

@Suppress("UNUSED_VARIABLE", "LocalVariableName", "ClassName", "unused")
@OptIn(PrivateForInline::class)
object JVM_DIAGNOSTICS_LIST : DiagnosticList("FirJvmErrors") {
    val DECLARATIONS by object : DiagnosticGroup("Declarations") {
        val CONFLICTING_JVM_DECLARATIONS by error<PsiElement>()

        val OVERRIDE_CANNOT_BE_STATIC by error<PsiElement>()
        val JVM_STATIC_NOT_IN_OBJECT_OR_CLASS_COMPANION by error<PsiElement>(PositioningStrategy.DECLARATION_SIGNATURE)
        val JVM_STATIC_NOT_IN_OBJECT_OR_COMPANION by error<PsiElement>(PositioningStrategy.DECLARATION_SIGNATURE)

        val INAPPLICABLE_JVM_NAME by error<PsiElement>()
        val ILLEGAL_JVM_NAME by error<PsiElement>()
    }
}
