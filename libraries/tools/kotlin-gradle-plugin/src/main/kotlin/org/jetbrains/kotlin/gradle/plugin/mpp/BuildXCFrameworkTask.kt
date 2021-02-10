/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.tasks.locateOrRegisterTask
import org.jetbrains.kotlin.gradle.tasks.locateTask
import org.jetbrains.kotlin.gradle.tasks.registerTask
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName
import java.io.File
import javax.inject.Inject

private val Project.buildXCFrameworkTask: TaskProvider<Task>
    get() = project.locateOrRegisterTask("buildXCFrameworkForXCode") {
        it.group = "build"
    }

fun Framework.includeIntoXCFramework(
    xcFrameworkClassifier: String, configure: BuildXCFrameworkTask.() -> Unit = {}
): TaskProvider<BuildXCFrameworkTask> {
    val buildXCFrameworkTask = project.locateOrRegisterBuildXCFrameworkTask(xcFrameworkClassifier, buildType)
    buildXCFrameworkTask.configure { task -> task.from(this) }
    buildXCFrameworkTask.configure(configure)
    return buildXCFrameworkTask
}

private fun Project.locateOrRegisterBuildXCFrameworkTask(
    xcFrameworkClassifier: String, buildType: NativeBuildType
): TaskProvider<BuildXCFrameworkTask> {
    val name = lowerCamelCaseName("build", buildType.name.toLowerCase(), xcFrameworkClassifier.toLowerCase(), "XCFramework")
    val buildXCFrameworkTask = locateTask<BuildXCFrameworkTask>(name) ?: registerTask(name, listOf(xcFrameworkClassifier, buildType))

    project.buildXCFrameworkTask.configure { parentTask ->
        if (buildType == XCodeEnvironment.requestedBuildType) {
            parentTask.dependsOn(buildXCFrameworkTask)
        }
    }

    return buildXCFrameworkTask
}

open class BuildXCFrameworkTask @Inject constructor(
    @get:Input val xcFrameworkClassifier: String,
    @get:Input val buildType: NativeBuildType
) : DefaultTask() {

    private val inputFrameworks = mutableSetOf<Framework>()

    fun from(vararg frameworks: Framework) {
        from(frameworks.toList())
    }

    fun from(frameworks: List<Framework>) {
        frameworks.forEach { framework ->
            require(framework.buildType == buildType) {
                "Cannot put ${framework.buildType} framework ${framework.name} in $buildType .xcframework"
            }
        }
        frameworks.forEach { framework -> dependsOn(framework.linkTaskProvider) }
        inputFrameworks.addAll(frameworks)
    }

    @get:Internal
    val outputDirectory: Property<File> = project.objects.property(File::class.java)
        .convention(project.buildDir.resolve("xcode-frameworks"))

    @get:Internal
    val frameworkName: Property<String> = project.objects.property(String::class.java)
        .convention("${project.name}-$xcFrameworkClassifier")


    @get:OutputDirectory
    val frameworkFile = project.provider {
        outputDirectory.get().resolve(frameworkName.get() + ".xcframework")
    }

    @get:Classpath
    internal val inputFrameworkDirectories
        get() = inputFrameworks.map { framework -> framework.outputFile }

    @TaskAction
    internal fun run() {
        frameworkFile.get().deleteRecursively()
        val args = mutableListOf<String>().apply {
            add("xcrun")
            add("xcodebuild")
            add("-create-xcframework")
            inputFrameworkDirectories.forEach { inputFrameworkDirectory ->
                add("-framework")
                add(inputFrameworkDirectory.absolutePath)
            }
            add("-output")
            add(frameworkFile.get().absolutePath)
        }

        project.exec {
            it.commandLine(args)
            it.errorOutput = System.err
        }
    }

    @Internal
    override fun getGroup(): String = "build"

    @Internal
    override fun getDescription(): String? = "Merges ${inputFrameworks.joinToString(",", "[", "]") { it.name }} into a " +
            "single $buildType *$xcFrameworkClassifier.xcframework"
}
