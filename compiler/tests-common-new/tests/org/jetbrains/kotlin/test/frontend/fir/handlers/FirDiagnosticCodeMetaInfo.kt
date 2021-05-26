/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.frontend.fir.handlers

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.test.codeMetaInfo.model.CodeMetaInfo
import org.jetbrains.kotlin.test.codeMetaInfo.rendering.AbstractCodeMetaInfoRenderer
import org.jetbrains.kotlin.fir.analysis.diagnostics.AbstractFirDiagnosticWithParametersRenderer
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirDefaultErrorMessages
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirDiagnostic
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirDiagnosticRenderer
import org.jetbrains.kotlin.test.codeMetaInfo.rendering.AbstractCodeMetaInfoRenderer.Companion.getAttributesString
import org.jetbrains.kotlin.test.directives.DiagnosticsDirectives
import org.jetbrains.kotlin.test.directives.model.RegisteredDirectives

class FirDiagnosticCodeMetaInfo(
    val diagnostic: FirDiagnostic<*>,
    var forceRenderParameters: Boolean = false // TODO NOW: :sadfrog: can't remove it, see org.jetbrains.kotlin.test.frontend.fir.handlers.FirDiagnosticsHandler.toMetaInfo
) : CodeMetaInfo {
    private val textRangeFromClassicDiagnostic: TextRange = run {
        diagnostic.factory.positioningStrategy.markDiagnostic(diagnostic).first()
    }

    override val start: Int
        get() = textRangeFromClassicDiagnostic.startOffset

    override val end: Int
        get() = textRangeFromClassicDiagnostic.endOffset

    override val tag: String
        get() = this.diagnostic.factory.name

    override val attributes: MutableList<String> = mutableListOf()
}

object FirDiagnosticCodeMetaRenderer : AbstractCodeMetaInfoRenderer {
    private val crossPlatformLineBreak = """\r?\n""".toRegex()

    override fun asString(codeMetaInfo: CodeMetaInfo, registeredDirectives: RegisteredDirectives): String {
        if (codeMetaInfo !is FirDiagnosticCodeMetaInfo) return ""
        return (codeMetaInfo.diagnostic.factory.name
                + getAttributesString(codeMetaInfo)
                + getParamsString(codeMetaInfo, registeredDirectives))
            .replace(crossPlatformLineBreak, "")
    }

    private fun getParamsString(codeMetaInfo: FirDiagnosticCodeMetaInfo, registeredDirectives: RegisteredDirectives): String {
        if (!registeredDirectives.contains(DiagnosticsDirectives.RENDER_DIAGNOSTICS_MESSAGES) && !codeMetaInfo.forceRenderParameters) return ""
        val params = mutableListOf<String>()

        val diagnostic = codeMetaInfo.diagnostic

        @Suppress("UNCHECKED_CAST")
        val renderer = FirDefaultErrorMessages.getRendererForDiagnostic(diagnostic) as FirDiagnosticRenderer<FirDiagnostic<*>>
        if (renderer is AbstractFirDiagnosticWithParametersRenderer<*>) {
            renderer.renderParameters(diagnostic).mapTo(params, Any?::toString)
        }

        if (DiagnosticsDirectives.RENDER_SEVERITY in registeredDirectives)
            params.add("severity='${diagnostic.severity}'")

        return "(\"${params.filter { it.isNotEmpty() }.joinToString("; ")}\")"
    }
}
