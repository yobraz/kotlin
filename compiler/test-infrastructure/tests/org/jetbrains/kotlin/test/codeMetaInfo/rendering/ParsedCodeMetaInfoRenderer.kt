/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.codeMetaInfo.rendering

import org.jetbrains.kotlin.test.codeMetaInfo.model.CodeMetaInfo
import org.jetbrains.kotlin.test.codeMetaInfo.model.ParsedCodeMetaInfo

object ParsedCodeMetaInfoRenderer : AbstractCodeMetaInfoRenderer() {
    override fun asString(codeMetaInfo: CodeMetaInfo): String {
        require(codeMetaInfo is ParsedCodeMetaInfo)
        return super.asString(codeMetaInfo) + (codeMetaInfo.description?.let { "(\"$it\")" } ?: "")
    }
}
