/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin

import org.gradle.BuildAdapter
import org.gradle.BuildResult
import org.gradle.api.Project
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logging
import com.gradle.scan.plugin.BuildScanExtension
import org.gradle.api.provider.Provider
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.tooling.events.OperationCompletionListener
import org.jetbrains.kotlin.gradle.logging.kotlinDebug
import org.jetbrains.kotlin.gradle.plugin.internal.state.TaskExecutionResults
import org.jetbrains.kotlin.gradle.plugin.internal.state.TaskLoggers
import org.jetbrains.kotlin.gradle.plugin.stat.ReportStatistics
import org.jetbrains.kotlin.gradle.plugin.statistics.KotlinBuildEsStatListener
import org.jetbrains.kotlin.gradle.plugin.statistics.ReportStatisticsToBuildScan
import org.jetbrains.kotlin.gradle.plugin.statistics.ReportStatisticsToElasticSearch
import org.jetbrains.kotlin.gradle.report.configureReporting
import org.jetbrains.kotlin.gradle.utils.isConfigurationCacheAvailable
import java.util.*

//Support Gradle 6 and less. Move to
internal class KotlinGradleBuildServices private constructor(
    private val gradle: Gradle,
    private vararg val gradleListenerProviders: Provider<out OperationCompletionListener>
) : BuildAdapter() {

    companion object {
        private val CLASS_NAME = KotlinGradleBuildServices::class.java.simpleName
        const val FORCE_SYSTEM_GC_MESSAGE = "Forcing System.gc()"
        const val SHOULD_REPORT_MEMORY_USAGE_PROPERTY = "kotlin.gradle.test.report.memory.usage"

        val INIT_MESSAGE = "Initialized $CLASS_NAME"
        val DISPOSE_MESSAGE = "Disposed $CLASS_NAME"
        val ALREADY_INITIALIZED_MESSAGE = "$CLASS_NAME is already initialized"

        @field:Volatile
        internal var instance: KotlinGradleBuildServices? = null

//        @JvmStatic
//        @Synchronized
//        fun getInstance(gradle: Gradle): KotlinGradleBuildServices {
//            val log = Logging.getLogger(KotlinGradleBuildServices::class.java)
//
//            if (instance != null) {
//                log.kotlinDebug(ALREADY_INITIALIZED_MESSAGE)
//                return instance!!
//            }
//
//            val services = KotlinGradleBuildServices(gradle)
//            instance = services
//            if (!isGradleVersionAtLeast(6,1)) {
//                gradle.addBuildListener(services)
//                log.kotlinDebug(INIT_MESSAGE)
//            } else {
//                BuildEventsListenerRegistry.
//            }
//
//            services.buildStarted()
//            return services
//        }


        @JvmStatic
        @Synchronized
        fun getInstance(project: Project, listenerRegistryHolder: BuildEventsListenerRegistryHolder): KotlinGradleBuildServices {
            val log = Logging.getLogger(KotlinGradleBuildServices::class.java)
            val kotlinGradleListenerProvider: Provider<KotlinGradleBuildListener> = project.provider {
                KotlinGradleBuildListener(KotlinGradleFinishBuildHandler())
            }

            val kotlinGradleEsListenerProvider = project.provider {
                val listeners = project.rootProject.objects.listProperty(ReportStatistics::class.java)
                    .value(listOf<ReportStatistics>(ReportStatisticsToElasticSearch))
                project.rootProject.extensions.findByName("buildScan")
                    ?.also { listeners.add(ReportStatisticsToBuildScan(it as BuildScanExtension, UUID.randomUUID().toString(), "kotlin_version")) }
                KotlinBuildEsStatListener(project.rootProject.name, listeners.get(), UUID.randomUUID().toString())
            }


            if (instance != null) {
                log.kotlinDebug(ALREADY_INITIALIZED_MESSAGE)
//                instance!!.gradleListenerProviders.forEach { listenerRegistry.onTaskCompletion(it) }
                return instance!!
            }

            val gradle = project.gradle
            val services = KotlinGradleBuildServices(gradle, kotlinGradleListenerProvider, kotlinGradleEsListenerProvider)
            if (isConfigurationCacheAvailable(gradle)) {
                listenerRegistryHolder.listenerRegistry!!.onTaskCompletion(kotlinGradleListenerProvider)
                listenerRegistryHolder.listenerRegistry.onTaskCompletion(kotlinGradleEsListenerProvider)
            } else {
                gradle.addBuildListener(services)
                gradle.taskGraph.addTaskExecutionListener(kotlinGradleEsListenerProvider.get())
                log.kotlinDebug(INIT_MESSAGE)
            }
            instance = services

            services.buildStarted()
            return services
        }
    }


    private val log = Logging.getLogger(this.javaClass)
    private var buildHandler: KotlinGradleFinishBuildHandler? = null

    // There is function with the same name in BuildAdapter,
    // but it is called before any plugin can attach build listener
    fun buildStarted() {
        buildHandler = KotlinGradleFinishBuildHandler()
        buildHandler!!.buildStart()

        TaskLoggers.clear()
        TaskExecutionResults.clear()

        configureReporting(gradle)
    }

    override fun buildFinished(result: BuildResult) {
        buildHandler!!.buildFinished(result.gradle!!)
        instance = null
        log.kotlinDebug(DISPOSE_MESSAGE)
    }


    private val multipleProjectsHolder = KotlinPluginInMultipleProjectsHolder(
        trackPluginVersionsSeparately = true
    )

    @Synchronized
    internal fun detectKotlinPluginLoadedInMultipleProjects(project: Project, kotlinPluginVersion: String) {
        val onRegister = {
            gradle.taskGraph.whenReady {
                if (multipleProjectsHolder.isInMultipleProjects(project, kotlinPluginVersion)) {
                    val loadedInProjects = multipleProjectsHolder.getAffectedProjects(project, kotlinPluginVersion)!!
                    if (PropertiesProvider(project).ignorePluginLoadedInMultipleProjects != true) {
                        project.logger.warn("\n$MULTIPLE_KOTLIN_PLUGINS_LOADED_WARNING")
                        project.logger.warn(
                            MULTIPLE_KOTLIN_PLUGINS_SPECIFIC_PROJECTS_WARNING + loadedInProjects.joinToString(limit = 4) { "'$it'" }
                        )
                    }
                    project.logger.info(
                        "$MULTIPLE_KOTLIN_PLUGINS_SPECIFIC_PROJECTS_INFO: " +
                                loadedInProjects.joinToString { "'$it'" }
                    )
                }
            }
        }

        multipleProjectsHolder.addProject(
            project,
            kotlinPluginVersion,
            onRegister
        )
    }
}


