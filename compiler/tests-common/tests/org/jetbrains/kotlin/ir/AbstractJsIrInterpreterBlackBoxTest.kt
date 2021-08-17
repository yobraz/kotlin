/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir

import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrErrorExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.interpreter.IrInterpreter
import org.jetbrains.kotlin.ir.interpreter.IrInterpreterConfiguration
import org.jetbrains.kotlin.ir.interpreter.IrInterpreterEnvironment
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.resolve.BindingContext
import org.junit.jupiter.api.Assumptions
import java.io.File

abstract class AbstractJsIrInterpreterBlackBoxTest: AbstractKlibTextTestCase() {
    override fun doTest(filePath: String) {
        val irModule = buildIrModule(File(filePath))
        val irFiles = irModule.files
        Assumptions.assumeFalse(irFiles.singleOrNull()?.name == "sync.kt") { "Ignore `await` method call interpretation" }


        val irBuiltins = irModule.irBuiltins
        val configuration = myEnvironment.configuration
        val boxFunction = irFiles
            .flatMap { it.declarations }
            .filterIsInstance<IrFunction>()
            .first { it.name.asString() == "box" && it.valueParameters.isEmpty() }

        val boxIrCall = IrCallImpl(
            UNDEFINED_OFFSET, UNDEFINED_OFFSET, irBuiltins.stringType, boxFunction.symbol as IrSimpleFunctionSymbol, 0, 0
        )

        var passed = false
        try {
            val interpreterConfiguration = IrInterpreterConfiguration(
                maxStack = 100_000,
                maxCommands = 10_000_000,
                createNonCompileTimeObjects = true
            )
            val environment = IrInterpreterEnvironment(irBuiltins, interpreterConfiguration)

            @Suppress("UNCHECKED_CAST")
            val irInterpreter = IrInterpreter(environment, configuration[CommonConfigurationKeys.IR_BODY_MAP] as Map<IdSignature, IrBody>)
            val interpreterResult = irInterpreter.interpret(boxIrCall, irFiles.last())

            if (interpreterResult is IrErrorExpression) assertions.fail { interpreterResult.description }
            if (interpreterResult !is IrConst<*>) assertions.fail { "Expect const, but returned ${interpreterResult::class.java}" }
            assertions.assertEquals("OK", interpreterResult.value)
            if (interpreterResult.value == "OK") passed = true
        } catch (e: Throwable) {
            val message = e.message
            if (
                message == "Cannot interpret get method on top level non const properties" ||
                message == "Cannot interpret set method on top level properties" ||
                message == "Cannot interpret set method on property of object"
            ) {
                Assumptions.assumeFalse(true) { message }
                return
            }
            throw e
        } finally {
            if (!passed) {
                Assumptions.assumeFalse(modules.size != 1) { "Can't handle multi module project" }
                Assumptions.assumeTrue(configuration.languageVersionSettings.supportsFeature(LanguageFeature.ProperIeee754Comparisons)) { "ProperIeee754Comparisons must be enabled" }
                Assumptions.assumeFalse(modules.flatMap { it.files }.any { it.name.endsWith(".java") }) { "Can't interpret java files" }
                Assumptions.assumeFalse(modules.flatMap { it.files }.flatMap { it.originalContent.split("\n") }.any { it.contains("import kotlin.coroutines.*") }) { "Can't interpret coroutines" }
                //Assumptions.assumeFalse(TargetBackend.JVM_IR in testServices.moduleStructure.allDirectives[CodegenTestDirectives.IGNORE_BACKEND]) { "Ignore test because of jvm ir ignore" }
                additionalIgnores(modules)

                Assumptions.assumeTrue(irFiles.all { propertyChecker.apply { visitElement(it) }.canBeEvaluated }) { "Can't evaluate delegation" }
                Assumptions.assumeTrue(checker.apply { visitCall(boxIrCall, null) }.canBeEvaluated) { "" }
            }
        }
    }

    private fun buildIrModule(wholeFile: File): IrModuleFragment {
        val expectActualSymbols = mutableMapOf<DeclarationDescriptor, IrSymbol>()
        val ignoreErrors = AbstractIrGeneratorTestCase.shouldIgnoreErrors(wholeFile)
        val stdlib = loadKlibFromPath(listOf(runtimeKlibPath)).single()
        return buildFragmentAndLinkIt(stdlib, ignoreErrors, expectActualSymbols).first
    }
}