/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kapt3.ir

import com.sun.tools.javac.code.Flags
import com.sun.tools.javac.tree.JCTree
import com.sun.tools.javac.tree.JCTree.JCModifiers
import com.sun.tools.javac.tree.TreeMaker
import com.sun.tools.javac.tree.TreeScanner
import org.jetbrains.kotlin.backend.jvm.codegen.*
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.util.isEnumClass
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.kapt3.base.mapJList
import org.jetbrains.kotlin.kapt3.base.util.TopLevelJava9Aware
import org.jetbrains.kotlin.kapt3.stubs.ClassFileToSourceStubConverter.KaptStub
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import javax.lang.model.element.ElementKind
import com.sun.tools.javac.util.List as JavacList

class IrToJavaStubsConverter(val kaptContext: KaptIrContextForStubGeneration) {

    private val backendContext get() = kaptContext.backendContext

    private val treeMaker = TreeMaker.instance(kaptContext.context) as KaptIrTreeMaker
    private val typeMapper get() = kaptContext.backendContext.typeMapper


    fun convert(): List<KaptStub> {
        val classes = kaptContext.backendContext.ir.irModule.files.flatMap { it.declarations.mapNotNull { it as? IrClass } }
        return classes.mapNotNull(::convertTopLevelClass)
    }

    private fun convertTopLevelClass(clazz: IrClass): KaptStub? {
        val packageClause = clazz.packageFqName?.let(treeMaker::FqName)
        val classDeclaration = convertClass(clazz) ?: return null

        val classes = JavacList.of<JCTree>(classDeclaration)

        val topLevel = treeMaker.TopLevelJava9Aware(packageClause, classes)
        postProcess(topLevel)

        return KaptStub(topLevel)
    }

    private fun convertClass(irClass: IrClass): JCTree.JCClassDecl? {
        if (irClass.origin.isSynthetic) return null

        val flags = irClass.getFlags(backendContext.state.languageVersionSettings)
        val modifiers = convertModifiers(
            flags.toLong(),
            if (irClass.isEnumClass) ElementKind.ENUM else ElementKind.CLASS
        )
//        val simpleName = irClass.name.asString()

//        val superClassType = irClass.superTypes.find { it.getClass()?.isJvmInterface == false }
//        val interfaces = irClass.superTypes.filter { it.getClass()?.isJvmInterface == true }

        val type: Type = typeMapper.mapClass(irClass)
        val signature = typeMapper.mapClassSignature(irClass, type)

        return treeMaker.ClassDef(
            modifiers,
            treeMaker.name(signature.name),
            JavacList.nil(),
            treeMaker.FqName(signature.superclassName),
            JavacList.from(signature.interfaces.map(treeMaker::FqName)),
            JavacList.nil()
        )
    }

    private fun convertModifiers(
        access: Long,
        kind: ElementKind
    ): JCModifiers {
        //todo annotations
        val flags = when (kind) {
            ElementKind.ENUM -> access and CLASS_MODIFIERS and Opcodes.ACC_ABSTRACT.inv().toLong()
            ElementKind.CLASS -> access and CLASS_MODIFIERS
            ElementKind.METHOD -> access and METHOD_MODIFIERS
            ElementKind.FIELD -> access and FIELD_MODIFIERS
            ElementKind.PARAMETER -> access and PARAMETER_MODIFIERS
            else -> throw IllegalArgumentException("Invalid element kind: $kind")
        }
        return treeMaker.Modifiers(flags, JavacList.nil())

    }


    private fun postProcess(topLevel: JCTree.JCCompilationUnit) {
        topLevel.accept(object : TreeScanner() {
            override fun visitClassDef(clazz: JCTree.JCClassDecl) {
                // Delete enums inside enum values
                if (clazz.isEnum()) {
                    for (child in clazz.defs) {
                        if (child is JCTree.JCVariableDecl) {
                            deleteAllEnumsInside(child)
                        }
                    }
                }

                super.visitClassDef(clazz)
            }

            private fun JCTree.JCClassDecl.isEnum() = mods.flags and Opcodes.ACC_ENUM.toLong() != 0L

            private fun deleteAllEnumsInside(def: JCTree) {
                def.accept(object : TreeScanner() {
                    override fun visitClassDef(clazz: JCTree.JCClassDecl) {
                        clazz.defs = mapJList(clazz.defs) { child ->
                            if (child is JCTree.JCClassDecl && child.isEnum()) null else child
                        }

                        super.visitClassDef(clazz)
                    }
                })
            }
        })
    }

    companion object {
        private const val VISIBILITY_MODIFIERS = (Opcodes.ACC_PUBLIC or Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED).toLong()
        private const val MODALITY_MODIFIERS = (Opcodes.ACC_FINAL or Opcodes.ACC_ABSTRACT).toLong()

        private const val CLASS_MODIFIERS = VISIBILITY_MODIFIERS or MODALITY_MODIFIERS or
                (Opcodes.ACC_DEPRECATED or Opcodes.ACC_INTERFACE or Opcodes.ACC_ANNOTATION or Opcodes.ACC_ENUM or Opcodes.ACC_STATIC).toLong()

        private const val METHOD_MODIFIERS = VISIBILITY_MODIFIERS or MODALITY_MODIFIERS or
                (Opcodes.ACC_DEPRECATED or Opcodes.ACC_SYNCHRONIZED or Opcodes.ACC_NATIVE or Opcodes.ACC_STATIC or Opcodes.ACC_STRICT).toLong()

        private const val FIELD_MODIFIERS = VISIBILITY_MODIFIERS or MODALITY_MODIFIERS or
                (Opcodes.ACC_VOLATILE or Opcodes.ACC_TRANSIENT or Opcodes.ACC_ENUM or Opcodes.ACC_STATIC).toLong()

        private const val PARAMETER_MODIFIERS = FIELD_MODIFIERS or Flags.PARAMETER or Flags.VARARGS or Opcodes.ACC_FINAL.toLong()
    }
}
