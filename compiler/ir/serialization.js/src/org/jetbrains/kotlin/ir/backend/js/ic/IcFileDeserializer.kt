/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.ic

import org.jetbrains.kotlin.ir.backend.js.JsStatementOrigins
import org.jetbrains.kotlin.backend.common.overrides.DefaultFakeOverrideClassFilter
import org.jetbrains.kotlin.backend.common.serialization.*
import org.jetbrains.kotlin.backend.common.serialization.encodings.BinarySymbolData
import org.jetbrains.kotlin.backend.common.serialization.proto.IrFile
import org.jetbrains.kotlin.ir.backend.js.JsMappingState
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir.JsIrLinker
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.path
import org.jetbrains.kotlin.ir.expressions.IrStatementOriginImpl
import org.jetbrains.kotlin.ir.serialization.CarrierDeserializer
import org.jetbrains.kotlin.ir.symbols.IrFileSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.library.SerializedIrFile
import org.jetbrains.kotlin.library.impl.DeclarationId
import org.jetbrains.kotlin.library.impl.DeclarationIrTableMemoryReader
import org.jetbrains.kotlin.library.impl.IrArrayMemoryReader
import org.jetbrains.kotlin.library.impl.IrLongArrayMemoryReader
import org.jetbrains.kotlin.protobuf.ExtensionRegistryLite

