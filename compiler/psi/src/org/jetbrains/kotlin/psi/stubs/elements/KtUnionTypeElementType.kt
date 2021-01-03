/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.psi.stubs.elements

import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import org.jetbrains.kotlin.psi.KtUnionType
import org.jetbrains.kotlin.psi.stubs.KotlinUnionTypeStub
import org.jetbrains.kotlin.psi.stubs.impl.KotlinUnionTypeStubImpl

class KtUnionTypeElementType(debugName: String) :
    KtStubElementType<KotlinUnionTypeStub, KtUnionType>(
        debugName,
        KtUnionType::class.java,
        KotlinUnionTypeStub::class.java
    ) {

    override fun serialize(p0: KotlinUnionTypeStub, p1: StubOutputStream) {
    }

    override fun deserialize(p0: StubInputStream, p1: StubElement<*>?): KotlinUnionTypeStub {
        return KotlinUnionTypeStubImpl(p1)
    }

    override fun createStub(p0: KtUnionType, p1: StubElement<*>?): KotlinUnionTypeStub {
        return KotlinUnionTypeStubImpl(p1)
    }
}