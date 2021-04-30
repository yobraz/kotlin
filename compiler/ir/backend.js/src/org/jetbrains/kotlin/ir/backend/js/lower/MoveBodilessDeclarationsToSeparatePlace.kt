/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower

import org.jetbrains.kotlin.backend.common.DeclarationTransformer
import org.jetbrains.kotlin.backend.common.ir.addChild
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.utils.getJsModule
import org.jetbrains.kotlin.ir.backend.js.utils.getJsQualifier
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrExternalPackageFragmentImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrFileImpl
import org.jetbrains.kotlin.ir.symbols.IrFileSymbol
import org.jetbrains.kotlin.ir.symbols.impl.DescriptorlessExternalPackageFragmentSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrFileSymbolImpl
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.isEffectivelyExternal
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.util.transformFlat
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.FqName

private val BODILESS_BUILTIN_CLASSES = listOf(
    "kotlin.String",
    "kotlin.Nothing",
    "kotlin.Array",
    "kotlin.Any",
    "kotlin.ByteArray",
    "kotlin.CharArray",
    "kotlin.ShortArray",
    "kotlin.IntArray",
    "kotlin.LongArray",
    "kotlin.FloatArray",
    "kotlin.DoubleArray",
    "kotlin.BooleanArray",
    "kotlin.Boolean",
    "kotlin.Byte",
    "kotlin.Short",
    "kotlin.Int",
    "kotlin.Float",
    "kotlin.Double",
    "kotlin.Function"
).map { FqName(it) }.toSet()

private fun isBuiltInClass(declaration: IrDeclaration): Boolean =
    declaration is IrClass && declaration.fqNameWhenAvailable in BODILESS_BUILTIN_CLASSES

fun moveBodilessDeclarationsToSeparatePlace(context: JsIrBackendContext, moduleFragment: IrModuleFragment) {
    moveBodilessDeclarationsToSeparatePlace(BodilessDeclarationMoverToContext(context), moduleFragment)
}

fun moveBodilessDeclarationsToSeparatePlaceDelayed(moduleFragment: IrModuleFragment): DelayedBodilessDeclarationMover {
    val mover = DelayedBodilessDeclarationMover()
    moveBodilessDeclarationsToSeparatePlace(mover, moduleFragment)
    return mover
}

private fun moveBodilessDeclarationsToSeparatePlace(mover: BodilessDeclarationMover, moduleFragment: IrModuleFragment) {
    MoveBodilessDeclarationsToSeparatePlaceLowering(mover).let { moveBodiless ->
        moduleFragment.files.forEach {
            validateIsExternal(it)
            it.declarations.transformFlat { moveBodiless.transformFlat(it) }
//            moveBodiless.lower(it)
        }
    }
}

class MoveBodilessDeclarationsToSeparatePlaceLowering(private val mover: BodilessDeclarationMover) : DeclarationTransformer {

    override fun transformFlat(declaration: IrDeclaration): List<IrDeclaration>? {
        val irFile = declaration.parent as? IrFile ?: return null

        val externalPackageFragment by lazy {
            mover.externalPackageFragment(irFile)
        }

        if (irFile.getJsModule() != null || irFile.getJsQualifier() != null) {
            externalPackageFragment.declarations += declaration
            declaration.parent = externalPackageFragment

            mover.addPackageLevelJsModule(externalPackageFragment)

            declaration.collectAllExternalDeclarations()

            return emptyList()
        } else {
            val d = declaration as? IrDeclarationWithName ?: return null

            if (isBuiltInClass(d)) {
                mover.addBodilessBuiltinDeclaration(d)
                d.collectAllExternalDeclarations()

                return emptyList()
            } else if (d.isEffectivelyExternal()) {
                if (d.getJsModule() != null) {
                    mover.addDeclarationLevelJsModule(d)
                }

                externalPackageFragment.declarations += d
                d.parent = externalPackageFragment

                d.collectAllExternalDeclarations()

                return emptyList()
            }

            return null
        }
    }

    private fun IrDeclaration.collectAllExternalDeclarations() {
        this.accept(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitDeclaration(declaration: IrDeclarationBase) {
                mover.addExternalDeclaration(declaration)
                super.visitDeclaration(declaration)
            }
        }, null)
    }
}

