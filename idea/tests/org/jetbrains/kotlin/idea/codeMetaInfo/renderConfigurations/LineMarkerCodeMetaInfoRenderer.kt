/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.idea.codeMetaInfo.models.LineMarkerCodeMetaInfo
import org.jetbrains.kotlin.test.codeMetaInfo.model.CodeMetaInfo
import org.jetbrains.kotlin.test.codeMetaInfo.rendering.CodeMetaInfoRenderer
import org.jetbrains.kotlin.test.codeMetaInfo.rendering.CodeMetaInfoRenderer.Companion.sanitizeLineBreaks
import org.jetbrains.kotlin.test.directives.model.RegisteredDirectives

object LineMarkerCodeMetaInfoRenderer : CodeMetaInfoRenderer {
    private val clickOrPressRegex = "Click or press (.*)to navigate".toRegex() // We have different hotkeys on different platforms

    override fun asString(codeMetaInfo: CodeMetaInfo, registeredDirectives: RegisteredDirectives): String {
        if (codeMetaInfo !is LineMarkerCodeMetaInfo) return ""
        return codeMetaInfo.tag + getParamsString(codeMetaInfo, registeredDirectives)
    }

    private fun getParamsString(lineMarkerCodeMetaInfo: LineMarkerCodeMetaInfo, registeredDirectives: RegisteredDirectives): String {
        // NB: line markers always render their description because they don't have unique
        //     tags like diagnostics, and without description all line markers would've been
        //     rendered just as <!LINE_MARKER!>
        val params = mutableListOf<String>()

        if (lineMarkerCodeMetaInfo.lineMarker.lineMarkerTooltip != null) {
            params.add("descr='${sanitizeLineMarkerTooltip(lineMarkerCodeMetaInfo.lineMarker.lineMarkerTooltip)}'")
        }

        val paramsString = params.filter { it.isNotEmpty() }.joinToString("; ")
        return if (paramsString.isEmpty()) "" else "(\"$paramsString\")"
    }

    private fun sanitizeLineMarkerTooltip(originalText: String?): String {
        if (originalText == null) return "null"
        val noHtmlTags = StringUtil.removeHtmlTags(originalText)
            .replace(" ", "")
            .replace(clickOrPressRegex, "")
            .trim()
        return sanitizeLineBreaks(noHtmlTags)
    }
}
