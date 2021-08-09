/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.compilerRunner

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logging
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.jetbrains.kotlin.daemon.common.COMPILE_DAEMON_DEFAULT_RUN_DIR_PATH
import org.jetbrains.kotlin.daemon.common.CompileService
import org.jetbrains.kotlin.daemon.common.DaemonOptions
import org.jetbrains.kotlin.daemon.common.walkDaemons
import org.jetbrains.kotlin.gradle.utils.appendLine
import java.io.File
import java.nio.file.Files
import java.text.CharacterIterator
import java.text.StringCharacterIterator

internal abstract class MultipleDaemonsDetectionService : BuildService<MultipleDaemonsDetectionService.Parameters>, AutoCloseable,
    OperationCompletionListener {
    internal interface Parameters : BuildServiceParameters {
        val rootBuildDir: DirectoryProperty
    }

    private class DaemonInfo(
        val displayName: String?,
        val usedMemory: Long?,
        val maxMemory: Long?,
        val rootBuildDir: File?
    ) {
        val Long.asMemoryString: String
            get() {
                val absB = if (this == Long.MIN_VALUE) Long.MAX_VALUE else Math.abs(this)
                if (absB < 1024) {
                    return "$this B"
                }
                var value = absB
                val ci: CharacterIterator = StringCharacterIterator("KMGTPE")
                var i = 40
                while (i >= 0 && absB > 0xfffccccccccccccL shr i) {
                    value = value shr 10
                    ci.next()
                    i -= 10
                }
                value *= java.lang.Long.signum(this).toLong()
                return java.lang.String.format("%.1f %ciB", value / 1024.0, ci.current())
            }

        override fun toString(): String {
            val memory = if (usedMemory != null && maxMemory != null) {
                " (${usedMemory.asMemoryString} / ${maxMemory.asMemoryString} memory)"
            } else {
                ""
            }
            return "${displayName ?: "Kotlin daemon"}$memory started for ${rootBuildDir ?: "unknown build"}"
        }
    }

    private fun <T> CompileService.CallResult<T>.getOrNull() = if (isGood) {
        get()
    } else {
        null
    }

    private val humanizedMemorySizeRegex = "(\\d+)([kmg]?)".toRegex()

    private fun String.memToBytes(): Long? =
        humanizedMemorySizeRegex
            .matchEntire(this.trim().toLowerCase())
            ?.groups?.let { match ->
                match[1]?.value?.let {
                    it.toLong() *
                            when (match[2]?.value) {
                                "k" -> 1 shl 10
                                "m" -> 1 shl 20
                                "g" -> 1 shl 30
                                else -> 1
                            }
                }
            }

    override fun close() {
        val registryDir = File(COMPILE_DAEMON_DEFAULT_RUN_DIR_PATH)
        val daemons = walkDaemons(
            registryDir,
            "[a-zA-Z0-9]*",
            Files.createTempFile(registryDir.toPath(), "kotlin-daemon-client-tsmarker", null).toFile()
        )
        val logger = Logging.getLogger(MultipleDaemonsDetectionService::class.java)
        val daemonsInfo = daemons
            .map { (daemon, _, _) ->
                val rootDir = try {
                    val daemonOptions = daemon.getDaemonOptions().get()
                    DaemonOptions::class.java.getDeclaredMethod("getRootBuildDir").invoke(daemonOptions) as File
                } catch (e: Exception) {
                    null
                }
                val daemonInfo = daemon.getDaemonInfo().getOrNull()
                val usedMemory = daemon.getUsedMemory().getOrNull()
                val maxMemory = daemon.getDaemonJVMOptions().getOrNull()?.maxMemory?.memToBytes()
                DaemonInfo(daemonInfo, usedMemory, maxMemory, rootDir)
            }
        logger.warn(buildString {
            appendLine()
            appendLine("*************************")
            appendLine("Kotlin daemons running:")
            daemonsInfo.forEach {
                appendLine(it)
            }
            appendLine("*************************")
        })
    }

    override fun onFinish(event: FinishEvent?) {
        // noop
    }
}