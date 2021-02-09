/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Property
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.tasks.locateOrRegisterTask
import org.jetbrains.kotlin.gradle.tasks.registerTask
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName
import org.jetbrains.kotlin.konan.target.Architecture
import java.io.File
import javax.inject.Inject

internal fun Framework.registerBuildFrameworkForXCodeTask(): TaskProvider<BuildFrameworkForXCode> {
    val taskName = lowerCamelCaseName("build", target.name, name, "forXCode")
    val buildFrameworkForXCodeTask = project.registerTask<BuildFrameworkForXCode>(taskName, listOf(this))

    if (isRequestedByXCodeEnvironmentVariables()) {
        project.maybeCreateBuildFrameworkForXCodeParentTask().configure { parentTask ->
            parentTask.dependsOn(buildFrameworkForXCodeTask)
        }
    }

    return buildFrameworkForXCodeTask
}

private fun Project.maybeCreateBuildFrameworkForXCodeParentTask(): TaskProvider<Task> {
    return locateOrRegisterTask("buildFrameworkForXCode") {
        it.group = "build"
        it.description = "Build all frameworks as requested by XCode's environment variables"
    }
}

@Suppress("LeakingThis")
open class BuildFrameworkForXCode @Inject constructor(private val framework: Framework) : Sync() {

    @get:OutputDirectory
    val outputDirectory: Property<File> = project.objects.property(File::class.java)
        .convention(project.buildDir.resolve("xcode-frameworks"))

    init {
        group = "build"
        dependsOn(framework.linkTaskProvider)
        from(project.provider { framework.outputDirectory })
        into(outputDirectory)
    }
}

private fun Framework.isRequestedByXCodeEnvironmentVariables(): Boolean {
    return isNativeBuildTypeRequestedByXCode() && isArchitectureRequestedByXCode()
}

private fun Framework.isNativeBuildTypeRequestedByXCode(): Boolean {
    val configuration: String? = System.getenv("CONFIGURATION")
    val requestedNativeBuildType = when (configuration?.toLowerCase()) {
        null -> NativeBuildType.DEBUG
        "debug" -> NativeBuildType.DEBUG
        "release" -> NativeBuildType.RELEASE
        else -> throw IllegalArgumentException(
            "Unexpected environment variable 'CONFIGURATION': $configuration\n" +
                    "Expected one of ${NativeBuildType.values().joinToString { it.name }}"
        )
    }
    return this.buildType == requestedNativeBuildType
}

private fun Framework.isArchitectureRequestedByXCode(): Boolean {
    val sdkName: String? = System.getenv("SDK_NAME")
    val requestedArchitecture = if (sdkName.orEmpty().startsWith("iphoneos")) Architecture.ARM64 else Architecture.X64
    return this.konanTarget.architecture == requestedArchitecture
}