class IcFileDeserializer(
    val linker: JsIrLinker,
    val fileDeserializer: IrFileDeserializer,
    val icFileData: SerializedIcDataForFile,
    val pathToFileSymbol: (String) -> IrFileSymbol,
    val mappingState: JsMappingState,
    val existingPublicSymbols: MutableMap<IdSignature, IrSymbol>,
    val moduleDeserializer: IrModuleDeserializer,
    val publicSignatureToIcFileDeserializer: MutableMap<IdSignature, IcFileDeserializer>,
    val enqueue: IdSignature.(IcFileDeserializer, IrSymbol) -> Unit,
) {

    private val fileReader = FileReaderFromSerializedIrFile(icFileData.file)

    val symbolDeserializer = IrSymbolDeserializer(
        linker.symbolTable,
        fileReader,
        fileDeserializer.file.path,
        emptyList(),
        { idSig, symbol -> enqueueLocalTopLevelDeclaration(idSig, symbol) },
        { _, s -> s },
        pathToFileSymbol,
        enqueueAllDeclarations = true,
        useGlobalSignatures = true,
        ::deserializePublicSymbol,
    ).also {
        for ((idSig, symbol) in fileDeserializer.symbolDeserializer.deserializedSymbols.entries) {
            it.deserializedSymbols[idSig] = symbol
        }
    }

    private val declarationDeserializer = IrDeclarationDeserializer(
        linker.builtIns,
        linker.symbolTable,
        linker.symbolTable.irFactory,
        fileReader,
        fileDeserializer.file,
        allowErrorNodes = true,
        deserializeInlineFunctions = true,
        deserializeBodies = true,
        symbolDeserializer,
        DefaultFakeOverrideClassFilter,
        linker.fakeOverrideBuilder,
        skipMutableState = true,
        additionalStatementOriginIndex = additionalStatementOriginIndex,
        allowErrorStatementOrigins = true,
    )

    private val protoFile: IrFile = IrFile.parseFrom(icFileData.file.fileData.codedInputStream, ExtensionRegistryLite.newInstance())

    private val carrierDeserializer = CarrierDeserializer(declarationDeserializer, icFileData.carriers)

    val reversedSignatureIndex: Map<IdSignature, Int> = protoFile.declarationIdList.map { symbolDeserializer.deserializeIdSignature(it) to it }.toMap()

    val visited = HashSet<IdSignature>()

    val mappingsDeserializer = mappingState.mappingsDeserializer(icFileData.mappings, { code ->
        val symbolData = symbolDeserializer.parseSymbolData(code)
        symbolDeserializer.deserializeIdSignature(symbolData.signatureId)
    }) {
        deserializeIrSymbol(it)
    }

    val topLevelSignatures = IrLongArrayMemoryReader(icFileData.order.topLevelSignatures).array.map {
        val symbolData = symbolDeserializer.parseSymbolData(it)
        symbolDeserializer.deserializeIdSignature(symbolData.signatureId)
    }

    init {
        fileDeserializer.reversedSignatureIndex.keys.forEach {
            publicSignatureToIcFileDeserializer[it] = this
        }

        reversedSignatureIndex.keys.forEach {
            publicSignatureToIcFileDeserializer[it] = this
        }
    }

    private val containerSigToOrder = mutableMapOf<IdSignature, ByteArray>().also { map ->
        val containerIds = IrLongArrayMemoryReader(icFileData.order.containerSignatures).array
        val declarationIds = IrArrayMemoryReader(icFileData.order.declarationSignatures)

        containerIds.forEachIndexed { index, id ->
            val symbolData = symbolDeserializer.parseSymbolData(id)
            val containerSig = symbolDeserializer.deserializeIdSignature(symbolData.signatureId)

            map[containerSig] = declarationIds.tableItemBytes(index)
        }
    }

    fun loadClassOrder(classSignature: IdSignature): List<IrSymbol>? {
        val bytes = containerSigToOrder[classSignature] ?: return null

        return IrLongArrayMemoryReader(bytes).array.map(::deserializeIrSymbol)
    }


    private fun deserializePublicSymbol(idSig: IdSignature, kind: BinarySymbolData.SymbolKind) : IrSymbol {
        return existingPublicSymbols[idSig] ?: if (moduleDeserializer.contains(idSig)) moduleDeserializer.deserializeIrSymbol(idSig, kind) else null ?: run {
            val fileDeserializer = publicSignatureToIcFileDeserializer[idSig.topLevelSignature()] ?: error("file deserializer not found: $idSig")
            fileDeserializer.deserializeIrSymbol(idSig, kind)
        }
    }

    private fun enqueueLocalTopLevelDeclaration(idSig: IdSignature, symbol: IrSymbol) {
        // We only care about declarations from IC cache. They all are in the map.
        val deser = publicSignatureToIcFileDeserializer[idSig] ?: return
        idSig.enqueue(deser, symbol)
    }

    fun deserializeDeclaration(idSig: IdSignature): IrDeclaration {
        val idSigIndex = reversedSignatureIndex[idSig] ?: error("Not found Idx for $idSig")
        val declarationStream = fileReader.irDeclaration(idSigIndex).codedInputStream
        val declarationProto = org.jetbrains.kotlin.backend.common.serialization.proto.IrDeclaration.parseFrom(declarationStream, ExtensionRegistryLite.newInstance())
        return declarationDeserializer.deserializeDeclaration(declarationProto)
    }

    fun deserializeIrSymbol(code: Long): IrSymbol {
        return symbolDeserializer.deserializeIrSymbol(code)
    }

    fun deserializeIrSymbol(idSig: IdSignature, symbolKind: BinarySymbolData.SymbolKind): IrSymbol {
        return symbolDeserializer.deserializeIrSymbol(idSig, symbolKind).also { symbol ->
            idSig.enqueue(this, symbol)
        }
    }

    fun injectCarriers(declaration: IrDeclaration, signature: IdSignature) {
        carrierDeserializer.injectCarriers(declaration, signature)
    }

    companion object {
        private val additionalStatementOrigins = JsStatementOrigins::class.nestedClasses.toList()
        private val additionalStatementOriginIndex =
            additionalStatementOrigins.mapNotNull { it.objectInstance as? IrStatementOriginImpl }.associateBy { it.debugName }
    }
}

private class FileReaderFromSerializedIrFile(val irFile: SerializedIrFile) : IrLibraryFile() {
    private val declarationReader = DeclarationIrTableMemoryReader(irFile.declarations)
    private val typeReader = IrArrayMemoryReader(irFile.types)
    private val signatureReader = IrArrayMemoryReader(irFile.signatures)
    private val stringReader = IrArrayMemoryReader(irFile.strings)
    private val bodyReader = IrArrayMemoryReader(irFile.bodies)

    override fun irDeclaration(index: Int): ByteArray = declarationReader.tableItemBytes(DeclarationId(index))

    override fun type(index: Int): ByteArray = typeReader.tableItemBytes(index)

    override fun signature(index: Int): ByteArray = signatureReader.tableItemBytes(index)

    override fun string(index: Int): ByteArray = stringReader.tableItemBytes(index)

    override fun body(index: Int): ByteArray = bodyReader.tableItemBytes(index)
}