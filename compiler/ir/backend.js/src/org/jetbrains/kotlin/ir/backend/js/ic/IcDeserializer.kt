/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.ic

import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir.JsIrLinker
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.persistent.PersistentIrFactory

class IcDeserializer(
    val linker: JsIrLinker,
    val context: JsIrBackendContext,
) {
    fun injectIcData(module: IrModuleFragment, icData: SerializedIcData) {
        val icModuleDeserializer = IcModuleDeserializer(
            context.irBuiltIns,
            context.symbolTable,
            context.irFactory as PersistentIrFactory,
            context.mapping,
            linker,
            icData,
            module.descriptor,
            module
        )
        icModuleDeserializer.init()
        icModuleDeserializer.deserializeAll()
        icModuleDeserializer.postProcess()
    }
}
