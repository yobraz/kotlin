/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.internal.hash.FileHasher
import org.jetbrains.kotlin.gradle.internal.ProcessedFilesCache
import java.io.File

/**
 * Cache for storing already created [GradleNodeModule]s
 */
internal abstract class AbstractNodeModulesCache : AutoCloseable, BuildService<AbstractNodeModulesCache.Parameters> {
    internal interface Parameters : BuildServiceParameters {
        val rootProjectDir: DirectoryProperty
        val cacheDir: DirectoryProperty
    }

    companion object {
        const val STATE_FILE_NAME = ".visited"
    }

    lateinit var fileHasher: FileHasher

    private val cache by lazy {
        ProcessedFilesCache(
            fileHasher,
            parameters.rootProjectDir.get().asFile,
            parameters.cacheDir.get().asFile,
            STATE_FILE_NAME,
            "9"
        )
    }

    private val packageJsonCache = mutableMapOf<File, PackageJson>()

    @Synchronized
    fun get(
        name: String,
        version: String,
        file: File
    ): GradleNodeModule? = cache.getOrCompute(file) {
        buildImportedPackage(name, version, file)
    }?.let {
        val packageJson = it.second
        val dir = it.first
        if (packageJson != null) {
            packageJsonCache[dir] = packageJson
            GradleNodeModule(dir, packageJson)
        } else {
            val alternativePackageJson = packageJsonCache[dir]
                ?: dir.resolve("package.json").reader().use {
                    Gson().fromJson(it, PackageJson::class.java)
                }
            GradleNodeModule(dir, alternativePackageJson)
        }
    }

    abstract fun buildImportedPackage(
        name: String,
        version: String,
        file: File
    ): Pair<File, PackageJson>?

    @Synchronized
    override fun close() {
        cache.close()
    }
}

fun makeNodeModule(
    container: File,
    packageJson: PackageJson,
    srcPackageJson: File?,
    files: (File) -> Unit
): File {
    val dir = importedPackageDir(container, packageJson.name, packageJson.version)

    if (dir.exists()) dir.deleteRecursively()

    check(dir.mkdirs()) {
        "Cannot create directory: $dir"
    }

    val gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    files(dir)

    val jsonToWrite = srcPackageJson?.reader()?.use {
        val json = Gson().fromJson(it, JsonObject::class.java)
        json.addProperty(packageJson::name.name, packageJson.name)
        json.addProperty(packageJson::version.name, packageJson.version)
        json.addProperty(packageJson::main.name, packageJson.main)
        json
    } ?: packageJson

    val packageJsonFile = dir.resolve("package.json")
    packageJsonFile.writer().use {
        gson.toJson(jsonToWrite, it)
    }

    return dir
}

fun importedPackageDir(container: File, name: String, version: String): File =
    container.resolve(name).resolve(version)

fun GradleNodeModule(dir: File, packageJson: PackageJson) = GradleNodeModule(dir.parentFile.name, dir.name, dir, packageJson)