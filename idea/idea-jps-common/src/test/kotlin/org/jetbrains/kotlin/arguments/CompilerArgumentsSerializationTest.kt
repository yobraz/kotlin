/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.arguments

import org.jetbrains.kotlin.cli.common.arguments.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.junit.Test
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

class CompilerArgumentsSerializationTest {

    @Test
    fun testDummyJVM() {
        doSerializeDeserializeAndCompareTest<K2JVMCompilerArguments>()
    }

    @Test
    fun testDummyJs() {
        doSerializeDeserializeAndCompareTest<K2JSCompilerArguments>()
    }

    @Test
    fun testDummyMetadata() {
        doSerializeDeserializeAndCompareTest<K2MetadataCompilerArguments>()
    }

    @Test
    fun testDummyJsDce() {
        doSerializeDeserializeAndCompareTest<K2JSDceArguments>()
    }

    private inline fun <reified T : CommonToolArguments> doSerializeDeserializeAndCompareTest(configure: T.() -> Unit = {}) {
        val oldInstance = T::class.java.newInstance().apply(configure)
        val serializer = CompilerArgumentsSerializerV4<T>()
        val element = serializer.serialize(oldInstance)
        val newInstance = T::class.java.newInstance()
        val deserializer = CompilerArgumentsDeserializerV4(newInstance)
        val deserializedArguments = deserializer.deserialize(element)
        T::class.memberProperties.mapNotNull { it.safeAs<KProperty1<T, *>>() }.forEach {
            assert(it.get(oldInstance) == it.get(deserializedArguments)) {
                "Property ${it.name} has different values before (${it.get(oldInstance)}) and after (${it.get(deserializedArguments)}) serialization"
            }
        }
    }
}