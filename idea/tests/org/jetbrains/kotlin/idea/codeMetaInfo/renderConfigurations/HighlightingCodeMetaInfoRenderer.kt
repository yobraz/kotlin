/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations

import org.jetbrains.kotlin.test.codeMetaInfo.model.CodeMetaInfo
import org.jetbrains.kotlin.test.codeMetaInfo.rendering.CodeMetaInfoRenderer
import org.jetbrains.kotlin.idea.codeMetaInfo.models.HighlightingCodeMetaInfo
import org.jetbrains.kotlin.idea.codeMetaInfo.models.HighlightingDirectives.RENDER_DESCRIPTION
import org.jetbrains.kotlin.idea.codeMetaInfo.models.HighlightingDirectives.RENDER_SEVERITY
import org.jetbrains.kotlin.idea.codeMetaInfo.models.HighlightingDirectives.RENDER_TEXT_ATTRIBUTE_KEY
import org.jetbrains.kotlin.test.codeMetaInfo.rendering.CodeMetaInfoRenderer.Companion.sanitizeLineBreaks
import org.jetbrains.kotlin.test.directives.model.RegisteredDirectives

object HighlightingCodeMetaInfoRenderer : CodeMetaInfoRenderer {

    override fun asString(codeMetaInfo: CodeMetaInfo, registeredDirectives: RegisteredDirectives): String? {
        if (codeMetaInfo !is HighlightingCodeMetaInfo) return null
        return codeMetaInfo.tag + getParamsString(codeMetaInfo, registeredDirectives)
    }

    private fun getParamsString(highlightingCodeMetaInfo: HighlightingCodeMetaInfo, registeredDirectives: RegisteredDirectives): String {
        val params = mutableListOf<String>()
        if (RENDER_SEVERITY in registeredDirectives)
            params.add("severity='${highlightingCodeMetaInfo.highlightingInfo.severity}'")

        if (RENDER_DESCRIPTION in registeredDirectives)
            params.add("descr='${
                highlightingCodeMetaInfo.highlightingInfo.description?.let { sanitizeLineBreaks(highlightingCodeMetaInfo.highlightingInfo.description) }
            }'")

        if (RENDER_TEXT_ATTRIBUTE_KEY in registeredDirectives)
            params.add("textAttributesKey='${highlightingCodeMetaInfo.highlightingInfo.forcedTextAttributesKey}'")

        val paramsString = params.filter { it.isNotEmpty() }.joinToString("; ")

        return if (paramsString.isEmpty()) "" else "(\"$paramsString\")"
    }
}
