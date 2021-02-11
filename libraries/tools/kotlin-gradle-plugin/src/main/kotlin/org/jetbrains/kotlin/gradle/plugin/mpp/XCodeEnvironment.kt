/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp

import org.jetbrains.kotlin.konan.target.Architecture

object XCodeEnvironment {
    val activeBuildType: NativeBuildType
        get() = when (val configuration = System.getenv("CONFIGURATION")?.toLowerCase()) {
            null -> NativeBuildType.DEBUG
            "debug" -> NativeBuildType.DEBUG
            "release" -> NativeBuildType.RELEASE
            else -> throw IllegalArgumentException(
                "Unexpected environment variable 'CONFIGURATION': $configuration\n" +
                        "Expected one of ${NativeBuildType.values().joinToString { it.name }}"
            )
        }

    val activeArchitecture: Architecture
        get() {
            val sdkName = System.getenv("SDK_NAME") ?: return Architecture.X64
            return if (sdkName.startsWith("iphoneos")) Architecture.ARM64 else Architecture.X64
        }
}
