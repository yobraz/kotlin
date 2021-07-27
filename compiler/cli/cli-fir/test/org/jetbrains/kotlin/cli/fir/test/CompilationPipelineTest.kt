/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.fir.test

import com.intellij.openapi.util.io.FileUtil
import junit.framework.TestCase
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.fir.*
import org.jetbrains.kotlin.cli.js.K2JSCompiler
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.integration.KotlinIntegrationTestBase
import org.jetbrains.kotlin.js.config.JsConfig
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

private const val TEST_DATA_DIR = "compiler/cli/cli-fir/testData"

class CompilationPipelineTest : KotlinIntegrationTestBase() {

    @Test
    fun testHelloWorldClassicJvm() {
        val source = File(TEST_DATA_DIR, "helloWorld/helloWorld.kt").normalize().absolutePath

        withDestDir("base") { baseDest ->
            val (baseResult, _) = compileWithOutput {
                K2JVMCompiler().exec(it, "-Xuse-old-backend", "-d", baseDest.path, source)
            }
            TestCase.assertEquals(ExitCode.OK, baseResult)

            withDestDir("classic") { classicCliDest ->
                compileAndCompareDestinations(baseDest, classicCliDest) {
                    classicCliJvmCompile(listOf("-Xuse-old-backend", "-d", classicCliDest.path, source), it)
                }
            }

            withDestDir("oldFeBe") { classicFeBeDest ->
                compileAndCompareDestinations(baseDest, classicFeBeDest) { outStr ->
                    oldFeOldBeCompile(listOf("-d", classicFeBeDest.path, source), outStr).also {
                        outStr.flush()
                    }
                }
            }
        }
    }

    @Test
    fun testHelloWorldFirJvm() {
        val source = File(TEST_DATA_DIR, "helloWorld/helloWorld.kt").normalize().absolutePath

        val baseDest = FileUtil.createTempDirectory("base", "out").normalize()
        val (baseResult, _) = compileWithOutput {
            K2JVMCompiler().exec(it, "-Xuse-fir", "-d", baseDest.path, source)
        }
        TestCase.assertEquals(ExitCode.OK, baseResult)

        val classicCliDest = FileUtil.createTempDirectory("classic", "out")
        val (classicCliResult, _) = compileWithOutput {
            classicCliJvmCompile(listOf("-Xuse-fir", "-d", classicCliDest.path, source), it)
        }
        assertTrue(classicCliResult is ExecutionResult.Success)

        compareDirectories(baseDest, classicCliDest)

        val firDest = FileUtil.createTempDirectory("fir", "out")
        val (firResult, firOut) = compileWithOutput { outStr ->
            firCompile(listOf("-d", firDest.path, source), outStr).also {
                outStr.flush()
            }
        }
        assertTrue(firOut, firResult is ExecutionResult.Success)

        compareDirectories(baseDest, firDest)
    }

    @Test
    fun testHelloWorldJs() {
        val source = File(TEST_DATA_DIR, "helloWorld/helloWorld.kt").normalize().absolutePath

        withDestFile("base", ".js") { baseDest ->
            val (baseResult, baseOut) = compileWithOutput {
                K2JSCompiler().exec(it, "-Xir-produce-js", "-libraries", JsConfig.JS_STDLIB.joinToString(File.pathSeparator), "-output", baseDest.path, source)
            }
            assertEquals(baseOut, ExitCode.OK, baseResult)

            withDestFile("classic", ".js") { classicDest ->
                compileAndCompareDestinations(baseDest, classicDest, compareAsText = true) {
                    classicJSCompile(listOf("-libraries", JsConfig.JS_STDLIB.joinToString(File.pathSeparator), "-Xir-produce-js", "-output", classicDest.path, source), it)
                }
            }
        }
    }

