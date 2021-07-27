/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.fir

import org.jetbrains.kotlin.backend.common.lower.ExpectDeclarationRemover
import org.jetbrains.kotlin.backend.common.phaser.invokeToplevel
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.ir.backend.js.*
import org.jetbrains.kotlin.ir.backend.js.lower.generateTests
import org.jetbrains.kotlin.ir.backend.js.lower.moveBodilessDeclarationsToSeparatePlace
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir.JsIrLinker
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.IrModuleToJsTransformer
import org.jetbrains.kotlin.ir.declarations.StageController
import org.jetbrains.kotlin.ir.declarations.persistent.PersistentIrFactory
import org.jetbrains.kotlin.ir.util.ExternalDependenciesGenerator
import org.jetbrains.kotlin.ir.util.IrMessageLogger
import org.jetbrains.kotlin.ir.util.noUnboundLeft
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.library.KotlinAbiVersion

class ClassicJsIrBackendResult

class ClassicJsIrBackendBuilder : CompilationStageBuilder<FrontendToIrConverterResult, ClassicJsIrBackendResult> {

    var outputFilePath: String? = null

    var abiVersion: KotlinAbiVersion = KotlinAbiVersion.CURRENT

    var jsOutputName: String? = null

    override fun build(): CompilationStage<FrontendToIrConverterResult, ClassicJsIrBackendResult> {
        return ClassicJsIrBackend(outputFilePath!!, abiVersion, jsOutputName)
    }

    operator fun invoke(body: ClassicJsIrBackendBuilder.() -> Unit): ClassicJsIrBackendBuilder {
        this.body()
        return this
    }
}

class ClassicJsIrBackend internal constructor(
    val outputKlibPath: String,
    var abiVersion: KotlinAbiVersion,
    var jsOutputName: String?
) : CompilationStage<FrontendToIrConverterResult, ClassicJsIrBackendResult> {

    override fun execute(
        input: FrontendToIrConverterResult
    ): ExecutionResult<ClassicJsIrBackendResult> {
        val configuration = input.configuration

        val messageLogger = configuration[IrMessageLogger.IR_MESSAGE_LOGGER] ?: IrMessageLogger.None

        val moduleName = configuration[CommonConfigurationKeys.MODULE_NAME]!!

        val (moduleFragment, dependencyModules, icData, symbolTable, irBuiltIns, irFactory, bindingContext, project, files, dependenciesList, expectDescriptorToSymbol, hasErrors) = input

        if (!configuration.expectActualLinker) {
            moduleFragment.acceptVoid(ExpectDeclarationRemover(symbolTable, false))
        }

        val moduleDescriptor = moduleFragment.descriptor
        val mainModule = MainModule.SourceFiles(files)

        val allModules = dependencyModules + listOf(moduleFragment)

        val context = JsIrBackendContext(
            moduleDescriptor,
            irBuiltIns,
            symbolTable,
            // TODO: seems unused in JS, delete the parameter from JsIrBackendContext
            moduleFragment,
            additionalExportedDeclarationNames = emptySet(),
            configuration = configuration,
            es6mode = false,
//            dceRuntimeDiagnostic = RuntimeDiagnostic.resolve(
//                arguments.irDceRuntimeDiagnostic,
//                messageCollector
//            ),
//            propertyLazyInitialization = arguments.irPropertyLazyInitialization,
//            legacyPropertyAccess = arguments.irLegacyPropertyAccess,
//            baseClassIntoMetadata = arguments.irBaseClassInMetadata,
//            safeExternalBoolean = arguments.irSafeExternalBoolean,
//            safeExternalBooleanDiagnostic = RuntimeDiagnostic.resolve(
//                arguments.irSafeExternalBooleanDiagnostic,
//                messageCollector
//            ),
        )

        // TODO: check if such a fallback JsIrLinker could work
        val deserializer = input.irDeserializer ?: JsIrLinker(moduleDescriptor, messageLogger, irBuiltIns, symbolTable, null)

        // Load declarations referenced during `context` initialization
        val irProviders = listOf(deserializer)
        ExternalDependenciesGenerator(symbolTable, irProviders).generateUnboundSymbolsAsDependencies()

        deserializer.postProcess()
        symbolTable.noUnboundLeft("Unbound symbols at the end of linker")

        allModules.forEach { module ->
            moveBodilessDeclarationsToSeparatePlace(context, module)
        }

        // TODO should be done incrementally
        generateTests(context, allModules.last())

        val dceDriven = false
        val lowerPerModule = false

        if (dceDriven) {
            val controller = MutableController(context, pirLowerings)

            check(irFactory is PersistentIrFactory)
            irFactory.stageController = controller

            controller.currentStage = controller.lowerings.size + 1

            eliminateDeadDeclarations(allModules, context)

            irFactory.stageController = StageController(controller.currentStage)

            val transformer = IrModuleToJsTransformer(
                context,
                null,
                fullJs = true,
                dceJs = false,
//                multiModule = multiModule,
//                relativeRequirePath = relativeRequirePath,
//                moduleToName = moduleToName,
            )
            transformer.generateModule(allModules)
        } else {
            // TODO is this reachable when lowerPerModule == true?
            if (lowerPerModule) {
                val controller = WholeWorldStageController()
                check(irFactory is PersistentIrFactory)
                irFactory.stageController = controller
                allModules.forEach {
                    lowerPreservingIcData(it, context, controller)
                }
                irFactory.stageController = object : StageController(irFactory.stageController.currentStage) {}
            } else {
                jsPhases.invokeToplevel(configuration.get(CLIConfigurationKeys.PHASE_CONFIG)!!, context, allModules)
            }

            val transformer = IrModuleToJsTransformer(
                context,
                null,
//                fullJs = generateFullJs,
//                dceJs = generateDceJs,
//                multiModule = multiModule,
//                relativeRequirePath = relativeRequirePath,
//                moduleToName = moduleToName,
            )
            transformer.generateModule(allModules)
        }

        return ExecutionResult.Success(
            ClassicJsIrBackendResult(),
            emptyList()
        )
    }
}

