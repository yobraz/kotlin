/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiType
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UastQualifiedExpressionAccessType
import org.jetbrains.uast.kotlin.internal.DelegatedMultiResolve

class KotlinUQualifiedReferenceExpression(
        override val sourcePsi: KtDotQualifiedExpression,
        givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UQualifiedReferenceExpression, DelegatedMultiResolve,
        KotlinUElementWithType, KotlinEvaluatableUElement {
    override val receiver by lz { KotlinConverter.convertOrEmpty(sourcePsi.receiverExpression, this) }
    override val selector by lz { KotlinConverter.convertOrEmpty(sourcePsi.selectorExpression, this) }
    override val accessType = UastQualifiedExpressionAccessType.SIMPLE

    override fun resolve(): PsiElement? = sourcePsi.selectorExpression?.let { resolveToDeclaration(it) }

    override val resolvedName: String?
        get() = (resolve() as? PsiNamedElement)?.name

    override val referenceNameElement: UElement?
        get() = when (val selector = selector) {
            is UCallExpression -> selector.methodIdentifier
            else -> super.referenceNameElement
        }

    //TODO: these overrides are required until uast migrate to -Xjvm-defaults
    override fun evaluate(): Any? {
        return super<KotlinEvaluatableUElement>.evaluate()
    }

    override fun getExpressionType(): PsiType? {
        return super<KotlinUElementWithType>.getExpressionType()
    }
}
