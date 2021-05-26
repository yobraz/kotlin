/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.codeMetaInfo.model

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.diagnostics.Diagnostic

class DiagnosticCodeMetaInfo(
    override val start: Int,
    override val end: Int,
    val diagnostic: Diagnostic,
    var forceParametersRendering: Boolean = false,
) : CodeMetaInfo {
    constructor(range: TextRange, diagnostic: Diagnostic) : this(range.startOffset, range.endOffset, diagnostic)

    override val tag: String
        get() = this.diagnostic.factory.name

    override val attributes: MutableList<String> = mutableListOf()
}
