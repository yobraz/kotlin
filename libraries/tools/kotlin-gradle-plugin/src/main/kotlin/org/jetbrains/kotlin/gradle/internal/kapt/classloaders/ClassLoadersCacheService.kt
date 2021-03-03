/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.internal.kapt.classloaders

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL
import java.net.URLClassLoader

abstract class ClassLoadersCacheService : BuildService<ClassLoadersCacheServiceParams>, AutoCloseable {

    private val logger = LoggerFactory.getLogger(ClassLoadersCacheService::class.java)

    private val cache by lazy {
        logger.info("Creating new ClassLoaders cache")
        CachingClassLoadersProvider(parameters.size, URLClassLoader(parameters.parentClassLoaderUrls.toTypedArray()))
    }

    fun getClassLoader(bottom: List<File>, top: List<File>): ClassLoader = cache.getSplitted(bottom, top)

    override fun close() {
        logger.info("Closing ClassLoadersCacheService")
        cache.close()
    }
}

interface ClassLoadersCacheServiceParams : BuildServiceParameters {
    var size: Int
    var parentClassLoaderUrls: List<URL>
}


