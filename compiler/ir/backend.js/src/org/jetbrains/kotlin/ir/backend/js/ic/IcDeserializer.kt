/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.ic

import org.jetbrains.kotlin.backend.common.lower.JsStatementOrigins
import org.jetbrains.kotlin.backend.common.overrides.DefaultFakeOverrideClassFilter
import org.jetbrains.kotlin.backend.common.serialization.*
import org.jetbrains.kotlin.backend.common.serialization.encodings.BinarySymbolData
import org.jetbrains.kotlin.backend.common.serialization.signature.IdSignatureSerializer
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.JsMappingState
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir.JsGlobalDeclarationTable
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir.JsIrLinker
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir.JsManglerIr
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.persistent.PersistentIrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.persistent.PersistentIrFactory
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrStatementOriginImpl
import org.jetbrains.kotlin.ir.serialization.CarrierDeserializer
import org.jetbrains.kotlin.ir.symbols.IrFileSymbol
import org.jetbrains.kotlin.ir.symbols.IrReturnableBlockSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.fileOrNull
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.library.SerializedIrFile
import org.jetbrains.kotlin.library.impl.*
import org.jetbrains.kotlin.protobuf.ExtensionRegistryLite
import kotlin.collections.ArrayDeque
import kotlin.collections.HashSet

import org.jetbrains.kotlin.backend.common.serialization.proto.IrFile as ProtoFile
import org.jetbrains.kotlin.backend.common.serialization.proto.IrDeclaration as ProtoIrDeclaration

