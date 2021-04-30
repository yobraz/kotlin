/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.ic

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analyzer.AbstractAnalyzerWithCompilerReport
import org.jetbrains.kotlin.backend.common.lower
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.backend.js.*
import org.jetbrains.kotlin.ir.backend.js.lower.generateTests
import org.jetbrains.kotlin.ir.backend.js.lower.moveBodilessDeclarationsToSeparatePlace
import org.jetbrains.kotlin.ir.backend.js.lower.moveBodilessDeclarationsToSeparatePlaceDelayed
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir.JsIrLinker
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.IrModuleToJsTransformer
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.persistent.PersistentIrFactory
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.js.config.DceRuntimeDiagnostic
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.resolver.KotlinLibraryResolveResult
import org.jetbrains.kotlin.name.FqName
import java.io.PrintWriter

// TODO test purpose only
// klib path -> ic data
val icCache = mutableMapOf<String, SerializedIcData>()

// TODO change API to support not only stdlib
fun prepareIcCaches(
    project: Project,
    analyzer: AbstractAnalyzerWithCompilerReport,
    configuration: CompilerConfiguration,
    allDependencies: KotlinLibraryResolveResult,
) {
    val irFactory = PersistentIrFactory()
    val controller = WholeWorldStageController()
    irFactory.stageController = controller

    // only process stdlib for now
    val stdlibResolved = findStdlib(allDependencies)
    val stdlibKlib = stdlibResolved.getFullList().single()

//    icCache.clear()

    icCache.computeIfAbsent(stdlibKlib.libraryFile.absolutePath) {
        val (context, deserializer, allModules) = prepareIr(
            project,
            MainModule.Klib(stdlibKlib),
            analyzer,
            configuration,
            stdlibResolved,
            emptyList(),
            emptySet(),
            null,
            false,
            false,
            irFactory,
            useStdlibCache = false,
        )

        val moduleFragment = allModules.single()

        moveBodilessDeclarationsToSeparatePlace(context, moduleFragment)

        generateTests(context, moduleFragment)

        lowerPreservingIcData(moduleFragment, context, controller)

        IcSerializer(
            context.irBuiltIns,
            context.mapping,
            irFactory,
            deserializer,
            moduleFragment
        ).serializeDeclarations(irFactory.allDeclarations)
    }
}

private fun findStdlib(allDependencies: KotlinLibraryResolveResult): KotlinLibraryResolveResult {
    var result: KotlinLibraryResolveResult? = null

    allDependencies.forEach { klib, _ ->
        val resolvedLib = allDependencies.filterRoots {
            it.library == klib
        }

        if (resolvedLib.getFullList().size == 1) result = resolvedLib
    }

    return result!!
}

fun loadIrForIc(
    linker: JsIrLinker,
    module: IrModuleFragment,
    context: JsIrBackendContext,
) {
//
//    val time = System.currentTimeMillis()
//
//    val icData = icCache.values.single() // TODO find a stable key present both in klib and module
//
//    val mover = moveBodilessDeclarationsToSeparatePlaceDelayed(module)
//
//    val icModuleDeserializer = IcModuleDeserializer(
//        context.irFactory as PersistentIrFactory,
//        context.mapping,
//        linker,
//        icData,
//        module.descriptor,
//        module
//    )
//    icModuleDeserializer.init()
//    icModuleDeserializer.deserializeAll()
//    icModuleDeserializer.postProcess()
//
//    mover.saveToContext(context)
//
//    println("${(System.currentTimeMillis() - time) / 1000.0}s")
//
//    linker.symbolTable.noUnboundLeft("Unbound symbols found")

    if (true) {

        val perFactory = context.irFactory as PersistentIrFactory
        val oldController = perFactory.stageController
        perFactory.stageController = StageController(100)

        println("==== Dumping ====")

        var actual = ""

        for (file in module.files) {
            actual += file.path + "\n"
            actual += context.irFactory.stageController.withStage(100) {
                var r = ""

                file.declarations.map { it.dumpKotlinLike() }.sorted().forEach { r += it }

                r
            }
            actual += "\n"
        }
        PrintWriter("/home/ab/vcs/kotlin/simple-dump.txt").use {
            it.print(actual)
        }

        perFactory.stageController = oldController
    }
}

private fun dumpIr(module: IrModuleFragment, fileName: String) {
    val dumpOptions = KotlinLikeDumpOptions(printElseAsTrue = true)

    var actual = ""

    for (file in module.files) {
        actual += file.path + "\n"
        actual += run {
            var r = ""

            file.declarations.map { it.dumpKotlinLike(dumpOptions) }.sorted().forEach { r += it }

            r
        }
        actual += "\n"
    }
    PrintWriter("/home/ab/vcs/kotlin/$fileName.txt").use {
        it.print(actual)
    }
}

