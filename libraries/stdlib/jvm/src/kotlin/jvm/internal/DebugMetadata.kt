/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.jvm.internal

@Target(AnnotationTarget.CLASS)
internal annotation class FieldDebugMetadata(
    @get:JvmName("v")
    val version: Int = 1,
    @get:JvmName("f")
    val fields: Array<String> = [],
    @get:JvmName("g")
    val getters: Array<String> = [],
    @get:JvmName("t")
    val types: Array<String> = [],
    @get:JvmName("i")
    val isInline: BooleanArray = [],
)

@Target(AnnotationTarget.FUNCTION)
internal annotation class InlineClassLocalDebugMetadata(
    @get:JvmName("v")
    val version: Int = 1,
    @get:JvmName("t")
    val types: Array<String> = [],
)

internal fun InlineClassLocalDebugMetadata.stringifyLocalVariable(index: Int, value: Any): String? {
    if (index >= types.size) return null

    return types[index].invokeBoxImpl(value)
}

internal fun String.invokeBoxImpl(value: Any): String? = try {
    Class.forName(this).methods.find { it.name == "box-impl" }?.invoke(null, value)?.toString()
} catch (e: Exception) { // NoSuchMethodException, SecurityException, or IllegalAccessException
    null
}

internal fun Any.invokeGetterOfField(fieldName: String): Any? {
    val metadata = fieldDebugMetadata() ?: return null

    val index = metadata.fields.indexOf(fieldName)
    if (index < 0) return null

    return try {
        javaClass.methods.find { it.name == metadata.getters[index] }?.invoke(this)
    } catch (e: Exception) { // NoSuchMethodException, SecurityException, or IllegalAccessException
        null
    }
}

internal fun Any.fieldDebugMetadata() = javaClass.getDeclaredAnnotationsByType(FieldDebugMetadata::class.java).firstOrNull()

internal fun Any.stringifyField(fieldName: String): String? {
    val metadata = fieldDebugMetadata() ?: return null

    val index = metadata.fields.indexOf(fieldName)
    if (index < 0) return null

    val value = try {
        val field = javaClass.declaredFields.find { it.name == fieldName } ?: return null
        field.isAccessible = true
        field.get(this) ?: return null
    } catch (e: Exception) { // NoSuchFieldException, SecurityException, or IllegalAccessException
        return null
    }

    return if (metadata.isInline[index]) metadata.types[index].invokeBoxImpl(value) else value.toString()
}

internal fun Any.invokeGetterAndStringifyField(fieldName: String): String? {
    val value = invokeGetterOfField(fieldName) ?: return null

    val metadata = fieldDebugMetadata() ?: return null

    val index = metadata.fields.indexOf(fieldName)
    if (index < 0) return null

    return if (metadata.isInline[index]) metadata.types[index].invokeBoxImpl(value) else value.toString()
}
