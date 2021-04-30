/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.ic

import org.jetbrains.kotlin.backend.common.serialization.DeserializationStrategy
import org.jetbrains.kotlin.backend.common.serialization.IrFileDeserializer
import org.jetbrains.kotlin.backend.common.serialization.IrModuleDeserializer
import org.jetbrains.kotlin.backend.common.serialization.encodings.BinarySymbolData
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
import org.jetbrains.kotlin.ir.util.isEffectivelyExternal
import org.jetbrains.kotlin.library.IrLibrary
import org.jetbrains.kotlin.library.impl.IrLongArrayMemoryReader

class IcModuleDeserializer(
    val irFactory: PersistentIrFactory,
    val mapping: JsMapping,
    val linker: JsIrLinker,
    val icData: SerializedIcData,
    val wrapped: IrModuleDeserializer,
) : IrModuleDeserializer(wrapped.moduleDescriptor) {

    private val globalDeclarationTable = JsGlobalDeclarationTable(linker.builtIns, throwOnClash = false)

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
        return wrapped.contains(idSig)
    }

    override fun deserializeIrSymbol(idSig: IdSignature, symbolKind: BinarySymbolData.SymbolKind): IrSymbol {
        return wrapped.deserializeIrSymbol(idSig, symbolKind)
    }

    override fun referenceSimpleFunctionByLocalSignature(file: IrFile, idSignature: IdSignature): IrSimpleFunctionSymbol {
        return wrapped.referenceSimpleFunctionByLocalSignature(file, idSignature)
    }

    override fun referencePropertyByLocalSignature(file: IrFile, idSignature: IdSignature): IrPropertySymbol {
        return wrapped.referencePropertyByLocalSignature(file, idSignature)
    }

    override fun declareIrSymbol(symbol: IrSymbol) {
        wrapped.declareIrSymbol(symbol)
    }

    override fun init() {
        wrapped.init(this)
    }

    private lateinit var actualDeserializer: IrModuleDeserializer

    override fun init(delegate: IrModuleDeserializer) {
        actualDeserializer = delegate
        wrapped.init(delegate)
    }

    override fun postProcess() {
        // This is needed to link functional types' type parameters
        (linker.symbolTable as IcSymbolTable).let { icSymbolTable ->
            icSymbolTable.typeParameterSymbols().forEach {
                val typeParameter = it.owner

                val filePath = typeParameter.fileOrNull?.path ?: ""

                val idSig = IdSignature.GlobalFileLocalSignature(
                    globalDeclarationTable.computeSignatureByDeclaration(typeParameter.parent as IrDeclaration),
                    1000_000_000_000L + typeParameter.index,
                    filePath
                )

                icSymbolTable.saveTypeParameterSignature(idSig, it)
            }
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
                    if (idSig is IdSignature.GlobalFileLocalSignature && idSig.filePath == currentFilePath ||
                        idSig is IdSignature.GlobalScopeLocalDeclaration && idSig.filePath == currentFilePath
                    ) {
                        idSig.enqueue(icDeserializer, symbol)
                    }
                }
            }
        }

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

        irFactory.stageController.withStage(1000) {

            for (icDeserializer in icDeserializers) {
                val fd = icDeserializer.fileDeserializer
                val order = icDeserializer.icFileData.order

                fd.file.declarations.retainAll { it.isEffectivelyExternal() }

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

    override fun addModuleReachableTopLevel(idSig: IdSignature) {
        wrapped.addModuleReachableTopLevel(idSig)
    }

    override val klib: IrLibrary
        get() = wrapped.klib

    override val strategy: DeserializationStrategy
        get() = wrapped.strategy

    override val moduleDependencies: Collection<IrModuleDeserializer>
        get() = wrapped.moduleDependencies

    override val isCurrent: Boolean
        get() = wrapped.isCurrent

    override fun fileDeserializers(): Collection<IrFileDeserializer> {
        return wrapped.fileDeserializers()
    }

    override val moduleFragment: IrModuleFragment
        get() = wrapped.moduleFragment
}