/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.ic

import org.jetbrains.kotlin.ir.backend.js.SerializedMappings
import org.jetbrains.kotlin.ir.serialization.SerializedCarriers
import org.jetbrains.kotlin.library.SerializedIrFile

class SerializedIcDataForFile(
    val file: SerializedIrFile,
    val carriers: SerializedCarriers,
    val mappings: SerializedMappings,
    val order: SerializedOrder,
)

class SerializedIcData(
    val files: Collection<SerializedIcDataForFile>,
)

class SerializedOrder(
    val topLevelSignatures: ByteArray,
    val containerSignatures: ByteArray,
    val declarationSignatures: ByteArray,
)