class IcDeserializer(
    val linker: JsIrLinker,
    val context: JsIrBackendContext,
) {
    private val signaturer = IdSignatureSerializer(JsManglerIr)
    private val globalDeclarationTable = JsGlobalDeclarationTable(signaturer, context.irBuiltIns)

    fun injectIcData(module: IrModuleFragment, icData: SerializedIcData) {
        // Prepare per-file indices

        val moduleDeserializer = linker.moduleDeserializer(module.descriptor)

        val fileDeserializers = moduleDeserializer.fileDeserializers()

        val fileQueue = ArrayDeque<IcFileDeserializer>()
        val signatureQueue = ArrayDeque<IdSignature>()
        val kindQueue = ArrayDeque<BinarySymbolData.SymbolKind>()

        fun IdSignature.enqueue(icDeserializer: IcFileDeserializer, kind: BinarySymbolData.SymbolKind) {
            if (this !in icDeserializer.visited) {
                fileQueue.addLast(icDeserializer)
                signatureQueue.addLast(this)
                kindQueue.addLast(kind)
                icDeserializer.visited += this
            }
        }

        val pathToIcFileData = icData.files.associateBy {
            it.file.path
        }

        val pathToFileSymbol = mutableMapOf<String, IrFileSymbol>()
        for (fd in fileDeserializers) {
            pathToFileSymbol[fd.file.path] = fd.file.symbol
        }

        val existingPublicSymbols = mutableMapOf<IdSignature, IrSymbol>()

        context.irBuiltIns.packageFragment.declarations.forEach {
            existingPublicSymbols[it.symbol.signature!!] = it.symbol
        }

//        context.intrinsics.externalPackageFragment.declarations.forEach {
////            it.traverse {
//                val signature = it.symbol.signature ?: globalDeclarationTable.computeSignatureByDeclaration(it)
//                existingPublicSymbols[signature] = it.symbol
////            }
//        }

        context.symbolTable.typeParameterSymbols().forEach {
            val typeParameter = it.owner

            val filePath = typeParameter.fileOrNull?.path ?: ""

            val idSig = IdSignature.FileLocalSignature(globalDeclarationTable.computeSignatureByDeclaration(typeParameter.parent as IrDeclaration), 1000_000_000_000L + typeParameter.index, filePath)

            context.symbolTable.saveTypeParameterSignature(idSig, it)

            existingPublicSymbols[idSig] = it
        }

        (context.irFactory as PersistentIrFactory).symbolToSignatureMap.entries.forEach { (symbol, idSig) ->
            existingPublicSymbols[idSig] = symbol
        }

        fileDeserializers.forEach { fd ->
            fd.symbolDeserializer.deserializedSymbols.entries.forEach { (idSig, symbol) ->
                if (idSig.isPublic) {
                    existingPublicSymbols[idSig] = symbol
                }
            }
        }

        val publicSignatureToIcFileDeserializer = mutableMapOf<IdSignature, IcFileDeserializer>()

        val fdToIcFd = mutableMapOf<IrFileDeserializer, IcFileDeserializer>()

        // Add all signatures withing the module to a queue ( declarations and bodies )
        // TODO add bodies
        println("==== Init ====")
        for (fd in fileDeserializers) {
            val icFileData = pathToIcFileData[fd.file.path] ?: continue

            lateinit var icDeserializer: IcFileDeserializer

            icDeserializer = IcFileDeserializer(
                linker, fd, icFileData,
                { idSig, kind ->
                    val deser = publicSignatureToIcFileDeserializer[idSig] ?: this
                    idSig.enqueue(deser, kind)
                },
                pathToFileSymbol = { p -> pathToFileSymbol[p]!! },
                context.mapping.state,
            ) { idSig, kind ->
                existingPublicSymbols[idSig] ?: icDeserializer.privateSymbols[idSig] ?: if (moduleDeserializer.contains(idSig)) moduleDeserializer.deserializeIrSymbol(idSig, kind) else null ?: run {
                    if (idSig.isPublic || idSig is IdSignature.FileLocalSignature) {
                        val fileDeserializer = publicSignatureToIcFileDeserializer[idSig.topLevelSignature()]
                            ?: fdToIcFd[fileDeserializers.first { idSig.topLevelSignature() in it.reversedSignatureIndex}]
                            ?: error("file deserializer not found: $idSig")
                        idSig.enqueue(fileDeserializer, kind)
                        fileDeserializer.deserializeIrSymbol(idSig, kind).also {
                            existingPublicSymbols[idSig] = it
                        }
                    } else {
                        idSig.enqueue(icDeserializer, kind)
                        icDeserializer.deserializeIrSymbol(idSig, kind).also {
                            icDeserializer.privateSymbols[idSig] = it
                        }
                    }
                }
            }

            fdToIcFd[fd] = icDeserializer

            val currentFilePath = fd.file.path

            fd.symbolDeserializer.deserializedSymbols.entries.forEach { (idSig, symbol) ->
                if (idSig.isPublic) {
                    idSig.enqueue(icDeserializer, IrFileSerializer.protoSymbolKind(symbol))
                    publicSignatureToIcFileDeserializer[idSig] = icDeserializer
                } else {
                    icDeserializer.privateSymbols[idSig] = symbol
                    if (idSig is IdSignature.FileLocalSignature && idSig.filePath == currentFilePath ||
                        idSig is IdSignature.ScopeLocalDeclaration && idSig.filePath == currentFilePath
                    ) {
                        publicSignatureToIcFileDeserializer[idSig] = icDeserializer
                        idSig.enqueue(icDeserializer, IrFileSerializer.protoSymbolKind(symbol))
                    }
                }
            }

            icDeserializer.reversedSignatureIndex.keys.forEach {
                if (it.isPublic || it is IdSignature.FileLocalSignature) {
                    publicSignatureToIcFileDeserializer[it] = icDeserializer
                }
            }
        }

        val classToDeclarationSymbols = mutableMapOf<IrClass, List<IrSymbol>>()

        println("==== Queue ==== ")

        while (signatureQueue.isNotEmpty()) {
            val icFileDeserializer = fileQueue.removeFirst()
            val signature = signatureQueue.removeFirst()
            val kind = kindQueue.removeFirst()

            if (signature is IdSignature.FileSignature) continue

            if ("$signature" == "private kotlin/Function1|null[0]:1000000000000 from ") {
                1
            }

            // Deserialize the declaration
            val symbol = existingPublicSymbols[signature] ?: icFileDeserializer.privateSymbols[signature]
            val declaration = if (symbol != null && symbol.isBound) symbol.owner as IrDeclaration else icFileDeserializer.deserializeDeclaration(signature)

            if (declaration == null) {
                if (/*kind != BinarySymbolData.SymbolKind.VARIABLE_SYMBOL && */kind == BinarySymbolData.SymbolKind.TYPE_PARAMETER_SYMBOL) {
                    println("skipped $signature [$kind] (${icFileDeserializer.fileDeserializer.file.name});")
                }
                continue
            }

            icFileDeserializer.signatureToDeclaration[signature] = declaration

            icFileDeserializer.injectCarriers(declaration, signature)

            icFileDeserializer.mappingsDeserializer(signature, declaration)

            // Make sure all members are loaded
            if (declaration is IrClass) {
                icFileDeserializer.loadClassOrder(signature)?.let {
                    classToDeclarationSymbols[declaration] = it
                }
            }
        }


        for (fd in fdToIcFd.values) {
            for (d in fd.visited) {
                if (d is PersistentIrDeclarationBase<*> && d.values == null) {
                    error("Declaration ${d.render()} didn't get injected with the carriers")
                }
            }
        }


        println("==== Order ==== ")

        context.irFactory.stageController.withStage(1000) {

            for (fd in fileDeserializers) {
                val icFileData = pathToIcFileData[fd.file.path] ?: continue
                val order = icFileData.order
                val icDeserializer = fdToIcFd[fd]!!

                fd.file.declarations.clear()

                IrLongArrayMemoryReader(order.topLevelSignatures).array.forEach {
                    val symbolData = icDeserializer.symbolDeserializer.parseSymbolData(it)
                    val idSig = icDeserializer.symbolDeserializer.deserializeIdSignature(symbolData.signatureId)

                    if (idSig in icDeserializer.visited) {
                        val declaration = icDeserializer.signatureToDeclaration[idSig]!!
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

    class IcFileDeserializer(
        val linker: JsIrLinker,
        val fileDeserializer: IrFileDeserializer,
        val icFileData: SerializedIcDataForFile,
        val enqueueLocalTopLevelDeclaration: IcFileDeserializer.(IdSignature, BinarySymbolData.SymbolKind) -> Unit,
        val pathToFileSymbol: (String) -> IrFileSymbol,
        val mappingState: JsMappingState,
        val deserializePublicSymbol: (IdSignature, BinarySymbolData.SymbolKind) -> IrSymbol,
    ) {

        val privateSymbols = mutableMapOf<IdSignature, IrSymbol>()

        private val fileReader = FileReaderFromSerializedIrFile(icFileData.file)

        private fun cntToReturnableBlockSymbol(upCnt: Int): IrReturnableBlockSymbol {
            return declarationDeserializer.bodyDeserializer.cntToReturnableBlockSymbol(upCnt)
        }

        val signatureToDeclaration = mutableMapOf<IdSignature, IrDeclaration>()

        val symbolDeserializer = IrSymbolDeserializer(
            linker.symbolTable,
            fileReader,
            fileDeserializer.file.path,
            emptyList(),
            { idSig, kind -> enqueueLocalTopLevelDeclaration(idSig, kind) },
            { _, s -> s },
            pathToFileSymbol,
            ::cntToReturnableBlockSymbol,
            enqueueAllDeclarations = true,
            deserializePublicSymbol,
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
            fileDeserializer.declarationDeserializer.allowErrorNodes,
            deserializeInlineFunctions = true,
            deserializeBodies = true,
            symbolDeserializer,
            DefaultFakeOverrideClassFilter,
            linker.fakeOverrideBuilder,
            skipMutableState = true,
            additionalStatementOriginIndex = additionalStatementOriginIndex,
        )

        private val protoFile: ProtoFile = ProtoFile.parseFrom(icFileData.file.fileData.codedInputStream, ExtensionRegistryLite.newInstance())

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


        fun deserializeDeclaration(idSig: IdSignature): IrDeclaration? {
            val idSigIndex = reversedSignatureIndex[idSig] ?: return null
//                error("Not found Idx for $idSig")
            val declarationStream = fileReader.irDeclaration(idSigIndex).codedInputStream
            val declarationProto = ProtoIrDeclaration.parseFrom(declarationStream, ExtensionRegistryLite.newInstance())
            return declarationDeserializer.deserializeDeclaration(declarationProto)
        }

        fun deserializeIrSymbol(code: Long): IrSymbol {
            return symbolDeserializer.deserializeIrSymbol(code)
        }

        fun deserializeIrSymbol(idSig: IdSignature, symbolKind: BinarySymbolData.SymbolKind): IrSymbol {
            enqueueLocalTopLevelDeclaration(idSig, symbolKind)
            return symbolDeserializer.deserializeIrSymbol(idSig, symbolKind)
        }

        fun deserializeIdSignature(index: Int): IdSignature {
            return symbolDeserializer.deserializeIdSignature(index)
        }

        fun injectCarriers(declaration: IrDeclaration, signature: IdSignature) {
            carrierDeserializer.injectCarriers(declaration, signature)
        }
    }

    companion object {
        private val additionalStatementOrigins = JsStatementOrigins::class.nestedClasses.toList()
        private val additionalStatementOriginIndex =
            additionalStatementOrigins.mapNotNull { it.objectInstance as? IrStatementOriginImpl }.associateBy { it.debugName }
    }
}

class FileReaderFromSerializedIrFile(val irFile: SerializedIrFile) : IrLibraryFile() {
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

private fun IrDeclaration.traverse(fn: (IrDeclaration) -> Unit) {
    this.acceptVoid(object : IrElementVisitorVoid {
        override fun visitElement(element: IrElement) {
            element.acceptChildrenVoid(this)
        }

        override fun visitDeclaration(declaration: IrDeclarationBase) {
            fn(declaration)
            super.visitDeclaration(declaration)
        }
    })
}