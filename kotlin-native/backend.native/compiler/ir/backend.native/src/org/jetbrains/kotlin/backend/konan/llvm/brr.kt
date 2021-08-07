/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.llvm

import kotlinx.cinterop.*
import llvm.*
import org.jetbrains.kotlin.backend.konan.BitcodeFile
import org.jetbrains.kotlin.backend.konan.Context
import org.jetbrains.kotlin.backend.konan.ObjectFile
import org.jetbrains.kotlin.backend.konan.logMultiple
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.isEffectivelyExternal
import org.jetbrains.kotlin.konan.file.File

internal val moduleToLlvm = mutableMapOf<LLVMModuleRef, Llvm>()
internal val moduleToDebugInfo = mutableMapOf<LLVMModuleRef, DebugInfo>()
internal val moduleToLlvmDeclarations = mutableMapOf<LLVMModuleRef, LlvmDeclarations>()

internal val irFileToModule = mutableMapOf<IrFile, LLVMModuleRef>()
internal val irFileToCodegenVisitor = mutableMapOf<IrFile, CodeGeneratorVisitor>()

internal fun programBitcode(): List<LLVMModuleRef> = irFileToModule.values.toList()

// TODO: Class, that accepts rule.
sealed class Spec {
    abstract fun containsDeclaration(declaration: IrDeclaration): Boolean
}

internal class RootSpec(private val moduleFiles: List<IrFile>) : Spec() {
    override fun containsDeclaration(declaration: IrDeclaration): Boolean {
        return declaration.file !in moduleFiles
    }
}

internal class FileLlvmModuleSpecification(
        val irFile: IrFile,
) : Spec() {
    override fun containsDeclaration(declaration: IrDeclaration): Boolean {
        if (declaration.isEffectivelyExternal()) return false
        return declaration.file == irFile
    }
}

// TODO: Module name could be an absolute path, thus some logic may fail
fun stableModuleName(llvmModule: LLVMModuleRef): String = memScoped {
    val sizeVar = alloc<size_tVar>()
    LLVMGetModuleIdentifier(llvmModule, sizeVar.ptr)?.toKStringFromUtf8()!!.replace('/', '_')
}

internal class SeparateCompilation(val context: Context) {

    fun classifyModules(modules: List<LLVMModuleRef>): Pair<List<LLVMModuleRef>, List<ObjectFile>> {
        val (reuse, compile) = modules.partition { didNotChange(it) && getObjectFileFor(it) != null }
        context.logMultiple {
            +"Compiling LLVM modules:"
            (compile).forEach {
                +stableModuleName(it)
            }
        }
        context.logMultiple {
            +"Reusing LLVM modules:"
            (reuse).forEach {
                +stableModuleName(it)
            }
        }
        return Pair(compile.onEach { storeHash(it) }, reuse.map { getObjectFileFor(it)!!.absolutePath })
    }

    fun classifyBitcode(files: List<BitcodeFile>): Pair<List<BitcodeFile>, List<ObjectFile>> {
        val fileToModule = files.map { it to parseBitcodeFile(it) }

        val (reuse, compile) = fileToModule.partition { didNotChange(it.second) && getObjectFileFor(it.second) != null }

        val toCompile = compile.onEach { storeHash(it.second) }.map { it.first }
        val toLink = reuse.map { getObjectFileFor(it.second)!!.absolutePath }

        fileToModule.forEach { LLVMDisposeModule(it.second) }
        return Pair(toCompile, toLink)
    }

    private fun getExistingHash(module: LLVMModuleRef): ByteArray? {
        val moduleName = stableModuleName(module)
        val file = context.config.tempFiles.lookup("$moduleName.md5")
                ?: return null
        return file.readBytes()
    }

    private fun computeHash(llvmModule: LLVMModuleRef): ByteArray {
        return memScoped {
            val hash = allocArray<uint32_tVar>(5)
            LLVMKotlinModuleHash(llvmModule, hash)
            hash.readBytes(5 * 4)
        }
    }

    private fun storeHash(module: LLVMModuleRef) {
        val moduleName = stableModuleName(module)
        val hash = computeHash(module)
        val file = context.config.tempFiles.create(moduleName, ".md5")
        file.writeBytes(hash)
    }

    private fun didNotChange(llvmModule: LLVMModuleRef): Boolean {
        val oldHash = getExistingHash(llvmModule)
                ?: return true
        val newHash = computeHash(llvmModule)
        return oldHash.contentEquals(newHash)
    }

    private fun getObjectFileFor(llvmModule: LLVMModuleRef): File? {
        val moduleName = stableModuleName(llvmModule)
        return context.config.tempFiles.lookup("$moduleName.bc.o")
    }
}