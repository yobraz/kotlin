/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir

import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.toBooleanLenient


private val USE_BE_IR = System.getProperty("fir.bench.fe1.useIR", "false").toBooleanLenient()!!
internal val JVM_TARGET = System.getProperty("fir.bench.jvmTarget", "1.8")!!

class FE1FullPipelineModularizedTest : AbstractFullPipelineModularizedTest() {
    override fun configureArguments(args: K2JVMCompilerArguments, moduleData: ModuleData) {
        args.useIR = USE_BE_IR
        args.useOldBackend = !USE_BE_IR
        args.useFir = false
        args.jvmDefault = "compatibility"
        args.apiVersion = LANGUAGE_VERSION
        args.optIn = arrayOf(
            "kotlin.RequiresOptIn",
            "kotlin.contracts.ExperimentalContracts",
            "kotlin.io.path.ExperimentalPathApi",
            "org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI"
        )
        args.jvmTarget = JVM_TARGET
        args.multiPlatform = true
    }

    fun testTotalKotlin() {
        for (i in 0 until PASSES) {
            println("Pass $i")
            runTestOnce(i)
        }
    }
}