/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.diagnostics.jvm

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.fir.analysis.diagnostics.*
import org.jetbrains.kotlin.fir.analysis.diagnostics.SourceElementPositioningStrategies

/*
 * This file was generated automatically
 * DO NOT MODIFY IT MANUALLY
 */

object FirJvmErrors {
    // Declarations
    val CONFLICTING_JVM_DECLARATIONS by error0<PsiElement>()
    val OVERRIDE_CANNOT_BE_STATIC by error0<PsiElement>()
    val JVM_STATIC_NOT_IN_OBJECT_OR_CLASS_COMPANION by error0<PsiElement>(SourceElementPositioningStrategies.DECLARATION_SIGNATURE)
    val JVM_STATIC_NOT_IN_OBJECT_OR_COMPANION by error0<PsiElement>(SourceElementPositioningStrategies.DECLARATION_SIGNATURE)
    val JVM_STATIC_ON_NON_PUBLIC_MEMBER by error0<PsiElement>(SourceElementPositioningStrategies.DECLARATION_SIGNATURE)
    val JVM_STATIC_ON_CONST_OR_JVM_FIELD by error0<PsiElement>(SourceElementPositioningStrategies.DECLARATION_SIGNATURE)
    val JVM_STATIC_ON_EXTERNAL_IN_INTERFACE by error0<PsiElement>(SourceElementPositioningStrategies.DECLARATION_SIGNATURE)
    val INAPPLICABLE_JVM_NAME by error0<PsiElement>()
    val ILLEGAL_JVM_NAME by error0<PsiElement>()

}
