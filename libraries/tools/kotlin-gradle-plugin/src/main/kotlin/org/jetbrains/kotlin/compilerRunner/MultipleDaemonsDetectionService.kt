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
import org.jetbrains.kotlin.daemon.common.DaemonOptions
import org.jetbrains.kotlin.daemon.common.walkDaemons
import java.io.File
import java.nio.file.Files

internal abstract class MultipleDaemonsDetectionService : BuildService<MultipleDaemonsDetectionService.Parameters>, AutoCloseable,
    OperationCompletionListener {
    internal interface Parameters : BuildServiceParameters {
        val rootBuildDir: DirectoryProperty
    }

    override fun close() {
        val registryDir = File(COMPILE_DAEMON_DEFAULT_RUN_DIR_PATH)
        val daemons = walkDaemons(
            registryDir,
            "[a-zA-Z0-9]*",
            Files.createTempFile(registryDir.toPath(), "kotlin-daemon-client-tsmarker", null).toFile()
        )
        val logger = Logging.getLogger(MultipleDaemonsDetectionService::class.java)
        val (relatedDaemons, unknownDaemons) = daemons
            .map { (daemon, _, _) ->
                val rootDir = try {
                    val daemonOptions = daemon.getDaemonOptions().get()
                    DaemonOptions::class.java.getDeclaredMethod("getRootBuildDir").invoke(daemonOptions) as File
                } catch (e: Exception) {
                    null
                }
                Pair(daemon, rootDir)
            }
            .filter { (_, rootDir) ->
                rootDir == null || rootDir == parameters.rootBuildDir.get().asFile
            }
            .partition { (_, rootDir) ->
                rootDir != null
            }

        logger.warn("There're ${relatedDaemons.count()} related and ${unknownDaemons.count()} not directly related to the project Kotlin daemons found")
    }

    override fun onFinish(event: FinishEvent?) {
        // noop
    }
}