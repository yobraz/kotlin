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

import com.intellij.psi.PsiType
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastBinaryExpressionWithTypeKind

class KotlinUTypeCheckExpression(
        override val sourcePsi: KtIsExpression,
        givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UBinaryExpressionWithType, KotlinUElementWithType, KotlinEvaluatableUElement {
    override val operand by lz { KotlinConverter.convertOrEmpty(sourcePsi.leftHandSide, this) }
    
    override val type by lz { sourcePsi.typeReference.toPsiType(this) }
    
    override val typeReference = sourcePsi.typeReference?.let {
        LazyKotlinUTypeReferenceExpression(it, this) { it.toPsiType(this) }
    }
    
    override val operationKind =
            if(sourcePsi.isNegated)
                KotlinBinaryExpressionWithTypeKinds.NEGATED_INSTANCE_CHECK
            else
                UastBinaryExpressionWithTypeKind.INSTANCE_CHECK

    //TODO: these overrides are required until uast migrate to -Xjvm-defaults
    override fun getExpressionType(): PsiType? {
        return super<KotlinUElementWithType>.getExpressionType()
    }

    override fun evaluate(): Any? {
        return super<KotlinEvaluatableUElement>.evaluate()
    }
}