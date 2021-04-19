/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.fir

import com.intellij.openapi.Disposable
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.common.messages.IrMessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.setupCommonArguments
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.NoScopeRecordCliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.configureAdvancedJvmOptions
import org.jetbrains.kotlin.cli.jvm.configureExplicitContentRoots
import org.jetbrains.kotlin.cli.jvm.configureJdkHome
import org.jetbrains.kotlin.cli.jvm.setupJvmSpecificArguments
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.ir.util.IrMessageLogger
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import java.io.PrintStream

@Suppress("unused")
class ClassicFrontendBuilder(
    val rootDisposable: Disposable,
) : CompilationStageBuilder<Pair<K2JVMCompilerArguments, List<KtFile>>, AnalysisResult> {

    var configuration: CompilerConfiguration = CompilerConfiguration()

    var messageCollector: MessageCollector = MessageCollector.NONE

    var services: Services = Services.EMPTY

    override fun build(): CompilationStage<Pair<K2JVMCompilerArguments, List<KtFile>>, AnalysisResult> {
        configuration.apply {
            put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
        }
        return ClassicFrontend(rootDisposable, messageCollector, configuration)
    }

    operator fun invoke(body: ClassicFrontendBuilder.() -> Unit): ClassicFrontendBuilder {
        this.body()
        return this
    }
}

class ClassicFrontend internal constructor(
    val rootDisposable: Disposable,
    val messageCollector: MessageCollector,
    val configuration: CompilerConfiguration
) : CompilationStage<Pair<K2JVMCompilerArguments, List<KtFile>>, AnalysisResult> {

    override fun execute(
        input: Pair<K2JVMCompilerArguments, List<KtFile>>
    ): ExecutionResult<AnalysisResult> {
        val sourceFiles = input.second
        val environment =
            KotlinCoreEnvironment.createForProduction(
                rootDisposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES
            )
        val analyzer = AnalyzerWithCompilerReport(messageCollector, configuration.languageVersionSettings)
        analyzer.analyzeAndReport(sourceFiles) {
            val project = environment.project
            TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
                project,
                sourceFiles,
                NoScopeRecordCliBindingTrace(),
                environment.configuration,
                environment::createPackagePartProvider
            )
        }
        return ExecutionResult.Success(analyzer.analysisResult, emptyList())
    }
}

inline fun CompilationSession.buildJvmOldFrontend(body: ClassicFrontendBuilder.() -> Unit): ClassicFrontend =
    (createStageBuilder(ClassicFrontend::class) as ClassicFrontendBuilder).also { it.body() }.build() as ClassicFrontend

fun CompilationService.createLocalJvmOldFeOldBeCompilationSession() = createLocalCompilationSession {
    registerStage<ClassicFrontend, ClassicFrontendBuilder> { ClassicFrontendBuilder((this@createLocalJvmOldFeOldBeCompilationSession as LocalCompilationService).rootDisposable) }
    registerStage<ClassicBackend, ClassicBackendBuilder> { ClassicBackendBuilder() }
}

fun CompilationService.createLocalJvmOldFeIrCompilationSession() = createLocalCompilationSession {
    registerStage<ClassicFrontend, ClassicFrontendBuilder> { ClassicFrontendBuilder((this@createLocalJvmOldFeIrCompilationSession as LocalCompilationService).rootDisposable) }
    registerStage<IrJvmBackend, IrJvmBackendBuilder> { IrJvmBackendBuilder() }
}

fun oldFeOldBeCompile(args: List<String>, outStream: PrintStream): ExecutionResult<List<File>> {

    val service = LocalCompilationServiceBuilder().build()

    val session = service.createLocalJvmOldFeOldBeCompilationSession();

    val (arguments, collector) = args.toJvmArgumentsAndCollector(outStream)

    if (collector.hasErrors()) return collector.makeCompilationFailureResult()

    val configuration = CompilerConfiguration().apply {
        put(IrMessageLogger.IR_MESSAGE_LOGGER, IrMessageCollector(collector))
        setupCommonArguments(arguments)
        setupJvmSpecificArguments(arguments)
        // TODO: plugins
        configureJdkHome(arguments)
        put(CommonConfigurationKeys.MODULE_NAME, arguments.moduleName ?: JvmProtoBufUtil.DEFAULT_MODULE_NAME)
        configureExplicitContentRoots(arguments)
        configureAdvancedJvmOptions(arguments)
        arguments.destination?.let {
            val destination = File(it)
            if (destination.path.endsWith(".jar")) {
                put(JVMConfigurationKeys.OUTPUT_JAR, destination)
            } else {
                put(JVMConfigurationKeys.OUTPUT_DIRECTORY, destination)
            }
        }
    }

    val frontend = session.buildJvmOldFrontend {
        this.configuration = configuration
        this.messageCollector = collector
    }
}
