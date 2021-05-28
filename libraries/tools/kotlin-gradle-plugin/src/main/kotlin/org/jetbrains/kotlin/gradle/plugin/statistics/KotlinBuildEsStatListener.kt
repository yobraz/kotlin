/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.statistics

import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskSuccessResult
import org.jetbrains.kotlin.gradle.plugin.stat.CompileStatData
import org.jetbrains.kotlin.gradle.plugin.stat.ReportStatistics

class KotlinBuildEsStatListener : OperationCompletionListener, AutoCloseable {
    val reportStatistics: ReportStatistics = ReportStatisticsToElasticSearch

    override fun onFinish(event: FinishEvent?) {
        if (event is TaskFinishEvent) {
            val result = event.result
            val taskPath = event.descriptor.taskPath
            val taskResult = when (result) {
                is TaskSuccessResult -> if (result.isFromCache) "FROM CACHE" else if (result.isUpToDate) "UP TO DATE" else "SUCCESS"
                is TaskSkippedResult -> "SKIPPED"
                is TaskFailureResult -> "FAILED"
                else -> "UNKNOWN"
            }

//            BuildTime.children.forEach()

            val compileStatData = CompileStatData(
                duration = event.result.endTime - event.result.startTime, taskResult = taskResult,
                statData = emptyMap(), projectName = "kotlin", taskName = taskPath
            )
            reportStatistics.report(compileStatData)
        }
    }

    override fun close() {
    }
}