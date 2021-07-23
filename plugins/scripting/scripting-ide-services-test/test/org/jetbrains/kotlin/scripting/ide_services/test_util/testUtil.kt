/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.scripting.ide_services.test_util

import org.jetbrains.kotlin.scripting.ide_services.compiler.KJvmReplCompilerWithIdeServices
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger
import kotlin.script.experimental.api.*
import kotlin.script.experimental.impl.internalScriptingRunSuspend
import kotlin.script.experimental.jvm.BasicJvmReplEvaluator
import kotlin.script.experimental.util.LinkedSnippet
import kotlin.script.experimental.jvm.util.toSourceCodePosition

internal class JvmTestRepl(
    private val compileConfiguration: ScriptCompilationConfiguration = simpleScriptCompilationConfiguration,
    private val evalConfiguration: ScriptEvaluationConfiguration = simpleScriptEvaluationConfiguration,
) : Closeable {
    private val currentLineCounter = AtomicInteger(0)

    fun nextCodeLine(code: String): SourceCode =
        SourceCodeTestImpl(
            currentLineCounter.getAndIncrement(),
            code
        )

    private val replCompiler: KJvmReplCompilerWithIdeServices by lazy {
        KJvmReplCompilerWithIdeServices()
    }

    private val compiledEvaluator: BasicJvmReplEvaluator by lazy {
        BasicJvmReplEvaluator()
    }

    @Suppress("DEPRECATION_ERROR")
    fun compile(code: SourceCode) = internalScriptingRunSuspend { replCompiler.compile(code, compileConfiguration) }

    @Suppress("DEPRECATION_ERROR")
    fun complete(code: SourceCode, cursor: Int) =
        internalScriptingRunSuspend { replCompiler.complete(code, cursor.toSourceCodePosition(code), compileConfiguration) }

    @Suppress("DEPRECATION_ERROR")
    fun eval(snippet: LinkedSnippet<out CompiledSnippet>) =
        internalScriptingRunSuspend { compiledEvaluator.eval(snippet, evalConfiguration) }

    override fun close() {

    }

}

internal class SourceCodeTestImpl(number: Int, override val text: String) : SourceCode {
    override val name: String? = "Line_$number"
    override val locationId: String? = "location_$number"
}

@JvmName("iterableToList")
fun <T> ResultWithDiagnostics<Iterable<T>>.toList() = this.valueOrNull()?.toList().orEmpty()

@JvmName("sequenceToList")
fun <T> ResultWithDiagnostics<Sequence<T>>.toList() = this.valueOrNull()?.toList().orEmpty()
