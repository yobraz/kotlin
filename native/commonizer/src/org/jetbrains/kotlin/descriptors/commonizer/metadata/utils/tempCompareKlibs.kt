/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("DuplicatedCode")

package org.jetbrains.kotlin.descriptors.commonizer.metadata.utils

import kotlinx.metadata.*
import kotlinx.metadata.klib.KlibModuleMetadata
import kotlinx.metadata.klib.fqName
import org.jetbrains.kotlin.descriptors.commonizer.cir.CirEntityId
import org.jetbrains.kotlin.descriptors.commonizer.metadata.utils.MetadataDeclarationsComparator.PathElement
import org.jetbrains.kotlin.descriptors.commonizer.metadata.utils.MetadataDeclarationsComparator.EntityKind
import org.jetbrains.kotlin.descriptors.commonizer.metadata.utils.MetadataDeclarationsComparator.EntityKind.TypeKind
import org.jetbrains.kotlin.descriptors.commonizer.metadata.utils.MetadataDeclarationsComparator.Mismatch
import org.jetbrains.kotlin.descriptors.commonizer.metadata.utils.MetadataDeclarationsComparator.Result
import org.jetbrains.kotlin.descriptors.commonizer.utils.isUnderKotlinNativeSyntheticPackages
import org.jetbrains.kotlin.library.ToolingSingleFileKlibResolveStrategy
import org.jetbrains.kotlin.library.resolveSingleFileKlib
import java.io.File
import org.jetbrains.kotlin.konan.file.File as KFile

@Suppress("SpellCheckingInspection")
fun main() {
    val baseDir0 = File("/Users/Dmitriy.Dolovov/.konan/kotlin-native-prebuilt-macos-1.4.30/klib")

    val baseDir1 = File("/Users/Dmitriy.Dolovov/temp/commonizer-output.601") // original
    val baseDir2 = File("/Users/Dmitriy.Dolovov/temp/commonizer-output.602") // fixed

    val klibPaths1 = baseDir1.listKlibs
    val klibPaths2 = baseDir2.listKlibs
    check(klibPaths1 == klibPaths2) {
        """
            Two sets of KLIB paths differ:
            $klibPaths1
            $klibPaths2
        """.trimIndent()
    }

    for (klibPath in klibPaths1) {
        doCompare(klibPath, baseDir0, baseDir1, baseDir2, false)
    }
}

private val File.listKlibs: Set<File>
    get() = walkTopDown()
        .filter { it.isDirectory && (it.name == "common" || it.parentFile.name == "platform") }
        .map { it.relativeTo(this) }
        .toSet()

private class KmResolvers(baseDir0: File, targetNames: List<String>) {
    private val defaultTargetName by lazy { targetNames.first { it != "mingw_x64" } }

    private val resolvers: Map<String, KmResolver> = targetNames.associateWith { targetName ->
        val libs = mutableListOf<KFile>()

        val stdlibDir = baseDir0.resolve("common/stdlib")
        check(stdlibDir.isDirectory) { "Not a directory: $stdlibDir" }
        libs += KFile(stdlibDir.absolutePath)

        val platformLibsDir = baseDir0.resolve("platform").resolve(targetName)
        if (platformLibsDir.exists()) {
            check(platformLibsDir.isDirectory) { "Not a directory: $platformLibsDir" }
            libs += (platformLibsDir.listFiles() ?: error("No files in $platformLibsDir"))
                .map {
                    check(it.isDirectory) { "Not a directory: $it" }
                    KFile(it.absolutePath)
                }
        } else {
            check(platformLibsDir.parentFile.isDirectory) { "Not a directory: ${platformLibsDir.parentFile}" }
        }

        KmResolver(libs)
    }

    fun getResolver(targetName: String) = resolvers[targetName.takeUnless { it == "common" } ?: defaultTargetName]
        ?: error("Unknown target: $targetName")
}

