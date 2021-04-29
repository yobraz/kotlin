/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.ic

import org.jetbrains.kotlin.backend.common.serialization.DeserializationStrategy
import org.jetbrains.kotlin.backend.common.serialization.IrFileDeserializer
import org.jetbrains.kotlin.backend.common.serialization.IrModuleDeserializer
import org.jetbrains.kotlin.backend.common.serialization.encodings.BinarySymbolData
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.ir.backend.js.JsMapping
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir.JsGlobalDeclarationTable
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir.JsIrLinker
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.persistent.PersistentIrFactory
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.fileOrNull
import org.jetbrains.kotlin.library.IrLibrary
import org.jetbrains.kotlin.library.impl.IrLongArrayMemoryReader

class IcModuleDeserializer(
    val irFactory: PersistentIrFactory,
    val mapping: JsMapping,
    val linker: JsIrLinker,
    val icData: SerializedIcData,
    moduleDescriptor: ModuleDescriptor,
    override val moduleFragment: IrModuleFragment,
) : IrModuleDeserializer(moduleDescriptor) {

    private val globalDeclarationTable = JsGlobalDeclarationTable(linker.builtIns)

    val fileQueue = ArrayDeque<IcFileDeserializer>()
    val signatureQueue = ArrayDeque<IdSignature>()
    val symbolQueue = ArrayDeque<IrSymbol>()

    val icDeserializers = mutableListOf<IcFileDeserializer>()
    val classToDeclarationSymbols = mutableMapOf<IrClass, List<IrSymbol>>()

    fun IdSignature.enqueue(icDeserializer: IcFileDeserializer, symbol: IrSymbol) {
        if (this !in icDeserializer.visited) {
            fileQueue.addLast(icDeserializer)
            signatureQueue.addLast(this)
            symbolQueue += symbol
            icDeserializer.visited += this
        }
    }

    override fun contains(idSig: IdSignature): Boolean {
        // New declaration | old declaration
        TODO("Not yet implemented")
    }

    override fun deserializeIrSymbol(idSig: IdSignature, symbolKind: BinarySymbolData.SymbolKind): IrSymbol {

        TODO("Not yet implemented")
    }

    override fun referenceSimpleFunctionByLocalSignature(file: IrFile, idSignature: IdSignature): IrSimpleFunctionSymbol {
        return super.referenceSimpleFunctionByLocalSignature(file, idSignature)
    }

    override fun referencePropertyByLocalSignature(file: IrFile, idSignature: IdSignature): IrPropertySymbol {
        return super.referencePropertyByLocalSignature(file, idSignature)
    }

    override fun declareIrSymbol(symbol: IrSymbol) {
        super.declareIrSymbol(symbol)
    }

    override fun init() {
        // This is needed to link functional types' type parameters
        linker.symbolTable.typeParameterSymbols().forEach {
            val typeParameter = it.owner

            val filePath = typeParameter.fileOrNull?.path ?: ""

            val idSig = IdSignature.FileLocalSignature(
                globalDeclarationTable.computeSignatureByDeclaration(typeParameter.parent as IrDeclaration),
                1000_000_000_000L + typeParameter.index,
                filePath
            )

            linker.symbolTable.saveTypeParameterSignature(idSig, it)
        }

        // intrinsics from JsIntrinsics
        val existingPublicSymbols = mutableMapOf<IdSignature, IrSymbol>()
        irFactory.symbolToSignatureMap.entries.forEach { (symbol, idSig) ->
            existingPublicSymbols[idSig] = symbol
        }

        val pathToIcFileData = icData.files.associateBy {
            it.file.path
        }

        val moduleDeserializer = linker.moduleDeserializer(moduleDescriptor)

        val fileDeserializers = moduleDeserializer.fileDeserializers()

        val pathToFileSymbol = fileDeserializers.map { it.file.path to it.file.symbol }.toMap()

        val publicSignatureToIcFileDeserializer = mutableMapOf<IdSignature, IcFileDeserializer>()

        icDeserializers += fileDeserializers.mapNotNull { fd ->
            pathToIcFileData[fd.file.path]?.let { icFileData ->
                IcFileDeserializer(
                    linker, fd, icFileData,
                    pathToFileSymbol = { p -> pathToFileSymbol[p]!! },
                    mapping.state,
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
    }

    fun deserializeAll() {
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
    }

    override fun postProcess() {
        irFactory.stageController.withStage(1000) {

            for (icDeserializer in icDeserializers) {
                val fd = icDeserializer.fileDeserializer
                val order = icDeserializer.icFileData.order

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
                irFactory.stageController.unrestrictDeclarationListsAccess {
                    klass.declarations.clear()
                    for (ds in declarations) {
                        klass.declarations += ds.owner as IrDeclaration
                    }
                }
            }
        }
    }

    override fun init(delegate: IrModuleDeserializer) {
        super.init(delegate)
    }

    override fun addModuleReachableTopLevel(idSig: IdSignature) {
        super.addModuleReachableTopLevel(idSig)
    }

    override val klib: IrLibrary
        get() = TODO("Not yet implemented")

    override val strategy: DeserializationStrategy
        get() = TODO("Not yet implemented")

    override val moduleDependencies: Collection<IrModuleDeserializer>
        get() = TODO("Not yet implemented")

    override val isCurrent: Boolean get() = false

    override fun fileDeserializers(): Collection<IrFileDeserializer> {
        return super.fileDeserializers()
    }


}