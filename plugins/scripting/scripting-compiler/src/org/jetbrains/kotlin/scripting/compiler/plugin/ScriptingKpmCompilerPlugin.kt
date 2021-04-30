/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.scripting.compiler.plugin

import org.jetbrains.kotlin.project.model.BasicKpmCompilerPlugin
import org.jetbrains.kotlin.project.model.PluginData
import org.jetbrains.kotlin.project.model.PluginOption
import org.jetbrains.kotlin.project.model.StringOption
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCommandLineProcessor.Companion.DISABLE_SCRIPT_DEFINITIONS_FROM_CLSSPATH_OPTION
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCommandLineProcessor.Companion.LEGACY_SCRIPT_RESOLVER_ENVIRONMENT_OPTION
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCommandLineProcessor.Companion.SCRIPT_DEFINITIONS_CLASSPATH_OPTION
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCommandLineProcessor.Companion.SCRIPT_DEFINITIONS_OPTION

class ScriptingKpmCompilerPlugin(
    private val scriptDefinitions: List<String>,
    private val scriptDefinitionsClasspath: List<String>,
    private val disableScriptDefinitionsFromClassPath: Boolean,
    private val legacyScriptResolverEnvironment: Map<String, String?>
) : BasicKpmCompilerPlugin() {
    override val pluginId = "kotlin.scripting"
    override val pluginOptions: List<PluginOption> by lazy {
        val options = mutableListOf<PluginOption>()

        scriptDefinitions.mapTo(options) { StringOption(SCRIPT_DEFINITIONS_OPTION.optionName, it) }
        scriptDefinitionsClasspath.mapTo(options) { StringOption(SCRIPT_DEFINITIONS_CLASSPATH_OPTION.optionName, it) }

        if (disableScriptDefinitionsFromClassPath) {
            options += StringOption(DISABLE_SCRIPT_DEFINITIONS_FROM_CLSSPATH_OPTION.optionName, "true")
        }

        legacyScriptResolverEnvironment.mapTo(options) {
            StringOption(LEGACY_SCRIPT_RESOLVER_ENVIRONMENT_OPTION.optionName, "${it.key}=${it.value}")
        }

        options
    }

    override fun commonPluginArtifact() = PluginData.ArtifactCoordinates(
        group = "org.jetbrains.kotlin",
        artifact = "kotlin-scripting-compiler-embeddable"
    )

    override fun nativePluginArtifact(): PluginData.ArtifactCoordinates? = null
}