/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */
package org.jetbrains.kotlin.backend.konan

import org.jetbrains.kotlin.backend.common.ir.addChild
import org.jetbrains.kotlin.backend.konan.descriptors.synthesizedName
import org.jetbrains.kotlin.backend.konan.llvm.llvmSymbolOrigin
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrModuleFragmentImpl
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.random.Random

/**
 * Sometimes we need to reference symbols that are not declared in metadata.
 * For example, symbol might be declared during lowering.
 * In case of compiler caches, this means that it is not accessible as Lazy IR
 * and we have to explicitly add an external declaration.
 */
internal class InternalAbi(private val context: Context) {
    /**
     * Representation of ABI files from external modules.
     */
    private val externalAbiFiles = mutableMapOf<FqName, IrFile>()

    private val accumulator = mutableMapOf<IrFile, MutableList<IrFunction>>()

    private fun createAbiFile(module: IrModuleFragment, packageFqName: FqName): IrFile =
        module.addFile(NaiveSourceBasedFileEntryImpl("internal_for_${module.name}"), packageFqName)

    /**
     * Adds external [function] from [module] to a list of external references.
     */
    fun reference(function: IrFunction, packageFqName: FqName, module: ModuleDescriptor) {
        assert(function.isExternal) { "Function that represents external ABI should be marked as external" }
        context.llvmImports.add(module.llvmSymbolOrigin)
        externalAbiFiles.getOrPut(packageFqName) {
            createAbiFile(IrModuleFragmentImpl(module, context.irBuiltIns), packageFqName)
        }.addChild(function)
    }

    /**
     * Adds [function] to a list of [module]'s publicly available symbols.
     */
    fun declare(function: IrFunction, irFile: IrFile) {
        accumulator.getOrPut(irFile, ::mutableListOf) += function
    }

    fun commit(irFile: IrFile) {
        accumulator[irFile]?.let { functions ->
            irFile.addChildren(functions)
            accumulator.remove(irFile)
        }
    }

    companion object {
        /**
         * Allows to distinguish external declarations to internal ABI.
         */
        val INTERNAL_ABI_ORIGIN = object : IrDeclarationOriginImpl("INTERNAL_ABI") {}

        fun getCompanionObjectAccessorName(companion: IrClass): Name =
                getMangledNameFor("companionAccessor", companion.parentAsClass)

        fun getEnumValuesAccessorName(enum: IrClass): Name =
                getMangledNameFor("getValues", enum)

        /**
         * Generate name for declaration that will be a part of internal ABI.
         */
        private fun getMangledNameFor(declarationName: String, parent: IrDeclaration): Name {
            val prefix = parent.nameForIrSerialization
            return "$prefix.$declarationName".synthesizedName
        }
    }
}