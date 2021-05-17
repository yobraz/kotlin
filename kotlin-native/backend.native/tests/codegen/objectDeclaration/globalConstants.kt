/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package codegen.objectDeclaration.globalConstants

import kotlin.test.*
import kotlin.native.internal.*

object EmptyClass {}

@Test fun checkEmptyClass() {
    assertTrue(EmptyClass.isGlobalConstant())
}


object ClassWithConstants {
    const val A = 1
    const val B = 2L
    const val C = 3.0
    const val D = 4.0f
    const val E = 5.toShort()
    const val F = 6.toByte()
    const val G = "8"
}

@Test fun checkInit() {
    assertTrue(ClassWithConstants.isGlobalConstant())
    assertEquals(ClassWithConstants.A, 1)
    assertEquals(ClassWithConstants.B, 2)
    assertEquals(ClassWithConstants.C, 3.0)
    assertEquals(ClassWithConstants.D, 4.0f)
    assertEquals(ClassWithConstants.E, 5)
    assertEquals(ClassWithConstants.F, 6)
    assertEquals(ClassWithConstants.G, "8")

    assertEquals((ClassWithConstants::A)(), 1)
    assertEquals((ClassWithConstants::B)(), 2)
    assertEquals((ClassWithConstants::C)(), 3.0)
    assertEquals((ClassWithConstants::D)(), 4.0f)
    assertEquals((ClassWithConstants::E)(), 5)
    assertEquals((ClassWithConstants::F)(), 6)
    assertEquals((ClassWithConstants::G)(), "8")
}


var ClassWithConstructorInitialized = 0

object ClassWithConstructor {
    init {
        ClassWithConstructorInitialized += 1
    }
    const val A = 1;
}

@Test fun checkConstructor() {
    assertEquals(ClassWithConstructorInitialized, 0)
    assertEquals(ClassWithConstructor.A, 1)
    assertEquals(ClassWithConstructorInitialized, 1)
    assertEquals(ClassWithConstructor.A, 1)
    assertEquals(ClassWithConstructorInitialized, 1)
    assertFalse(ClassWithConstructor.isGlobalConstant())
}

object ClassWithField {
    val x = 4
}

@Test fun checkField() {
    assertFalse(ClassWithField.isGlobalConstant())
}

object ClassWithComputedField {
    val x : Int
       get() = 4
}

@Test fun checkComputedField() {
    assertTrue(ClassWithComputedField.isGlobalConstant())
}
