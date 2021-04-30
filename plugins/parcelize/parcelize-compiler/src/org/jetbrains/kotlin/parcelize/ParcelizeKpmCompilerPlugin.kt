/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.parcelize

import org.jetbrains.kotlin.project.model.*

object ParcelizeKpmCompilerPlugin : KpmCompilerPlugin {
    override fun forMetadataCompilation(fragment: KotlinModuleFragment): PluginData? = null

    override fun forNativeMetadataCompilation(fragment: KotlinModuleFragment): PluginData? = null

    override fun forPlatformCompilation(variant: KotlinModuleVariant): PluginData? {
        if (variant.platform != KotlinPlatformTypeAttribute.ANDROID_JVM) return null

        return PluginData(
            pluginId = "org.jetbrains.kotlin.parcelize",
            artifact = PluginData.ArtifactCoordinates(
                group = "org.jetbrains.kotlin",
                artifact = "kotlin-parcelize-compiler"
            ),
            options = emptyList()
        )
    }
}