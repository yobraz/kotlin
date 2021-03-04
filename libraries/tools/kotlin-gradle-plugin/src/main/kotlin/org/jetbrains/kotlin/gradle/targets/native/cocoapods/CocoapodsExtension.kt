/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("PackageDirectoryMismatch") // Old package for compatibility
package org.jetbrains.kotlin.gradle.plugin.cocoapods

import groovy.lang.Closure
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.util.ConfigureUtil
import org.jetbrains.kotlin.gradle.plugin.cocoapods.CocoapodsExtension.CocoapodsDependency.PodLocation.*
import org.jetbrains.kotlin.gradle.utils.newProperty
import java.io.File
import java.net.URI

private fun String.asModuleName() = this
    .split("/")[0]     // Pick the module name from a subspec name.
    .replace('-', '_') // Support pods with dashes in names (see https://github.com/JetBrains/kotlin-native/issues/2884).

abstract class PodDependencyHolder(@Internal val project: Project) {
    abstract val pods: NamedDomainObjectSet<CocoapodsExtension.CocoapodsDependency>

    // For some reason Gradle doesn't consume the @Nested annotation on NamedDomainObjectContainer.
    @get:Nested
    val podsAsTaskInput: List<CocoapodsExtension.CocoapodsDependency>
        get() = pods.toList()

    abstract fun addToPods(dependency: CocoapodsExtension.CocoapodsDependency)

    /**
     * Add a CocoaPods dependency to the pod built from this project.
     */
    @JvmOverloads
    fun pod(name: String, version: String? = null, path: File? = null, moduleName: String = name.asModuleName()) {
        // Empty string will lead to an attempt to create two podDownload tasks.
        // One is original podDownload and second is podDownload + pod.name
        require(name.isNotEmpty()) { "Please provide not empty pod name to avoid ambiguity" }
        var podSource = path
        if (path != null && !path.isDirectory) {
            val pattern = "\\W*pod(.*\"${name}\".*)".toRegex()
            val buildScript = project.buildFile
            val lines = buildScript.readLines()
            val lineNumber = lines.indexOfFirst { pattern.matches(it) }
            val warnMessage = if (lineNumber != -1) run {
                val lineContent = lines[lineNumber].trimIndent()
                val newContent = lineContent.replace(path.name, "")
                """
                |Deprecated DSL found on ${buildScript.absolutePath}${File.pathSeparator}${lineNumber + 1}:
                |Found: "${lineContent}"
                |Expected: "${newContent}"
                |Please, change the path to avoid this warning.
                |
            """.trimMargin()
            } else
                """
                |Deprecated DSL is used for pod "$name".
                |Please, change its path from ${path.path} to ${path.parentFile.path} 
                |
            """.trimMargin()
            project.logger.warn(warnMessage)
            podSource = path.parentFile
        }
        addToPods(CocoapodsExtension.CocoapodsDependency(name, moduleName, version, podSource?.let { Path(it) }))
    }


    /**
     * Add a CocoaPods dependency to the pod built from this project.
     */
    fun pod(name: String, configure: CocoapodsExtension.CocoapodsDependency.() -> Unit) {
        // Empty string will lead to an attempt to create two podDownload tasks.
        // One is original podDownload and second is podDownload + pod.name
        require(name.isNotEmpty()) { "Please provide not empty pod name to avoid ambiguity" }
        val dependency = CocoapodsExtension.CocoapodsDependency(name, name.split("/")[0])
        dependency.configure()
        addToPods(dependency)
    }

    /**
     * Add a CocoaPods dependency to the pod built from this project.
     */
    fun pod(name: String, configure: Closure<*>) = pod(name) {
        ConfigureUtil.configure(configure, this)
    }

}

open class CocoapodsExtension(project: Project) : PodDependencyHolder(project) {
    @get:Input
    val version: String
        get() {
            require(project.version != Project.DEFAULT_VERSION) {
                """
                Cocoapods Integration requires version of this project to be specified.
                Please, add line 'version = "<version>"' to project's build file.
                For more details, please, see https://guides.cocoapods.org/syntax/podspec.html#version 
            """.trimIndent()
            }
            return project.version.toString()
        }

