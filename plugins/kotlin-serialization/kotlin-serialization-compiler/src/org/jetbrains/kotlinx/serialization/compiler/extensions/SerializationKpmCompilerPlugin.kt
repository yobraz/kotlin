/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.serialization.compiler.extensions

import org.jetbrains.kotlin.project.model.BasicKpmCompilerPlugin
import org.jetbrains.kotlin.project.model.PluginData
import org.jetbrains.kotlin.project.model.PluginOption

class SerializationKpmCompilerPlugin(
    override val commonPluginArtifact: PluginData.ArtifactCoordinates,
    override val nativePluginArtifact: PluginData.ArtifactCoordinates
) : BasicKpmCompilerPlugin() {
    override val pluginId = "org.jetbrains.kotlinx.serialization"

    override val pluginOptions: List<PluginOption> = emptyList()
}