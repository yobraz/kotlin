/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.build.report.metrics

import java.util.*

class BuildMetricsReporterImpl : BuildMetricsReporter {
    private val myBuildMetricStartNs: EnumMap<BuildMetric, Long> =
        EnumMap(
            BuildMetric::class.java
        )
    private val myBuildTimes = BuildTimes()
    private val myBuildAttributes = BuildAttributes()

    override fun startMeasure(metric: BuildMetric, startNs: Long) {
        if (metric in myBuildMetricStartNs) {
            error("$metric was restarted before it finished")
        }
        myBuildMetricStartNs[metric] = startNs
    }

    override fun endMeasure(metric: BuildMetric, endNs: Long) {
        val startNs = myBuildMetricStartNs.remove(metric) ?: error("$metric finished before it started")
        val durationNs = endNs - startNs
        myBuildTimes.add(metric, durationNs)
    }

    override fun addMetric(metric: BuildMetric, value: Long) {
        myBuildTimes.add(metric, value)
    }

    override fun addAttribute(attribute: BuildAttribute) {
        myBuildAttributes.add(attribute)
    }

    override fun getMetrics(): BuildMetrics =
        BuildMetrics(
            buildTimes = myBuildTimes,
            buildAttributes = myBuildAttributes
        )

    override fun addMetrics(metrics: BuildMetrics?) {
        if (metrics == null) return

        myBuildAttributes.addAll(metrics.buildAttributes)
        myBuildTimes.addAll(metrics.buildTimes)
    }
}