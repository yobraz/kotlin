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

object FirMetaInfoUtils {
    val renderDiagnosticNoArgs = FirDiagnosticCodeMetaRenderer().apply { renderParams = false }
    val renderDiagnosticWithArgs = FirDiagnosticCodeMetaRenderer().apply { renderParams = true }
}

class FirDiagnosticCodeMetaInfo(
    val diagnostic: FirDiagnostic<*>,
    renderConfiguration: FirDiagnosticCodeMetaRenderer
) : CodeMetaInfo {
    private val textRangeFromClassicDiagnostic: TextRange = run {
        diagnostic.factory.positioningStrategy.markDiagnostic(diagnostic).first()
    }

    override var renderer: FirDiagnosticCodeMetaRenderer = renderConfiguration
        private set

    override val start: Int
        get() = textRangeFromClassicDiagnostic.startOffset

    override val end: Int
        get() = textRangeFromClassicDiagnostic.endOffset

    override val tag: String
        get() = this.diagnostic.factory.name

    override val attributes: MutableList<String> = mutableListOf()

    override fun asString(): String = renderer.asString(this)

    fun replaceRenderConfiguration(renderConfiguration: FirDiagnosticCodeMetaRenderer) {
        this.renderer = renderConfiguration
    }
}

class FirDiagnosticCodeMetaRenderer(
    val renderSeverity: Boolean = false,
) : AbstractCodeMetaInfoRenderer(renderParams = false) {
    private val crossPlatformLineBreak = """\r?\n""".toRegex()

    override fun asString(codeMetaInfo: CodeMetaInfo): String {
        if (codeMetaInfo !is FirDiagnosticCodeMetaInfo) return ""
        return (codeMetaInfo.diagnostic.factory.name
                + getAttributesString(codeMetaInfo)
                + getParamsString(codeMetaInfo))
            .replace(crossPlatformLineBreak, "")
    }

    private fun getParamsString(codeMetaInfo: FirDiagnosticCodeMetaInfo): String {
        if (!renderParams) return ""
        val params = mutableListOf<String>()

        val diagnostic = codeMetaInfo.diagnostic

        @Suppress("UNCHECKED_CAST")
        val renderer = FirDefaultErrorMessages.getRendererForDiagnostic(diagnostic) as FirDiagnosticRenderer<FirDiagnostic<*>>
        if (renderer is AbstractFirDiagnosticWithParametersRenderer<*>) {
            renderer.renderParameters(diagnostic).mapTo(params, Any?::toString)
        }

        if (renderSeverity)
            params.add("severity='${diagnostic.severity}'")

        return "(\"${params.filter { it.isNotEmpty() }.joinToString("; ")}\")"
    }
}
