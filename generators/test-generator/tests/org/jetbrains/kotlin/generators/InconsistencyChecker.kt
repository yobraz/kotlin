/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.generators

import java.util.function.Consumer

interface InconsistencyChecker {
    fun add(affectedFile: String)

    val affectedFiles: List<String>

    companion object {
        private val DRY_RUN_ARG = "dryRun"

        fun inconsistencyChecker(dryRun: Boolean) = if (dryRun) DefaultInconsistencyChecker else EmptyInconsistencyChecker

        fun withAssertAllGenerated(args: Array<String>, f: Consumer<Boolean>) {
            val dryRun = args.any { it == DRY_RUN_ARG }
            f.accept(dryRun)
            if (dryRun) {
                assertAllGenerated()
            }
        }

        fun assertAllGenerated() {
            val affectedFiles = inconsistencyChecker(true).affectedFiles
            if (affectedFiles.isNotEmpty()) {
                throw IllegalStateException(
                    "Several test files should be regenerated!\n" +
                            affectedFiles.joinToString(separator = "\n", prefix = "        +:"))
            }
        }
    }
}

object DefaultInconsistencyChecker : InconsistencyChecker {
    private val files = mutableListOf<String>()

    override fun add(affectedFile: String) {
        files.add(affectedFile)
    }

    override val affectedFiles: List<String>
        get() = files
}

object EmptyInconsistencyChecker : InconsistencyChecker {
    override fun add(affectedFile: String) {
    }

    override val affectedFiles: List<String>
        get() = emptyList()
}
