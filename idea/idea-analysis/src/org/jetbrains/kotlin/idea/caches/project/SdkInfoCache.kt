/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.concurrentMapOf
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.caches.project.cacheInvalidatingOnRootModifications

/**
 * Maintains and caches mapping ModuleInfo -> SdkInfo *form its dependencies*
 * (note that this SDK might be different from Project SDK)
 *
 * Cache is needed because one and the same library might (and usually does)
 * participate as dependency in several modules, so if someone queries a lot
 * of modules for their SDKs (ex.: determine built-ins for each module in a
 * project), we end up inspecting one and the same dependency multiple times.
 *
 * With projects with abnormally high amount of dependencies this might lead
 * to performance issues.
 */
interface SdkInfoCache {
    fun findOrGetCachedSdk(moduleInfo: ModuleInfo): SdkInfo?

    companion object {
        fun getInstance(project: Project): SdkInfoCache =
            ServiceManager.getService(project, SdkInfoCache::class.java)
    }
}

class SdkInfoCacheImpl(private val project: Project) : SdkInfoCache {
    @JvmInline
    value class SdkDependency(val sdk: SdkInfo?)

    private val cache: MutableMap<ModuleInfo, SdkDependency>
        get() = project.cacheInvalidatingOnRootModifications { concurrentMapOf() }

    override fun findOrGetCachedSdk(moduleInfo: ModuleInfo): SdkInfo? {
        return doFindSdk(moduleInfo).sdk
    }

    private fun doFindSdk(moduleInfo: ModuleInfo): SdkDependency {
        val visitedModuleInfos = mutableSetOf<ModuleInfo>()

        fun handleRecursivelyAndCache(currentModule: ModuleInfo): SdkDependency {
            visitedModuleInfos.add(currentModule)

            // positive result - is Sdk or already cached
            cache[currentModule]?.let { return it }
            if (currentModule is SdkInfo) {
                SdkDependency(currentModule).also { newDependency ->
                    cache[currentModule] = newDependency
                    return newDependency
                }
            }

            // no positive result on current level - try dependencies
            for (dependency in currentModule.dependencies()) {
                if (dependency !in visitedModuleInfos) {
                    val sdkFromDependency = handleRecursivelyAndCache(dependency)
                    when (sdkFromDependency.sdk) {
                        null -> continue // this dependency has no transitive dependency on SDK, but other might have
                        else -> {
                            cache[currentModule] = sdkFromDependency
                            return sdkFromDependency
                        }
                    }
                }
            }

            // no positive result for module info and all transitive deps
            SdkDependency(null).also { notFoundDependency ->
                cache[currentModule] = notFoundDependency
                return notFoundDependency
            }
        }

        return handleRecursivelyAndCache(moduleInfo)
    }
}