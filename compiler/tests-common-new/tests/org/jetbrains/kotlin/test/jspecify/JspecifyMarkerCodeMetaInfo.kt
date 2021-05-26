/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.jspecify

import org.jetbrains.kotlin.test.codeMetaInfo.model.CodeMetaInfo

class JspecifyMarkerCodeMetaInfo(
    override val start: Int,
    override val end: Int,
    val offset: Int,
    val name: String
) : CodeMetaInfo {
    override val tagPrefix = "\n${" ".repeat(offset)}// "
    override val tagPostfix = ""
    override val closingTag = ""

    override val tag = this.name

    override val attributes: MutableList<String> = mutableListOf()
}
