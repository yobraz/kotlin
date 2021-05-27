/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.codeMetaInfo.rendering

import org.jetbrains.kotlin.checkers.diagnostics.factories.DebugInfoDiagnosticFactory1
import org.jetbrains.kotlin.test.codeMetaInfo.model.CodeMetaInfo
import org.jetbrains.kotlin.test.codeMetaInfo.model.DiagnosticCodeMetaInfo
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.rendering.*
import org.jetbrains.kotlin.test.codeMetaInfo.rendering.CodeMetaInfoRenderer.Companion.getAttributesString
import org.jetbrains.kotlin.test.directives.DiagnosticsDirectives
import org.jetbrains.kotlin.test.directives.model.RegisteredDirectives

object DiagnosticCodeMetaInfoRenderer : CodeMetaInfoRenderer {
    private val crossPlatformLineBreak = """\r?\n""".toRegex()

    override fun asString(codeMetaInfo: CodeMetaInfo, registeredDirectives: RegisteredDirectives): String? {
        if (codeMetaInfo !is DiagnosticCodeMetaInfo) return null
        return (codeMetaInfo.tag
                + getAttributesString(codeMetaInfo)
                + getParamsString(codeMetaInfo, registeredDirectives))
            .replace(crossPlatformLineBreak, "")
    }

    private fun getParamsString(codeMetaInfo: DiagnosticCodeMetaInfo, registeredDirectives: RegisteredDirectives): String {
        if (DiagnosticsDirectives.RENDER_DIAGNOSTICS_MESSAGES !in registeredDirectives && !codeMetaInfo.forceParametersRendering) return ""
        val params = mutableListOf<String>()

        @Suppress("UNCHECKED_CAST")
        val renderer = when (codeMetaInfo.diagnostic.factory) {
            is DebugInfoDiagnosticFactory1 -> DiagnosticWithParameters1Renderer(
                "{0}",
                Renderers.TO_STRING
            ) as DiagnosticRenderer<Diagnostic>
            else -> DefaultErrorMessages.getRendererForDiagnostic(codeMetaInfo.diagnostic)
        }

        if (renderer is AbstractDiagnosticWithParametersRenderer) {
            renderer.renderParameters(codeMetaInfo.diagnostic).mapTo(params, Any?::toString)
        }

        if (DiagnosticsDirectives.RENDER_SEVERITY in registeredDirectives)
            params.add("severity='${codeMetaInfo.diagnostic.severity}'")

        return "(\"${params.filter { it.isNotEmpty() }.joinToString("; ")}\")"
    }
}
