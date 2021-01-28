/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")

package test.jvm.internal

import org.junit.Test
import kotlin.jvm.internal.*
import kotlin.test.assertEquals

inline class IC(val i: Int)

@FieldDebugMetadata(
    fields = ["a", "b", "c"],
    getters = ["getA", "b", "getC"],
    types = ["kotlin.UInt", "test.jvm.internal.IC", "java.lang.String"],
    isInline = [true, true, false]
)
class InlineClassDebugMetadataTest {
    @InlineClassLocalDebugMetadata(
        types = ["kotlin.UInt", "test.jvm.internal.IC"]
    )
    fun foo() {}

    @get:JvmName("_a")
    val a: UInt = 1u

    @JvmName("getA")
    fun getA(): UInt = 42u

    @get:JvmName("_b")
    val b: IC = IC(1)

    @JvmName("b")
    fun b(): IC = IC(42)

    @get:JvmName("_c")
    val c: String = "foo"

    fun getC(): String = "bar"

    @Test
    fun testInlineClassLocalDebugMetadata() {
        val metadata = this.javaClass.methods.single { it.name == "foo" }.declaredAnnotations[0] as InlineClassLocalDebugMetadata

        assertEquals("4294967295", metadata.stringifyLocalVariable(0, -1))
        assertEquals("IC(i=-1)", metadata.stringifyLocalVariable(1, -1))
    }

    @Test
    fun testInvokeGetter() {
        assertEquals(42, invokeGetterOfField("a"))
        assertEquals(42, invokeGetterOfField("b"))
        assertEquals("bar", invokeGetterOfField("c"))
    }

    @Test
    fun testStringifyField() {
        assertEquals("1", stringifyField("a"))
        assertEquals("IC(i=1)", stringifyField("b"))
        assertEquals("foo", stringifyField("c"))
    }

    @Test
    fun testInvokeGetterAndStringifyField() {
        assertEquals("42", invokeGetterAndStringifyField("a"))
        assertEquals("IC(i=42)", invokeGetterAndStringifyField("b"))
        assertEquals("bar", invokeGetterAndStringifyField("c"))
    }
}