/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.configuration.mpp

import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.kotlin.gradle.KotlinDependency

internal object ConflictingVersionKotlinDependenciesPreprocessor : KotlinDependenciesPreprocessor {
    override fun invoke(dependencies: Iterable<KotlinDependency>): List<KotlinDependency> {
        return dependencies
            .groupBy { dependency -> dependency.let { it.scope + it.group + it.name } }
            .mapValues { (_, differentVersionDependencies) ->
                differentVersionDependencies.maxWithOrNull { o1, o2 -> VersionComparatorUtil.compare(o1.version, o2.version) }
            }
            .values
            .filterNotNull()
    }

}