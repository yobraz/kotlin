/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.jspecify

import org.jetbrains.kotlin.test.codeMetaInfo.model.CodeMetaInfo
import org.jetbrains.kotlin.test.codeMetaInfo.rendering.AbstractCodeMetaInfoRenderer

object JspecifyCodeMetaInfoRenderConfiguration : AbstractCodeMetaInfoRenderer() {
    override fun asString(codeMetaInfo: CodeMetaInfo): String {
        if (codeMetaInfo !is JspecifyMarkerCodeMetaInfo) return ""
        return getTag(codeMetaInfo)
    }

    fun getTag(codeMetaInfo: JspecifyMarkerCodeMetaInfo): String {
        return codeMetaInfo.name
    }
}
