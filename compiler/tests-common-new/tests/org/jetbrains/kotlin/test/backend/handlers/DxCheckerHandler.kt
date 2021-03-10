/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.backend.handlers

import org.jetbrains.kotlin.codegen.D8Checker
import org.jetbrains.kotlin.codegen.getClassFiles
import org.jetbrains.kotlin.generators.util.GeneratorsFileUtil
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives.IGNORE_DEXING
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives.RUN_DEX_CHECKER
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.model.BinaryArtifacts
import org.jetbrains.kotlin.test.model.DependencyKind
import org.jetbrains.kotlin.test.model.DependencyRelation
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.dependencyProvider
import org.jetbrains.kotlin.test.services.jvm.compiledClassesManager
import java.io.File
import java.nio.file.Path

class DxCheckerHandler(testServices: TestServices) : JvmBinaryArtifactHandler(testServices) {
    override val directivesContainers: List<DirectivesContainer>
        get() = listOf(CodegenTestDirectives)

    override fun processModule(module: TestModule, info: BinaryArtifacts.Jvm) {
        if (RUN_DEX_CHECKER !in module.directives || IGNORE_DEXING in module.directives) return
        val compiledClassesManager = testServices.compiledClassesManager
        val reportProblems = module.targetBackend !in module.directives[CodegenTestDirectives.IGNORE_BACKEND]
        val classFilesFromFriendModules = module.friends
            .asSequence()
            .filter { it.relation == DependencyRelation.Dependency && it.kind == DependencyKind.Binary }
            .map { testServices.dependencyProvider.getTestModule(it.moduleName) }
            .flatMap { collectClassFiles(compiledClassesManager.getCompiledKotlinDirForModule(it)) }
            .toList()
        try {
            D8Checker.check(info.classFileFactory, classFilesFromFriendModules)
        } catch (e: Throwable) {
            if (reportProblems && !GeneratorsFileUtil.isTeamCityBuild) {
                try {
                    val javaDir = compiledClassesManager.getCompiledJavaDirForModule(module)
                    if (javaDir != null) {
                        println("Compiled Java files: ${javaDir.absolutePath}")
                    }
                    val kotlinDir = compiledClassesManager.getCompiledKotlinDirForModule(module)
                    println("Compiled Kotlin files: ${kotlinDir.absolutePath}")
                    info.classFileFactory.getClassFiles().forEach {
                        println(" * ${it.relativePath}")
                    }
                    if (classFilesFromFriendModules.isNotEmpty()) {
                        println("Additional classpath:")
                        for (path in classFilesFromFriendModules) {
                            println(" * $path")
                        }
                    }
                    println(info.classFileFactory.createText())
                } catch (_: Throwable) {
                    // In FIR we have factory which can't print bytecode
                    //   and it throws exception otherwise. So we need
                    //   ignore that exception to report original one
                    // TODO: fix original problem
                }
            }
            throw e
        }
    }

    private fun collectClassFiles(dir: File): Sequence<Path> {
        return dir.walk().filter { it.name.endsWith(".class") }.map { it.toPath() }
    }

    override fun processAfterAllModules(someAssertionWasFailed: Boolean) {}
}