    /**
     * Configure authors of the pod built from this project.
     */
    @Optional
    @Input
    var authors: String? = null

    /**
     * Configure existing file `Podfile`.
     */
    @Optional
    @InputFile
    var podfile: File? = null

    @get:Input
    internal var needPodspec: Boolean = true

    /**
     * Setup plugin not to produce podspec file for cocoapods section
     */
    fun noPodspec() {
        needPodspec = false
    }

    /**
     * Setup cocoapods-generate to produce xcodeproj compatible with static libraries
     */
    fun useLibraries() {
        useLibraries = true
    }

    @get:Input
    internal var useLibraries: Boolean = false

    /**
     * Configure license of the pod built from this project.
     */
    @Optional
    @Input
    var license: String? = null

    /**
     * Configure description of the pod built from this project.
     */
    @Optional
    @Input
    var summary: String? = null

    /**
     * Configure homepage of the pod built from this project.
     */
    @Optional
    @Input
    var homepage: String? = null

    private val _platformSettings = mapOf(
        "ios" to PodspecPlatformSettings("ios", project),
        "osx" to PodspecPlatformSettings("osx", project),
        "tvos" to PodspecPlatformSettings("tvos", project),
        "watchos" to PodspecPlatformSettings("watchos", project)
    )

    @Deprecated("Explicit modifying of this object is deprecated", ReplaceWith("ios(configure: PodspecPlatformSettings.() -> Unit)"))
    @get:Nested
    val ios: PodspecPlatformSettings by _platformSettings

    fun ios(configure: PodspecPlatformSettings.() -> Unit) = configure(ios)

    fun ios(configure: Closure<*>) = ios {
        ConfigureUtil.configure(configure, this)
    }

    @Deprecated("Explicit modifying of this object is deprecated", ReplaceWith("osx(configure: PodspecPlatformSettings.() -> Unit)"))
    @get:Nested
    val osx: PodspecPlatformSettings by _platformSettings

    fun osx(configure: PodspecPlatformSettings.() -> Unit) = configure(osx)

    fun osx(configure: Closure<*>) = osx {
        ConfigureUtil.configure(configure, this)
    }

    @Deprecated("Explicit modifying of this object is deprecated", ReplaceWith("tvos(configure: PodspecPlatformSettings.() -> Unit)"))
    @get:Nested
    val tvos: PodspecPlatformSettings by _platformSettings

    fun tvos(configure: PodspecPlatformSettings.() -> Unit) = configure(tvos)

    fun tvos(configure: Closure<*>) = tvos {
        ConfigureUtil.configure(configure, this)
    }

    @Deprecated("Explicit modifying of this object is deprecated!", ReplaceWith("watchos(configure: PodspecPlatformSettings.() -> Unit)"))
    @get:Nested
    val watchos: PodspecPlatformSettings by _platformSettings

    fun watchos(configure: PodspecPlatformSettings.() -> Unit) = configure(watchos)

    fun watchos(configure: Closure<*>) = watchos {
        ConfigureUtil.configure(configure, this)
    }

    /**
     * Configure framework name of the pod built from this project.
     */
    @Input
    var frameworkName: String = project.name.asValidFrameworkName()

    @get:Internal
    override val pods: NamedDomainObjectSet<CocoapodsDependency>
        get() = project.container(CocoapodsDependency::class.java).apply {
            _platformSettings.values.forEach { platform ->
                platform.pods.all {
                    addLater(project.provider { it })
                }
            }
        }

    override fun addToPods(dependency: CocoapodsDependency) {
        _platformSettings.values.forEach { it.addToPods(dependency) }
    }

    @get:Nested
    internal val specRepos = SpecRepos()

    /**
     * Add spec repositories (note that spec repository is different from usual git repository).
     * Please refer to <a href="https://guides.cocoapods.org/making/private-cocoapods.html">cocoapods documentation</a>
     * for additional information.
     * Default sources (cdn.cocoapods.org) implicitly included.
     */
    fun specRepos(configure: SpecRepos.() -> Unit) = specRepos.configure()

