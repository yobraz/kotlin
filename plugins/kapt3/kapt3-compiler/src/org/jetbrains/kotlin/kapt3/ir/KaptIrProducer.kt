/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kapt3.ir

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.backend.common.phaser.PhaseConfig
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.JvmIrCodegenFactory
import org.jetbrains.kotlin.backend.jvm.jvmPhases
import org.jetbrains.kotlin.codegen.ClassBuilderMode
import org.jetbrains.kotlin.codegen.OriginCollectingClassBuilderFactory
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.modules.TargetId
import org.jetbrains.kotlin.progress.ProgressIndicatorAndCompilationCanceledStatus
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext

class KaptIrProducer(private val compilerConfiguration: CompilerConfiguration) {

    fun transformToIr(
        project: Project,
        module: ModuleDescriptor,
        bindingContext: BindingContext,
        files: List<KtFile>
    ): JvmBackendContext {
        val builderFactory = OriginCollectingClassBuilderFactory(ClassBuilderMode.KAPT3)

        val targetId = TargetId(
            name = compilerConfiguration[CommonConfigurationKeys.MODULE_NAME] ?: module.name.asString(),
            type = "java-production"
        )

        val generationState = GenerationState.Builder(
            project,
            builderFactory,
            module,
            bindingContext,
            files,
            compilerConfiguration
        ).targetId(targetId)
            .isIrBackend(true)
            .build()

        generationState.beforeCompile();

        //todo less lowering
        val codegenFactory = JvmIrCodegenFactory(PhaseConfig(jvmPhases))
        //todo ignore errors probably
        val input = codegenFactory.convertToIr(generationState, files)
        val context = codegenFactory.prepareIr(input)

        return context
    }

}