fun validateIsExternal(packageFragment: IrPackageFragment) {
    for (declaration in packageFragment.declarations) {
        validateNestedExternalDeclarations(declaration, (declaration as? IrPossiblyExternalDeclaration)?.isExternal ?: false)
    }
}


fun validateNestedExternalDeclarations(declaration: IrDeclaration, isExternalTopLevel: Boolean) {
    fun IrPossiblyExternalDeclaration.checkExternal() {
        if (isExternal != isExternalTopLevel) {
            throw error("isExternal validation failed for declaration ${declaration.render()}")
        }
    }

    if (declaration is IrPossiblyExternalDeclaration) {
        declaration.checkExternal()
    }
    if (declaration is IrProperty) {
        declaration.getter?.checkExternal()
        declaration.setter?.checkExternal()
        declaration.backingField?.checkExternal()
    }
    if (declaration is IrClass) {
        declaration.declarations.forEach {
            validateNestedExternalDeclarations(it, isExternalTopLevel)
        }
    }
}

interface BodilessDeclarationMover {

    fun externalPackageFragment(irFile: IrFile): IrFile

    fun addPackageLevelJsModule(externalPackageFragment: IrFile)

    fun addDeclarationLevelJsModule(d: IrDeclarationWithName)

    fun addExternalDeclaration(d: IrDeclaration)

    fun addBodilessBuiltinDeclaration(d: IrDeclaration)
}

class BodilessDeclarationMoverToContext(val context: JsIrBackendContext): BodilessDeclarationMover {

    override fun externalPackageFragment(irFile: IrFile): IrFile {
        return context.externalPackageFragment.getOrPut(irFile.symbol) {
            IrFileImpl(fileEntry = irFile.fileEntry, fqName = irFile.fqName, symbol = IrFileSymbolImpl()).also {
                it.annotations += irFile.annotations
            }
        }
    }

    override fun addPackageLevelJsModule(externalPackageFragment: IrFile) {
        context.packageLevelJsModules += externalPackageFragment
    }

    override fun addDeclarationLevelJsModule(d: IrDeclarationWithName) {
        context.declarationLevelJsModules.add(d)
    }

    override fun addExternalDeclaration(d: IrDeclaration) {
        context.externalDeclarations.add(d)
    }

    override fun addBodilessBuiltinDeclaration(d: IrDeclaration) {
        context.bodilessBuiltInsPackageFragment.addChild(d)
    }
}

class DelayedBodilessDeclarationMover: BodilessDeclarationMover {

    val externalPackageFragment = mutableMapOf<IrFileSymbol, IrFile>()
    val externalDeclarations = hashSetOf<IrDeclaration>()
    val packageLevelJsModules = mutableSetOf<IrFile>()
    val declarationLevelJsModules = mutableListOf<IrDeclarationWithName>()
    val bodilessBuiltInsPackageFragment: IrPackageFragment = IrExternalPackageFragmentImpl(
        DescriptorlessExternalPackageFragmentSymbol(),
        FqName("kotlin")
    )

    override fun externalPackageFragment(irFile: IrFile): IrFile {
        return externalPackageFragment.getOrPut(irFile.symbol) {
            IrFileImpl(fileEntry = irFile.fileEntry, fqName = irFile.fqName, symbol = IrFileSymbolImpl()).also {
                it.annotations += irFile.annotations
            }
        }
    }

    override fun addPackageLevelJsModule(externalPackageFragment: IrFile) {
        packageLevelJsModules += externalPackageFragment
    }

    override fun addDeclarationLevelJsModule(d: IrDeclarationWithName) {
        declarationLevelJsModules.add(d)
    }

    override fun addExternalDeclaration(d: IrDeclaration) {
        externalDeclarations.add(d)
    }

    override fun addBodilessBuiltinDeclaration(d: IrDeclaration) {
        bodilessBuiltInsPackageFragment.addChild(d)
    }

    fun saveToContext(context: JsIrBackendContext) {
        context.externalPackageFragment += externalPackageFragment
        context.externalDeclarations += externalDeclarations
        context.packageLevelJsModules += packageLevelJsModules
        context.declarationLevelJsModules += declarationLevelJsModules
        bodilessBuiltInsPackageFragment.declarations.forEach {
            context.bodilessBuiltInsPackageFragment.addChild(it)
        }
    }
}