    /**
     * Add spec repositories (note that spec repository is different from usual git repository).
     * Please refer to <a href="https://guides.cocoapods.org/making/private-cocoapods.html">cocoapods documentation</a>
     * for additional information.
     * Default sources (cdn.cocoapods.org) implicitly included.
     */
    fun specRepos(configure: Closure<*>) = specRepos {
        ConfigureUtil.configure(configure, this)
    }

    data class CocoapodsDependency(
        private val name: String,
        @get:Input var moduleName: String,
        @get:Optional @get:Input var version: String? = null,
        @get:Optional @get:Nested var source: PodLocation? = null,
        @get:Internal var extraOpts: List<String> = listOf(),
        @get:Internal var packageName: String = "cocoapods.$moduleName"
    ) : Named {
        @Input
        override fun getName(): String = name

        /**
         * Url to archived (tar, jar, zip) pod folder, that should contain the podspec file and all sources required by it.
         *
         * Archive name should match pod name.
         *
         * @param url url to tar, jar or zip archive.
         * @param flatten does archive contains subdirectory that needs to be expanded
         */
        @JvmOverloads
        fun url(url: String, flatten: Boolean = false): PodLocation = Url(URI(url), flatten)

        /**
         * Path to local pod
         */
        fun path(podspecDirectory: String): PodLocation = Path(File(podspecDirectory))

        /**
         * Path to local pod
         */
        fun path(podspecDirectory: File): PodLocation = Path(podspecDirectory)

        /**
         * Configure pod from git repository. The podspec file is expected to be in the repository root.
         */
        @JvmOverloads
        fun git(url: String, configure: (Git.() -> Unit)? = null): PodLocation {
            val git = Git(URI(url))
            if (configure != null) {
                git.configure()
            }
            return git
        }

        /**
         * Configure pod from git repository. The podspec file is expected to be in the repository root.
         */
        fun git(url: String, configure: Closure<*>) = git(url) {
            ConfigureUtil.configure(configure, this)
        }

        sealed class PodLocation {
            internal abstract fun getLocalPath(project: Project, podName: String): String

            data class Url(
                @get:Input val url: URI,
                @get:Input var flatten: Boolean
            ) : PodLocation() {
                override fun getLocalPath(project: Project, podName: String): String {
                    return project.cocoapodsBuildDirs.externalSources("url").resolve(podName).absolutePath
                }
            }

            data class Path(@get:InputDirectory val dir: File) : PodLocation() {
                override fun getLocalPath(project: Project, podName: String): String {
                    return dir.absolutePath
                }
            }

            data class Git(
                @get:Input val url: URI,
                @get:Input @get:Optional var branch: String? = null,
                @get:Input @get:Optional var tag: String? = null,
                @get:Input @get:Optional var commit: String? = null
            ) : PodLocation() {
                override fun getLocalPath(project: Project, podName: String): String {
                    return project.cocoapodsBuildDirs.externalSources("git").resolve(podName).absolutePath
                }
            }
        }
    }

    class PodspecPlatformSettings(
        private val name: String,
        project: Project
    ) : Named, PodDependencyHolder(project) {

        @get:Internal
        var deploymentTarget: String?
            get() = deploymentTargetProperty.orNull
            set(value) = deploymentTargetProperty.set(project.provider { value })

        @get:Optional
        @get:Input
        val deploymentTargetAsInput: String?
            get() = deploymentTargetProperty.orNull

        private val deploymentTargetProperty: Property<String> = project.newProperty()

        private val _pods = project.container(CocoapodsDependency::class.java)

        /**
         * Returns a list of pod dependencies.
         */
        // Already taken into account as a task input in the [podsAsTaskInput] property.
        @get:Internal
        override val pods: NamedDomainObjectSet<CocoapodsDependency>
            get() = _pods

        override fun addToPods(dependency: CocoapodsDependency) {
            val name = dependency.name
            check(pods.findByName(name) == null) { "Project already has a CocoaPods dependency with name $name" }
            pods.add(dependency)
        }

        @Input
        override fun getName(): String = name
    }

    class SpecRepos {
        @get:Internal
        internal val specRepos = mutableSetOf(URI("https://cdn.cocoapods.org"))

        fun url(url: String) {
            specRepos.add(URI(url))
        }

        @Input
        internal fun getAll(): Collection<URI> {
            return specRepos
        }
    }
}