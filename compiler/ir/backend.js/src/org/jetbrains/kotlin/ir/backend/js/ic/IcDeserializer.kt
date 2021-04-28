/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.ic

import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir.JsGlobalDeclarationTable
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir.JsIrLinker
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.persistent.PersistentIrFactory
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.fileOrNull
import org.jetbrains.kotlin.library.impl.*
import kotlin.collections.ArrayDeque

class IcDeserializer(
    val linker: JsIrLinker,
    val context: JsIrBackendContext,
) {
    private val globalDeclarationTable = JsGlobalDeclarationTable(context.irBuiltIns)

    fun injectIcData(module: IrModuleFragment, icData: SerializedIcData) {
        // Prepare per-file indices

        val fileQueue = ArrayDeque<IcFileDeserializer>()
        val signatureQueue = ArrayDeque<IdSignature>()
        val symbolQueue = ArrayDeque<IrSymbol>()

        fun IdSignature.enqueue(icDeserializer: IcFileDeserializer, symbol: IrSymbol) {
            if (this !in icDeserializer.visited) {
                fileQueue.addLast(icDeserializer)
                signatureQueue.addLast(this)
                symbolQueue += symbol
                icDeserializer.visited += this
            }
        }

        // This is needed to link functional types' type parameters
        context.symbolTable.typeParameterSymbols().forEach {
            val typeParameter = it.owner

            val filePath = typeParameter.fileOrNull?.path ?: ""

            val idSig = IdSignature.FileLocalSignature(
                globalDeclarationTable.computeSignatureByDeclaration(typeParameter.parent as IrDeclaration),
                1000_000_000_000L + typeParameter.index,
                filePath
            )

            context.symbolTable.saveTypeParameterSignature(idSig, it)
        }

        // intrinsics from JsIntrinsics
        val existingPublicSymbols = mutableMapOf<IdSignature, IrSymbol>()
        (context.irFactory as PersistentIrFactory).symbolToSignatureMap.entries.forEach { (symbol, idSig) ->
            existingPublicSymbols[idSig] = symbol
        }

        val pathToIcFileData = icData.files.associateBy {
            it.file.path
        }

        val moduleDeserializer = linker.moduleDeserializer(module.descriptor)

        val fileDeserializers = moduleDeserializer.fileDeserializers()

        val pathToFileSymbol = fileDeserializers.map { it.file.path to it.file.symbol }.toMap()

        val publicSignatureToIcFileDeserializer = mutableMapOf<IdSignature, IcFileDeserializer>()

        val icDeserializers = fileDeserializers.mapNotNull { fd ->
            pathToIcFileData[fd.file.path]?.let { icFileData ->
                IcFileDeserializer(
                    linker, fd, icFileData,
                    pathToFileSymbol = { p -> pathToFileSymbol[p]!! },
                    context.mapping.state,
                    existingPublicSymbols,
                    moduleDeserializer,
                    publicSignatureToIcFileDeserializer,
                    { fileDeserializer, symbol -> enqueue(fileDeserializer, symbol) },
                )
            }
        }

        // Add all signatures withing the module to a queue ( declarations and bodies )
        println("==== Enqueue ====")
        for (icDeserializer in icDeserializers) {
            val currentFilePath = icDeserializer.fileDeserializer.file.path

            icDeserializer.fileDeserializer.symbolDeserializer.deserializedSymbols.entries.forEach { (idSig, symbol) ->
                if (idSig.isPublic) {
                    idSig.enqueue(icDeserializer, symbol)
                } else {
                    if (idSig is IdSignature.FileLocalSignature && idSig.filePath == currentFilePath ||
                        idSig is IdSignature.ScopeLocalDeclaration && idSig.filePath == currentFilePath
                    ) {
                        idSig.enqueue(icDeserializer, symbol)
                    }
                }
            }
        }

        val classToDeclarationSymbols = mutableMapOf<IrClass, List<IrSymbol>>()

        println("==== Queue ==== ")

        while (signatureQueue.isNotEmpty()) {
            val icFileDeserializer = fileQueue.removeFirst()
            val signature = signatureQueue.removeFirst()
            val symbol = symbolQueue.removeFirst()

            if (signature is IdSignature.FileSignature) continue

            // Deserialize the declaration
            val declaration = if (symbol.isBound) symbol.owner as IrDeclaration else icFileDeserializer.deserializeDeclaration(signature)

            icFileDeserializer.injectCarriers(declaration, signature)

            icFileDeserializer.mappingsDeserializer(signature, declaration)

            // Make sure all members are loaded
            if (declaration is IrClass) {
                icFileDeserializer.loadClassOrder(signature)?.let {
                    classToDeclarationSymbols[declaration] = it
                }
            }
        }

        println("==== Order ==== ")

        context.irFactory.stageController.withStage(1000) {

            for (icDeserializer in icDeserializers) {
                val fd = icDeserializer.fileDeserializer
                val icFileData = pathToIcFileData[fd.file.path] ?: continue
                val order = icFileData.order

                fd.file.declarations.clear()

                IrLongArrayMemoryReader(order.topLevelSignatures).array.forEach {
                    val symbolData = icDeserializer.symbolDeserializer.parseSymbolData(it)
                    val idSig = icDeserializer.symbolDeserializer.deserializeIdSignature(symbolData.signatureId)

                    // Don't create unbound symbols for top-level declarations we don't need.
                    if (idSig in icDeserializer.visited) {
                        val declaration = icDeserializer.deserializeIrSymbol(idSig, symbolData.kind).owner as IrDeclaration
                        fd.file.declarations += declaration
                    }
                }
            }

            for ((klass, declarations) in classToDeclarationSymbols.entries) {
                context.irFactory.stageController.unrestrictDeclarationListsAccess {
                    klass.declarations.clear()
                    for (ds in declarations) {
                        klass.declarations += ds.owner as IrDeclaration
                    }
                }
            }
        }
    }
}
