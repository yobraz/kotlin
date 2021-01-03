/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubElement
import org.jetbrains.kotlin.psi.KtUnionType
import org.jetbrains.kotlin.psi.stubs.KotlinUnionTypeStub
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes

class KotlinUnionTypeStubImpl(
    parent: StubElement<out PsiElement>?
) : KotlinStubBaseImpl<KtUnionType>(parent, KtStubElementTypes.UNION_TYPE), KotlinUnionTypeStub
