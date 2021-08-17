/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.runners.ir.interpreter

import org.jetbrains.kotlin.backend.common.lower.partialCompileTimeEvaluationPhase
import org.jetbrains.kotlin.test.backend.handlers.PhasedIrDumpHandler
import org.jetbrains.kotlin.test.bind
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.builders.jvmArtifactsHandlersStep
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.directives.LanguageSettingsDirectives
import org.jetbrains.kotlin.test.model.ArtifactKinds
import org.jetbrains.kotlin.test.model.FrontendKinds
import org.jetbrains.kotlin.test.services.sourceProviders.IrInterpreterHelpersSourceFilesProvider

open class AbstractIrPartialInterpreterTest : AbstractCommonIrInterpreterTest() {
    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)
        with(builder) {
            globalDefaults {
                frontend = FrontendKinds.ClassicFrontend
                artifactKind = ArtifactKinds.Jvm
            }

            defaultDirectives {
                LanguageSettingsDirectives.LANGUAGE with "+PartialCompileTimeCalculations"
                CodegenTestDirectives.DUMP_IR_FOR_GIVEN_PHASES with partialCompileTimeEvaluationPhase
            }

            useAdditionalSourceProviders(::IrInterpreterHelpersSourceFilesProvider.bind(true))
            jvmArtifactsHandlersStep {
                useHandlers(::PhasedIrDumpHandler)
            }
        }
    }
}
