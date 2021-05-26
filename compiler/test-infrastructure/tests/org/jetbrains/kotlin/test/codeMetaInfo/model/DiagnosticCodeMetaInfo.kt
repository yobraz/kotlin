/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.codeMetaInfo.model

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.test.codeMetaInfo.rendering.DiagnosticCodeMetaInfoRenderer
import org.jetbrains.kotlin.diagnostics.Diagnostic

class DiagnosticCodeMetaInfo(
    override val start: Int,
    override val end: Int,
    renderConfiguration: DiagnosticCodeMetaInfoRenderer,
    val diagnostic: Diagnostic
) : CodeMetaInfo {
    constructor(
        range: TextRange,
        renderConfiguration: DiagnosticCodeMetaInfoRenderer,
        diagnostic: Diagnostic
    ) : this(range.startOffset, range.endOffset, renderConfiguration, diagnostic)

    override var renderer: DiagnosticCodeMetaInfoRenderer = renderConfiguration
        private set

    fun replaceRenderConfiguration(renderConfiguration: DiagnosticCodeMetaInfoRenderer) {
        this.renderer = renderConfiguration
    }

    override val tag: String
        get() = this.diagnostic.factory.name

    override val attributes: MutableList<String> = mutableListOf()

    override fun asString(): String = renderer.asString(this)
}
