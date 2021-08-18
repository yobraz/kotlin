/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.tasks

import groovy.lang.Closure
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.compilerRunner.KotlinNativeCompilerRunner
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonToolOptions
import org.jetbrains.kotlin.gradle.internal.ensureParentDirsCreated
import org.jetbrains.kotlin.gradle.plugin.mpp.BitcodeEmbeddingMode
import org.jetbrains.kotlin.gradle.plugin.sources.DefaultLanguageSettingsBuilder
import org.jetbrains.kotlin.konan.target.CompilerOutputKind
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.project.model.LanguageSettings
import java.io.File
import javax.inject.Inject

open class KotlinNativeLinkArtifactTask @Inject constructor(
    @get:Input val konanTarget: KonanTarget,
    @get:Input val outputKind: CompilerOutputKind
) : DefaultTask() {

    @get:Input
    var baseName: String = project.name

    @get:InputDirectory
    var destinationDir: File = project.buildDir.resolve("out/${outputKind.name}/${konanTarget.name}")

    @get:Input
    var optimized: Boolean = false

    @get:Input
    var debuggable: Boolean = true

    @get:Input
    var enableEndorsedLibs: Boolean = false

    @get:Input
    var processTests: Boolean = false

    @get:Optional
    @get:Input
    var entryPoint: String? = null

    @get:Input
    var isStaticFramework: Boolean = false

    @get:Input
    var embedBitcode: BitcodeEmbeddingMode = BitcodeEmbeddingMode.DISABLE

    @get:Internal
    val languageSettings: LanguageSettings = DefaultLanguageSettingsBuilder(project)

    fun languageSettings(fn: LanguageSettings.() -> Unit) {
        languageSettings.fn()
    }

    fun languageSettings(fn: Closure<*>) {
        fn.delegate = languageSettings
        fn.call()
    }

    @get:Optional
    @get:Input
    val languageVersion: String?
        get() = languageSettings.languageVersion

    @get:Optional
    @get:Input
    val apiVersion: String?
        get() = languageSettings.apiVersion

    @get:Input
    val progressiveMode: Boolean
        get() = languageSettings.progressiveMode

    @get:Input
    val enabledLanguageFeatures: Set<String>
        get() = languageSettings.enabledLanguageFeatures

    @get:Input
    val optInAnnotationsInUse: Set<String>
        get() = languageSettings.optInAnnotationsInUse

    @get:InputFiles
    val libraries: List<File> = mutableListOf()

    @get:InputFiles
    val exportLibraries: List<File> = mutableListOf()

    @get:InputFiles
    val includeLibraries: List<File> = mutableListOf()

    @get:Input
    val linkerOptions: List<String> = mutableListOf()

    @get:Internal
    val kotlinOptions = object : KotlinCommonToolOptions {
        override var allWarningsAsErrors: Boolean = false
        override var suppressWarnings: Boolean = false
        override var verbose: Boolean = false
        override var freeCompilerArgs: List<String> = listOf()
    }

    fun kotlinOptions(fn: KotlinCommonToolOptions.() -> Unit) {
        kotlinOptions.fn()
    }

    fun kotlinOptions(fn: Closure<*>) {
        fn.delegate = kotlinOptions
        fn.call()
    }

    @get:Input
    val allWarningsAsErrors: Boolean
        get() = kotlinOptions.allWarningsAsErrors

    @get:Input
    val suppressWarnings: Boolean
        get() = kotlinOptions.suppressWarnings

    @get:Input
    val verbose: Boolean
        get() = kotlinOptions.verbose

    @get:Input
    val freeCompilerArgs: List<String>
        get() = kotlinOptions.freeCompilerArgs

    @get:OutputFile
    val outputFile: File
        get() {
            val outFileName = "${outputKind.prefix(konanTarget)}$baseName${outputKind.suffix(konanTarget)}".replace('-', '_')
            return destinationDir.resolve(outFileName)
        }

    @TaskAction
    fun link() {
        val outFile = outputFile
        outFile.ensureParentDirsCreated()

        val buildArgs = buildKotlinNativeBinaryLinkerArgs(
            outFile,
            optimized,
            debuggable,
            konanTarget,
            outputKind,
            libraries,
            languageSettings,
            enableEndorsedLibs,
            kotlinOptions,
            emptyList(),//todo CompilerPlugins
            processTests,
            entryPoint,
            embedBitcode,
            linkerOptions,
            isStaticFramework,
            exportLibraries,
            includeLibraries,
            emptyList()//todo external deps and cache
        )

        KotlinNativeCompilerRunner(project).run(buildArgs)
    }
}