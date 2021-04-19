/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.fir

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.cli.common.arguments.validateArguments
import org.jetbrains.kotlin.cli.common.messages.*
import java.io.File
import java.io.PrintStream

fun List<String>.toJvmArguments(): K2JVMCompilerArguments {
    val arguments = K2JVMCompilerArguments()
    parseCommandLineArguments(this, arguments)
    return arguments
}

fun K2JVMCompilerArguments.validate(messageCollector: MessageCollector): Boolean =
    validateArguments(errors)?.let {
        messageCollector.report(CompilerMessageSeverity.ERROR, it)
        false
    } ?: true

fun List<String>.toJvmArgumentsAndCollector(messageStream: PrintStream = System.err): Pair<K2JVMCompilerArguments, MessageCollector> {
    val arguments = toJvmArguments()
    val collector =
        GroupingMessageCollector(
            PrintingMessageCollector(messageStream, MessageRenderer.WITHOUT_PATHS, arguments.verbose),
            arguments.allWarningsAsErrors
        )
    arguments.validate(collector)
    return arguments to collector
}

@Suppress("unused")
fun MessageCollector.makeCompilationFailureResult(): ExecutionResult<List<File>> =
    ExecutionResult.Failure(ExitCode.COMPILATION_ERROR, emptyList())
