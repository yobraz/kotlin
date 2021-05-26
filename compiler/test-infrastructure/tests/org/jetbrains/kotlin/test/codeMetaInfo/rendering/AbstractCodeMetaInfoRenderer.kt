/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.codeMetaInfo.rendering

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.test.codeMetaInfo.model.CodeMetaInfo
import org.jetbrains.kotlin.test.directives.model.RegisteredDirectives

abstract class AbstractCodeMetaInfoRenderer {
    open fun asString(codeMetaInfo: CodeMetaInfo, registeredDirectives: RegisteredDirectives): String =
        codeMetaInfo.tag + getAttributesString(codeMetaInfo)

    protected fun sanitizeLineBreaks(originalText: String): String {
        var sanitizedText = originalText
        sanitizedText = StringUtil.replace(sanitizedText, "\r\n", " ")
        sanitizedText = StringUtil.replace(sanitizedText, "\n", " ")
        sanitizedText = StringUtil.replace(sanitizedText, "\r", " ")
        return sanitizedText
    }

    protected fun getAttributesString(codeMetaInfo: CodeMetaInfo): String {
        if (codeMetaInfo.attributes.isEmpty()) return ""
        return "{${codeMetaInfo.attributes.joinToString(";")}}"
    }
}