    @Test
    fun testHelloWorldJsKLib() {
        val source = File(TEST_DATA_DIR, "helloWorld/helloWorld.kt").normalize().absolutePath

        withDestFile("base", ".klib") { baseDest ->
            val (baseResult, baseOut) = compileWithOutput {
                K2JSCompiler().exec(it, "-Xir-produce-klib-file", "-libraries", JsConfig.JS_STDLIB.joinToString(File.pathSeparator), "-output", baseDest.path, source)
            }
            assertEquals(baseOut, ExitCode.OK, baseResult)

            withDestFile("classic", ".klib") { classicDest ->
                compileAndCompareDestinations(baseDest, classicDest) {
                    classicJSCompile(listOf("-libraries", JsConfig.JS_STDLIB.joinToString(File.pathSeparator), "-Xir-produce-klib-file", "-output", classicDest.path, source), it)
                }
            }
        }
    }
}

internal inline fun <T> compileWithOutput(body: (PrintStream) -> T): Pair<T, String> {
    ByteArrayOutputStream().use { bos ->
        val (_, cerr, res) = captureOutErrRet { body(PrintStream(bos)) }
        assertFalse(cerr.contains("error", ignoreCase = true), cerr)
        return res to bos.toString().trim()
    }
}

internal fun compareDirectories(src: File, dst: File) {
    for (srcFile in src.walkTopDown()) {
        val dstFile = dst.resolve(srcFile.relativeTo(src))
        compareFiles(srcFile, dstFile)
    }
}

internal fun compareFiles(src: File, dst: File, message: String? = null) {
    assertTrue(dst.exists())
    assertEquals(src.isFile, dst.isFile, message)
    if (dst.isFile) {
        assertTrue(src.readBytes() contentEquals dst.readBytes(), message)
    }
}

internal fun compareTextFiles(src: File, dst: File, message: String? = null) {
    assertTrue(dst.exists())
    assertEquals(src.isFile, dst.isFile, message)
    if (dst.isFile) {
        val srcText = src.readText()
        val dstText = dst.readText()
        assertTrue(srcText contentEquals dstText, "$message\nExpected:\n$srcText\nGot:\n$dstText")
    }
}

internal inline fun <R, T : ExecutionResult<R>> compileAndCompareDestinations(
    baseDestination: File, compileDestination: File, compareAsText: Boolean = false, compileBody: (PrintStream) -> T
) {
    val (res, out) = compileWithOutput(compileBody)
    assertTrue(res is ExecutionResult.Success<*>, out)

    when {
        baseDestination.isDirectory && compileDestination.isDirectory ->
            compareDirectories(baseDestination, compileDestination)
        baseDestination.isFile && compileDestination.isFile ->
            if (compareAsText)
                compareTextFiles(baseDestination, compileDestination)
            else
                compareFiles(baseDestination, compileDestination)
        else -> fail("expecting either files or directories: $baseDestination vs $compileDestination")
    }
}

internal inline fun <T> captureOutErrRet(body: () -> T): Triple<String, String, T> {
    val outStream = ByteArrayOutputStream()
    val errStream = ByteArrayOutputStream()
    val prevOut = System.out
    val prevErr = System.err
    System.setOut(PrintStream(outStream))
    System.setErr(PrintStream(errStream))
    val ret = try {
        body()
    } finally {
        System.out.flush()
        System.err.flush()
        System.setOut(prevOut)
        System.setErr(prevErr)
    }
    return Triple(outStream.toString().trim(), errStream.toString().trim(), ret)
}

internal inline fun <T> withDestDir(prefix: String = "dest", body: (File) -> T): T {
    val dest = FileUtil.createTempDirectory(prefix, "out").normalize()
    return try {
        body(dest)
    } finally {
        dest.deleteRecursively()
    }
}

internal inline fun <T> withDestFile(prefix: String = "dest", extension: String = ".out", body: (File) -> T): T {
    val dest = FileUtil.createTempFile(prefix, extension).normalize()
    return try {
        body(dest)
    } finally {
        dest.deleteRecursively()
    }
}
