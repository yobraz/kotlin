/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.codeMetaInfo.models

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import org.jetbrains.kotlin.checkers.diagnostics.ActualDiagnostic
import org.jetbrains.kotlin.test.codeMetaInfo.model.CodeMetaInfo
import org.jetbrains.kotlin.test.codeMetaInfo.model.DiagnosticCodeMetaInfo
import org.jetbrains.kotlin.test.codeMetaInfo.rendering.CodeMetaInfoRenderer
import org.jetbrains.kotlin.test.codeMetaInfo.rendering.DiagnosticCodeMetaInfoRenderer
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.HighlightingCodeMetaInfoRenderer
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.LineMarkerCodeMetaInfoRenderer
import org.jetbrains.kotlin.idea.editor.fixers.end
import org.jetbrains.kotlin.idea.editor.fixers.start

fun createCodeMetaInfo(obj: Any, renderer: CodeMetaInfoRenderer): List<CodeMetaInfo> {
    fun errorMessage() = "Unexpected render configuration for object $obj"
    return when (obj) {
        is Diagnostic -> {
            require(renderer is DiagnosticCodeMetaInfoRenderer, ::errorMessage)
            obj.textRanges.map { DiagnosticCodeMetaInfo(it.start, it.end, obj) }
        }
        is ActualDiagnostic -> {
            require(renderer is DiagnosticCodeMetaInfoRenderer, ::errorMessage)
            obj.diagnostic.textRanges.map { DiagnosticCodeMetaInfo(it.start, it.end, obj.diagnostic) }
        }
        is HighlightInfo -> {
            require(renderer is HighlightingCodeMetaInfoRenderer, ::errorMessage)
            listOf(HighlightingCodeMetaInfo(obj))
        }
        is LineMarkerInfo<*> -> {
            require(renderer is LineMarkerCodeMetaInfoRenderer, ::errorMessage)
            listOf(LineMarkerCodeMetaInfo(obj))
        }
        else -> throw IllegalArgumentException("Unknown type for creating CodeMetaInfo object $obj")
    }
}

fun getCodeMetaInfo(
    objects: List<Any>,
    renderer: CodeMetaInfoRenderer
): List<CodeMetaInfo> {
    return objects.flatMap { createCodeMetaInfo(it, renderer) }
}
