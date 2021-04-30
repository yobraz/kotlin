/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.serialization.compiler.extensions

import org.jetbrains.kotlin.project.model.BasicKpmCompilerPlugin
import org.jetbrains.kotlin.project.model.PluginData
import org.jetbrains.kotlin.project.model.PluginOption

object SerializationKpmCompilerPlugin : BasicKpmCompilerPlugin() {
    override val pluginId = "org.jetbrains.kotlinx.serialization"

    override val pluginOptions: List<PluginOption> = emptyList()

    override fun commonPluginArtifact() = PluginData.ArtifactCoordinates(
        group = "org.jetbrains.kotlin",
        artifact = "kotlin-serialization"
    )

    override fun nativePluginArtifact() = PluginData.ArtifactCoordinates(
        group = "org.jetbrains.kotlin",
        artifact = "kotlin-serialization-unshaded"
    )
}