@Suppress("unused")
private class KmResolver(private val libs: Collection<KFile>) {
    private class Contents {
        val classes = mutableMapOf<String, KmClass>()
        val typeAliases = mutableMapOf<String, KmTypeAlias>()
        val properties = mutableMapOf<String, KmProperty>()
        val functions = mutableMapOf<String, KmFunction>()
    }

    private val contents by lazy {
        Contents().apply {
            libs.forEach { file ->
                val library = resolveSingleFileKlib(file, strategy = ToolingSingleFileKlibResolveStrategy)
                val metadata = KlibModuleMetadata.read(KotlinMetadataLibraryProvider(library))
                metadata.fragments.forEach { fragment ->
                    fragment.classes.forEach { clazz ->
                        classes[clazz.name] = clazz
                        val fqNamePrefix = clazz.name + '.'
                        clazz.typeAliases.associateByTo(typeAliases) { fqNamePrefix + it.name }
                        clazz.properties.associateByTo(properties) { fqNamePrefix + it.name }
                        clazz.functions.associateByTo(functions) { fqNamePrefix + it.name }
                    }

                    fragment.pkg?.let { pkg ->
                        val fqNamePrefix = fragment.fqName?.takeIf { it.isNotEmpty() }?.replace('.', '/')?.let { "$it/" } ?: ""
                        pkg.typeAliases.associateByTo(typeAliases) { fqNamePrefix + it.name }
                        pkg.properties.associateByTo(properties) { fqNamePrefix + it.name }
                        pkg.functions.associateByTo(functions) { fqNamePrefix + it.name }
                    }
                }
            }
        }
    }

    fun findClass(fullName: String): KmClass = contents.classes[fullName] ?: error("Class not found: $fullName")
    fun findTypeAlias(fullName: String): KmTypeAlias = contents.typeAliases[fullName] ?: error("Type alias not found: $fullName")
    fun findFunction(fullName: String): KmFunction = contents.functions[fullName] ?: error("Function not found: $fullName")
    fun findProperty(fullName: String): KmProperty = contents.properties[fullName] ?: error("Property not found: $fullName")

    fun findTypeAlias(packageName: String, name: String) = findTypeAlias(fullName(packageName, name))

    private fun fullName(packageName: String, name: String): String {
        if (packageName.isEmpty())
            return name

        return packageName.replace('.', '/') + '/' + name
    }
}

