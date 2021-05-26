/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.mangle.descriptor

import org.jetbrains.kotlin.backend.common.serialization.mangle.KotlinExportChecker
import org.jetbrains.kotlin.backend.common.serialization.mangle.SpecialDeclarationType
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ScriptDescriptor
import org.jetbrains.kotlin.resolve.DescriptorUtils

abstract class DescriptorExportCheckerVisitor : KotlinExportChecker<DeclarationDescriptor> {
    override fun check(declaration: DeclarationDescriptor, type: SpecialDeclarationType): Boolean {
        return declaration !is ScriptDescriptor && !DescriptorUtils.isLocal(declaration)
    }

    override fun DeclarationDescriptor.isPlatformSpecificExported(): Boolean = error("Should not be called")
}