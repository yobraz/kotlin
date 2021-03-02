/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.internal.kapt.classloaders

import com.google.common.cache.CacheBuilder
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentMap

interface ClassLoadersProvider {

    fun getForClassPath(files: List<File>): ClassLoader

}

class CreatingClassloadersProvider(private val parentClassLoader: ClassLoader = ClassLoader.getSystemClassLoader()) : ClassLoadersProvider {
    override fun getForClassPath(files: List<File>): ClassLoader =
        URLClassLoader(files.map { it.toURI().toURL() }.toTypedArray(), parentClassLoader)
}

class CachingClassLoadersProvider(
    size: Int,
    private val parentClassLoader: ClassLoader = ClassLoader.getSystemClassLoader()
) : ClassLoadersProvider {

    private val logger = LoggerFactory.getLogger(ClassLoadersProvider::class.java)

    private val cache: ConcurrentMap<CacheKey, URLClassLoader> =
        CacheBuilder
            .newBuilder()
            .maximumSize(size.toLong())
            .removalListener<CacheKey, URLClassLoader> { (key, cl) ->
                logger.info("Removing classloader from cache: ${key.entries.map {it.path} }")
                cl.close()
            }
            .build<CacheKey, URLClassLoader>()
            .asMap()

    override fun getForClassPath(files: List<File>): ClassLoader {
        val key = makeKey(files)
        return cache.getOrPut(key) {
            makeClassLoader(key)
        }
    }

    private fun makeClassLoader(key: CacheKey): URLClassLoader {
        val cp = key.entries.map { it.path }
        logger.info("Creating new classloader for classpath: $cp")
        return URLClassLoader(cp.toTypedArray(), parentClassLoader)
    }

    private fun makeKey(files: List<File>): CacheKey {
        //probably should walk dirs content for actual last modified
        val entries = files.map { f -> ClEntry(f.toURI().toURL(), f.lastModified()) }
        return CacheKey(entries)
    }

    private data class ClEntry(val path: URL, val modificationTimestamp: Long)

    private data class CacheKey(val entries: List<ClEntry>)
}