fun icCompile(
    project: Project,
    mainModule: MainModule,
    analyzer: AbstractAnalyzerWithCompilerReport,
    configuration: CompilerConfiguration,
    allDependencies: KotlinLibraryResolveResult,
    friendDependencies: List<KotlinLibrary>,
    mainArguments: List<String>?,
    exportedDeclarations: Set<FqName> = emptySet(),
    generateFullJs: Boolean = true,
    generateDceJs: Boolean = false,
    dceRuntimeDiagnostic: DceRuntimeDiagnostic? = null,
    es6mode: Boolean = false,
    multiModule: Boolean = false,
    relativeRequirePath: Boolean = false,
    propertyLazyInitialization: Boolean,
    useStdlibCache: Boolean,
): CompilerResult {

    if (useStdlibCache) {
        // Lower and save stdlib IC data if needed
        prepareIcCaches(project, analyzer, configuration, allDependencies)
    }

    val irFactory = PersistentIrFactory()
    val controller = WholeWorldStageController()
    irFactory.stageController = controller

    val (context, deserializer, allModules, loweredIrLoaded) = prepareIr(
        project,
        mainModule,
        analyzer,
        configuration,
        allDependencies,
        friendDependencies,
        exportedDeclarations,
        dceRuntimeDiagnostic,
        es6mode,
        propertyLazyInitialization,
        irFactory,
        useStdlibCache,
    )

//    if (useStdlibCache) {
//        loadIrForIc(deserializer, allModules.first(), context)
//    }

    val modulesToLower = allModules.filter { it !in loweredIrLoaded }

    // This won't work incrementally
    modulesToLower.forEach { module ->
        moveBodilessDeclarationsToSeparatePlace(context, module)
    }

    // TODO should be done incrementally
    generateTests(context, allModules.last())

    modulesToLower.forEach {
        lowerPreservingIcData(it, context, controller)
    }

//    dumpIr(allModules.first(), "simple-dump${if (useStdlibCache) "-actual" else ""}")

    val transformer = IrModuleToJsTransformer(
        context,
        mainArguments,
        fullJs = generateFullJs,
        dceJs = generateDceJs,
        multiModule = multiModule,
        relativeRequirePath = relativeRequirePath,
    )

    return transformer.generateModule(allModules)
}

fun lowerPreservingIcData(module: IrModuleFragment, context: JsIrBackendContext, controller: WholeWorldStageController) {
    // Lower all the things
    controller.currentStage = 0

    pirLowerings.forEachIndexed { i, lowering ->
        controller.currentStage = i + 1
        when (lowering) {
            is DeclarationLowering ->
                lowering.declarationTransformer(context).lower(module)
            is BodyLowering ->
                lowering.bodyLowering(context).lower(module)
            // else -> TODO what about other lowerings?
        }
    }

    controller.currentStage = pirLowerings.size + 1
}

private fun prepareIr(
    project: Project,
    mainModule: MainModule,
    analyzer: AbstractAnalyzerWithCompilerReport,
    configuration: CompilerConfiguration,
    allDependencies: KotlinLibraryResolveResult,
    friendDependencies: List<KotlinLibrary>,
    exportedDeclarations: Set<FqName> = emptySet(),
    dceRuntimeDiagnostic: DceRuntimeDiagnostic? = null,
    es6mode: Boolean = false,
    propertyLazyInitialization: Boolean,
    irFactory: PersistentIrFactory,
    useStdlibCache: Boolean,
): PreparedIr {
    val (moduleFragment: IrModuleFragment, dependencyModules, irBuiltIns, symbolTable, deserializer, loweredIrLoaded) =
        loadIr(project, mainModule, analyzer, configuration, allDependencies, friendDependencies, irFactory) { path ->
            if (useStdlibCache) icCache[path] else null
        }

    val moduleDescriptor = moduleFragment.descriptor

    val allModules = when (mainModule) {
        is MainModule.SourceFiles -> dependencyModules + listOf(moduleFragment)
        is MainModule.Klib -> dependencyModules
    }

    val context = irFactory.withIntrinsicSignature("jsIntrinsics") {
        JsIrBackendContext(
            moduleDescriptor,
            irBuiltIns,
            symbolTable,
            allModules.first(),
            exportedDeclarations,
            configuration,
            es6mode = es6mode,
            dceRuntimeDiagnostic = dceRuntimeDiagnostic,
            propertyLazyInitialization = propertyLazyInitialization,
            irFactory = irFactory,
            mapping = deserializer.mapping,
        )
    }

    // Load declarations referenced during `context` initialization
    val irProviders = listOf(deserializer)
    ExternalDependenciesGenerator(symbolTable, irProviders).generateUnboundSymbolsAsDependencies()

    deserializer.postProcess()
    symbolTable.noUnboundLeft("Unbound symbols at the end of linker")

    deserializer.loadIcIr { moveBodilessDeclarationsToSeparatePlace(context, it) }

    return PreparedIr(context, deserializer, allModules, loweredIrLoaded)
}

data class PreparedIr(
    val context: JsIrBackendContext,
    val linker: JsIrLinker,
    val allModules: List<IrModuleFragment>,
    val loweredIrLoaded: Set<IrModuleFragment>,
)