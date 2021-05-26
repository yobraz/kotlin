/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.codeMetaInfo.models

import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer

object HighlightingDirectives : SimpleDirectivesContainer() {
    val RENDER_SEVERITY by directive(
        description = """
            Render severity of a highlighting
        """.trimIndent()
    )

    val RENDER_DESCRIPTION by directive(
        description = """
            Render description of a highlighting
        """.trimIndent()
    )

    val RENDER_TEXT_ATTRIBUTE_KEY by directive(
        description = """
            Render text attribute key of a highlighting
        """.trimIndent()
    )
}