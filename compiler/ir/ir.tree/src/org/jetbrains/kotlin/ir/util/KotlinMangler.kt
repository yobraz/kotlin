/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.util

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.types.KotlinType

interface KotlinMangler<D : Any> {

    val String.hashMangle: Long

    fun D.isExported(compatibleMode: Boolean): Boolean
//    fun D.isExported(): Boolean
    val D.mangleString: String
    val D.signatureString: String
    val D.fqnString: String

    val D.hashedMangle: Long get() = mangleString.hashMangle
    val D.signatureMangle: Long get() {
        val sss = signatureString
        return sss.hashMangle
    }
    val D.fqnMangle: Long get() = fqnString.hashMangle

    val manglerName: String

    interface DescriptorMangler : KotlinMangler<DeclarationDescriptor> {
        override val manglerName: String
            get() = "Descriptor"

//        fun ClassDescriptor.isExportEnumEntry(): Boolean
        fun ClassDescriptor.mangleEnumEntryString(): String

//        fun PropertyDescriptor.isExportField(): Boolean
        fun PropertyDescriptor.mangleFieldString(): String

        fun setupTypeApproximation(app: (KotlinType) -> (KotlinType))
    }

    interface IrMangler : KotlinMangler<IrDeclaration> {
        override val manglerName: String
            get() = "Ir"
    }
}