private fun doCompare(klibPath: File, baseDir0: File, baseDir1: File, baseDir2: File, printMatches: Boolean) {
    println("BEGIN $klibPath")

    val libs1: Map<String, KFile> =
        baseDir1.resolve(klibPath).listFiles().orEmpty().groupBy { it.name }.mapValues { KFile(it.value.single().absolutePath) }
    val libs2: Map<String, KFile> =
        baseDir2.resolve(klibPath).listFiles().orEmpty().groupBy { it.name }.mapValues { KFile(it.value.single().absolutePath) }

    val allLibs: Set<String> = libs1.keys intersect libs2.keys
    check(allLibs == libs1.keys)
    check(allLibs == libs2.keys)

    val (currentTargetName, targetNames) = with(klibPath.path) {
        val currentTargetName = substringAfterLast('/')
        if (currentTargetName == "common")
            currentTargetName to substringBefore('/').split('-')
        else
            currentTargetName to listOf(currentTargetName)
    }

    val resolvers = KmResolvers(baseDir0, targetNames)

    for (lib in allLibs.sorted()) {
        val lib1 = libs1.getValue(lib)
        val lib2 = libs2.getValue(lib)

        val klib1 = resolveSingleFileKlib(lib1, strategy = ToolingSingleFileKlibResolveStrategy)
        val klib2 = resolveSingleFileKlib(lib2, strategy = ToolingSingleFileKlibResolveStrategy)

        val metadata1 = KlibModuleMetadata.read(KotlinMetadataLibraryProvider(klib1))
        val metadata2 = KlibModuleMetadata.read(KotlinMetadataLibraryProvider(klib2))

        when (val result = MetadataDeclarationsComparator.compare(metadata1, metadata2)) {
            Result.Success -> if (printMatches) println("- [full match] $lib")
            is Result.Failure -> {
                val resolver = resolvers.getResolver(currentTargetName)
                val mismatches = result.mismatches.filter { mismatch ->
                    when (mismatch) {
                        is Mismatch.DifferentValues -> when (mismatch.kind) {
                            EntityKind.Classifier -> {
                                val classifierA = mismatch.valueA as KmClassifier
                                val classifierB = mismatch.valueB as KmClassifier
                                if (classifierA is KmClassifier.Class && classifierB is KmClassifier.Class) {
                                    val nameA = CirEntityId.create(classifierA.name)
                                    val nameB = CirEntityId.create(classifierB.name)
                                    if (!nameA.packageName.isUnderKotlinNativeSyntheticPackages
                                        && nameB.packageName.isUnderKotlinNativeSyntheticPackages
                                        && nameA.relativeNameSegments.contentEquals(nameB.relativeNameSegments)
                                    ) {
                                        return@filter false
                                    }
                                }

                                if (classifierA is KmClassifier.Class && classifierB is KmClassifier.TypeAlias) {
                                    if ((mismatch.path.last() as? PathElement.Type)?.kind == TypeKind.UNDERLYING) {
                                        if (classifierA.name == "kotlinx/cinterop/CPointer"
                                            && (classifierB.name == "kotlinx/cinterop/COpaquePointer"
                                                    || classifierB.name == "kotlinx/cinterop/CArrayPointer")
                                        ) {
                                            return@filter false
                                        }
                                    }

                                    if (mismatch.path.any { (it as? PathElement.Type)?.kind == TypeKind.UNDERLYING }) {
                                        val nameA = classifierA.name
                                        val nameB = classifierB.name

                                        if (nameA.startsWith("kotlinx/cinterop/") && nameA.endsWith("Of")
                                            && nameB == nameA.removeSuffix("Of")
                                        ) {
                                            return@filter false
                                        }
                                    }
                                }
                            }
                            is EntityKind.FlagKind -> {
                                if (mismatch.name == "IS_NULLABLE"
                                    && mismatch.valueA == true
                                    && mismatch.valueB == false
                                ) {
                                    val entityName =
                                        when (val entityClassifier = (mismatch.path.last() as PathElement.Type).typeB.classifier) {
                                            is KmClassifier.Class -> entityClassifier.name
                                            is KmClassifier.TypeAlias -> entityClassifier.name
                                            else -> error("Unexpected classifier type: $entityClassifier")
                                        }

                                    mismatch.path.dropWhile { it !is PathElement.Package }.takeIf { it.isNotEmpty() }?.let {
                                        val packagePathElement = it[0] as PathElement.Package
                                        val typeAliasPathElement = it[1]

                                        if (typeAliasPathElement is PathElement.TypeAlias) {
                                            val usefulPath = it.drop(2)
                                            val originalTypeAlias = resolver.findTypeAlias(packagePathElement.name, typeAliasPathElement.name)

                                            var currentEntity: Any = originalTypeAlias
                                            for (pathElement in usefulPath) {
                                                currentEntity = when (currentEntity) {
                                                    is KmTypeAlias -> when (pathElement) {
                                                        is PathElement.Type -> when (pathElement.kind) {
                                                            TypeKind.UNDERLYING -> currentEntity.underlyingType
                                                            TypeKind.EXPANDED -> currentEntity.expandedType
                                                            else -> error("Unsupported type kind ${pathElement.kind} for ${pathElement::class.java} in ${currentEntity::class.java}")
                                                        }
                                                        else -> error("Unsupported path element ${pathElement::class.java} for entity ${currentEntity::class.java}")
                                                    }
                                                    is KmType -> when (pathElement) {
                                                        is PathElement.TypeArgument -> currentEntity.arguments[pathElement.index]
                                                        is PathElement.Type -> when (pathElement.kind) {
                                                            TypeKind.ABBREVIATED -> currentEntity.abbreviatedType ?: error("Null abbreviated type")
                                                            else -> error("Unsupported type kind ${pathElement.kind} for ${pathElement::class.java} in ${currentEntity::class.java}")
                                                        }
                                                        else -> error("Unsupported path element ${pathElement::class.java} for entity ${currentEntity::class.java}")
                                                    }
                                                    is KmTypeProjection -> when (pathElement) {
                                                        is PathElement.Type -> when (pathElement.kind) {
                                                            TypeKind.TYPE_ARGUMENT -> currentEntity.type ?: error("Null argument type")
                                                            else -> error("Unsupported type kind ${pathElement.kind} for ${pathElement::class.java} in ${currentEntity::class.java}")
                                                        }
                                                        else -> error("Unsupported path element ${pathElement::class.java} for entity ${currentEntity::class.java}")
                                                    }
                                                    else -> error("Unsupported entity: ${currentEntity::class.java}")
                                                }
                                            }

                                            check(currentEntity is KmType) { "Unexpected current entity type: ${currentEntity::class.java}" }

                                            fun isNonNullableSameType(currentEntity: KmType): Boolean {
                                                val classifier = currentEntity.classifier as? KmClassifier.TypeAlias ?: return false
                                                return classifier.name == entityName && !Flag.Type.IS_NULLABLE(currentEntity.flags)
                                            }

                                            if (isNonNullableSameType(currentEntity)) {
                                                return@filter false
                                            } else {
                                                val abbreviatedType = currentEntity.abbreviatedType
                                                if (abbreviatedType != null && isNonNullableSameType(abbreviatedType)) {
                                                    return@filter false
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            else -> Unit
                        }
                        is Mismatch.MissingEntity -> when (mismatch.kind) {
                            TypeKind.ABBREVIATED -> {
                                if (mismatch.missingInA) {
                                    fun List<PathElement>.indexOfKind(typeKind: TypeKind): Int? =
                                        indexOfFirst { (it as? PathElement.Type)?.kind == typeKind }.takeIf { it != -1 }

                                    fun List<PathElement>.hasTypeKinds(vararg typeKinds: TypeKind): Boolean =
                                        typeKinds.any { indexOfKind(it) != null }

                                    val allowed = when {
                                        mismatch.path.hasTypeKinds(TypeKind.RETURN, TypeKind.VALUE_PARAMETER) -> true
                                        else -> {
                                            var allowed = false

                                            val indexOfExpandedType = mismatch.path.indexOfKind(TypeKind.EXPANDED)
                                            if (indexOfExpandedType != null) {
                                                val expandedTypePathElement = mismatch.path[indexOfExpandedType] as PathElement.Type
                                                val expandedType = expandedTypePathElement.typeB

                                                if (mismatch.path.size == indexOfExpandedType + 1) {
                                                    allowed = true
                                                } else if (mismatch.path[indexOfExpandedType + 1] is PathElement.TypeArgument
                                                    && expandedType.classifier is KmClassifier.Class
                                                ) {
                                                    allowed = true
                                                }
                                            }

                                            allowed
                                        }
                                    }

                                    if (allowed) {
                                        val type = (mismatch.path.last() as PathElement.Type).typeB
                                        val abbreviatedType = type.abbreviatedType!!

                                        val className = (type.classifier as KmClassifier.Class).name
                                        val typeAliasName = (abbreviatedType.classifier as KmClassifier.TypeAlias).name

                                        val typeAlias = resolver.findTypeAlias(typeAliasName)
                                        val expandedType = typeAlias.expandedType
                                        val expandedClassName = (expandedType.classifier as KmClassifier.Class).name

                                        if (expandedClassName == className) {
                                            return@filter false
                                        }
                                    }
                                }
                            }
                            EntityKind.Function -> {
                                fun isWithFwdDeclarations(types: List<KmType>): Boolean {
                                    val classNames = mutableSetOf<String>()
                                    types.mapTo(classNames) { (it.classifier as KmClassifier.Class).name }

                                    val shouldReturnTrue = when (classNames.size) {
                                        1 -> with(classNames.first()) { startsWith("cnames/") || startsWith("objcnames/") }
                                        2 -> classNames.all { className -> className.count { it == '/' } == 2 }
                                                && classNames.count { it.startsWith("cnames/") || it.startsWith("objcnames/") } == 1
                                                && classNames.count { it.startsWith("platform/") } == 1
                                                && classNames.first().substringAfterLast('/') == classNames.last().substringAfterLast('/')
                                        else -> false // smth strange
                                    }

                                    if (shouldReturnTrue)
                                        return true

                                    for (i in 0 until types.first().arguments.size) {
                                        val subtypes = types.mapNotNull { it.arguments[i].type }
                                        if (subtypes.size == types.size && isWithFwdDeclarations(subtypes))
                                            return true
                                    }

                                    return false
                                }

                                fun isWithFwdDeclarations(functions: List<KmFunction>): Boolean {
                                    if (isWithFwdDeclarations(functions.map { it.returnType }))
                                        return true

                                    for (i in 0 until functions.first().valueParameters.size) {
                                        if (isWithFwdDeclarations(functions.map { it.valueParameters[i].type!! }))
                                            return true
                                    }

                                    return false
                                }

                                val existentFunction = mismatch.existentValue as KmFunction
                                val functionName = existentFunction.name

                                val lastPathElement = mismatch.path.last()
                                if (lastPathElement is PathElement.Class) {
                                    val fullFunctionName = lastPathElement.name + '.' + functionName

                                    if (
                                        (mismatch.missingInA
                                                && lastPathElement.clazzA.functions.none { it.name == functionName }
                                                && lastPathElement.clazzB.functions.singleOrNull { it.name == functionName } != null)
                                        || (mismatch.missingInB
                                                && lastPathElement.clazzA.functions.singleOrNull { it.name == functionName } != null
                                                && lastPathElement.clazzB.functions.none { it.name == functionName })
                                    ) {
                                        val originalFunctions = targetNames.map { targetName ->
                                            resolvers.getResolver(targetName).findFunction(fullFunctionName)
                                        }

                                        if (isWithFwdDeclarations(originalFunctions)) {
                                            return@filter false
                                        }
                                    }
                                }
                            }
                            EntityKind.TypeArgument -> {
                                val lastPathElement = mismatch.path.last()
                                if (lastPathElement is PathElement.Type) {
                                    val typeA = lastPathElement.typeA
                                    val typeB = lastPathElement.typeB

                                    val classifierA = typeA.classifier
                                    val classifierB = typeB.classifier

                                    if (mismatch.missingInB
                                        && typeA.arguments.size == 1
                                        && typeB.arguments.isEmpty()
                                        && classifierA is KmClassifier.Class
                                        && classifierB is KmClassifier.TypeAlias
                                    ) {
                                        val nameA = classifierA.name
                                        val nameB = classifierB.name

                                        if (nameA.startsWith("kotlinx/cinterop") && nameB.startsWith("kotlinx/cinterop")) {
                                            val shortNameA = nameA.substringAfter("kotlinx/cinterop/")
                                            val shortNameB = nameB.substringAfter("kotlinx/cinterop/")

                                            if (shortNameA == "CPointer" && shortNameB == "COpaquePointer") {
                                                return@filter false
                                            } else if (shortNameA.endsWith("Of") && shortNameB == shortNameA.removeSuffix("Of")) {
                                                return@filter false
                                            }
                                        }
                                    }
                                }
                            }
                            else -> Unit
                        }
                    }
                    true
                }

                if (mismatches.isNotEmpty()) {

                    println("- [MISMATCHES] $lib ${mismatches.size}")
                    mismatches
                        .groupingBy { it::class.java.simpleName to it.kind }
                        .eachCount()
                        .entries
                        .sortedBy { it.key.first + "-" + it.key.second }
                        .forEach { (key, value) ->
                            val (simpleName, entityKind) = key

                            println("\t$simpleName $entityKind -> $value")
                        }

                }
            }
        }
    }
    println("END $klibPath\n")
}
