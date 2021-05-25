/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.frontend.classic.handlers

import org.jetbrains.kotlin.test.codeMetaInfo.rendering.DiagnosticCodeMetaInfoRenderer

object ClassicMetaInfoUtils {
    val renderDiagnosticNoArgs = DiagnosticCodeMetaInfoRenderer().apply { renderParams = false }
    val renderDiagnosticWithArgs = DiagnosticCodeMetaInfoRenderer().apply { renderParams = true }
}
