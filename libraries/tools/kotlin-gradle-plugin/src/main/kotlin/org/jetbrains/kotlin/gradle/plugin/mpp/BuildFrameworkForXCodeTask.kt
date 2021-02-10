/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.tasks.locateOrRegisterTask
import org.jetbrains.kotlin.gradle.tasks.registerTask
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName
import java.io.File

private val Project.parentBuildFrameworkForXCodeTask: TaskProvider<BuildFrameworkForXCodeTask>
    get() = locateOrRegisterTask("buildFrameworkForXCode") {
        it.group = "build"
        it.description = "Build all frameworks as requested by XCode's environment variables"
    }


fun Framework.buildForXCode(configure: BuildFrameworkForXCodeTask.() -> Unit = {}): TaskProvider<BuildFrameworkForXCodeTask> {
    val buildFrameworkForXCodeTaskName = lowerCamelCaseName("build", target.name, name, "forXCode")
    val buildFrameworkForXCodeTask = project.registerTask<BuildFrameworkForXCodeTask>(buildFrameworkForXCodeTaskName, listOf(this))
    buildFrameworkForXCodeTask.configure(configure)

    project.parentBuildFrameworkForXCodeTask.configure { parentTask ->
        if (
            konanTarget.architecture == XCodeEnvironment.requestedArchitecture &&
            buildType == XCodeEnvironment.requestedBuildType
        ) {
            parentTask.dependsOn(buildFrameworkForXCodeTask)
        }
    }


    return buildFrameworkForXCodeTask
}

@Suppress("LeakingThis")
open class BuildFrameworkForXCodeTask(
    @get:Internal val framework: Framework
) : Sync() {

    @get:OutputDirectory
    val outputDirectory: Property<File> = project.objects.property(File::class.java)
        .convention(project.buildDir.resolve("xcode-frameworks"))

    init {
        group = "build"
        into(outputDirectory)
        from(project.provider { outputDirectory })
        dependsOn(framework.linkTaskProvider)
    }
}
