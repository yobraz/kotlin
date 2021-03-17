/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.arguments

import org.jdom.Element
import org.jetbrains.kotlin.cli.common.arguments.CommonToolArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments

interface CompilerArgumentDeserializer<T : CommonToolArguments> {
    val compilerArguments: T
    fun transformByElement(element: Element): T.() -> Unit
    fun deserialize(element: Element): T = compilerArguments.apply { transformByElement(element) }
}

class CompilerArgumentDeserializerV4<T : CommonToolArguments>(override val compilerArguments: T) : CompilerArgumentDeserializer<T> {
    override fun transformByElement(element: Element): T.() -> Unit {
        TODO("Not yet implemented")
    }
}