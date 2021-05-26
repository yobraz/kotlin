/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.codeMetaInfo.model

interface CodeMetaInfo {
    val start: Int
    val end: Int
    val tag: String
    val attributes: MutableList<String>

    val tagPrefix: String get() = "<!"
    val tagPostfix: String get() = "!>"
    val closingTag: String get() = "<!>"
}
