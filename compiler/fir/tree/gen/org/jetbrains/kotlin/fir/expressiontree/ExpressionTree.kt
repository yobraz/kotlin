/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:OptIn(FirImplementationDetail::class)

@file:Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")

package org.jetbrains.kotlin.fir.expressiontree

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.builder.buildImport
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyGetter
import org.jetbrains.kotlin.fir.declarations.impl.FirPrimaryConstructor
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.builder.FirArgumentListBuilder
import org.jetbrains.kotlin.fir.expressions.builder.FirFunctionCallBuilder
import org.jetbrains.kotlin.fir.expressions.impl.FirElseIfTrueCondition
import org.jetbrains.kotlin.fir.expressions.impl.FirNoReceiverExpression
import org.jetbrains.kotlin.fir.expressions.impl.FirQualifiedAccessExpressionImpl
import org.jetbrains.kotlin.fir.expressions.impl.FirUnitExpression
import org.jetbrains.kotlin.fir.references.FirNamedReference
import org.jetbrains.kotlin.fir.references.impl.FirExplicitSuperReference
import org.jetbrains.kotlin.fir.references.impl.FirExplicitThisReference
import org.jetbrains.kotlin.fir.references.impl.FirImplicitThisReference
import org.jetbrains.kotlin.fir.references.impl.FirSimpleNamedReference
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.impl.FirImplicitNothingTypeRef
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.SimplePlatform
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.resolve.PlatformConfigurator
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.ConstantValueKind
import org.jetbrains.kotlin.types.Variance

private val EXPRESSION_TREE_NAME = Name.identifier("expressionTree")

// TODO: not thread-safe
internal var filePsiElement: PsiElement? = null

internal val syntheticModuleData = FirModuleDataImpl(
    Name.special("<expressionTree>"),
    emptyList(),
    emptyList(),
    emptyList(),
    TargetPlatform(setOf(object : SimplePlatform("JVM") {
        override val oldFashionedDescription: String
            get() = "JVM "
    })),
    object : PlatformDependentAnalyzerServices() {
        override val platformConfigurator: PlatformConfigurator = object : PlatformConfigurator {
            override val platformSpecificContainer: StorageComponentContainer = StorageComponentContainer("<expression-tree>")

            override fun configureModuleComponents(
                container: StorageComponentContainer,
                languageVersionSettings: LanguageVersionSettings
            ) {
            }

            override fun configureModuleDependentCheckers(container: StorageComponentContainer) {}

        }

        override fun computePlatformSpecificDefaultImports(storageManager: StorageManager, result: MutableList<ImportPath>) {}
    }
)

fun FirFile.replaceExpressionTreeIntrinsicCalls(): FirFile {
    filePsiElement = psi
    val transformer = ExpressionTreeTransformer()
    val result = transformDeclarations(transformer, false)
    if (transformer.addImport) {
        (result.imports as MutableList<FirImport>) += buildImport {
            this.importedFqName = FqName("org.jetbrains.kotlin.fir.expressiontree")
            this.isAllUnder = true
        }
    }
    return result
}

private class ExpressionTreeTransformer : FirTransformer<Boolean>() {
    var addImport = false

    override fun <E : FirElement> transformElement(element: E, data: Boolean): E = element.transformChildren(this, data) as E

    override fun transformFunctionCall(functionCall: FirFunctionCall, data: Boolean): FirStatement =
        if (functionCall.calleeReference.name == EXPRESSION_TREE_NAME) {
            val argumentExpression = functionCall.argumentList.arguments.singleOrNull()
            if (argumentExpression !is FirLambdaArgumentExpression) {
                error("Expression tree should accept only one lambda argument")
            }
            addImport = true
            (argumentExpression.expression as FirAnonymousFunctionExpression).anonymousFunction.body!!.build()
        } else {
            super.transformFunctionCall(functionCall, data)
        }
}

// transformers

private fun FirElement.build(): FirExpression = when (this) {
    is FirLambdaArgumentExpression -> call("firLambdaArgumentExpression", listOf(expression.build()))
    is FirAnonymousFunctionExpression -> call("firAnonymousFunctionExpression", listOf(anonymousFunction.build()))
    is FirAnonymousFunction -> call(
        "firAnonymousFunction", listOf(
            annotations.buildList(),
            isLambda.build(),
            receiverTypeRef.buildNullable(),
            valueParameters.buildList(),
            returnTypeRef.build(),
            body.buildNullable()
        )
    )
    is FirImplicitTypeRef -> call("firImplicitTypeRef", emptyList())
    is FirBlock -> call(
        "firBlock", listOf(
            annotations.buildList(),
            statements.buildList()
        )
    )
    is FirWhenExpression -> call(
        "firWhenExpression", listOf(
            annotations.buildList(),
            subject.buildNullable(),
            subjectVariable.buildNullable(),
            branches.buildList(),
            usedAsExpression.build()
        )
    )
    is FirWhenBranch -> call(
        "firWhenBranch", listOf(
            condition.build(),
            result.build()
        )
    )
    is FirEqualityOperatorCall -> call(
        "firEqualityOperatorCall", listOf(
            annotations.buildList(),
            argumentList.arguments.buildList(),
            operation.build()
        )
    )
    is FirComparisonExpression -> call(
        "firComparisonExpression", listOf(
            annotations.buildList(),
            operation.build(),
            compareToCall.explicitReceiver.buildNullable(),
            compareToCall.arguments.buildList()
        )
    )
    is FirQualifiedAccessExpressionImpl -> call(
        "firQualifiedAccessExpression", listOf(
            annotations.buildList(),
            typeArguments.buildList(),
            dispatchReceiver.build(),
            extensionReceiver.build(),
            explicitReceiver.buildNullable(),
            calleeReference.build(),
        )
    )
    is FirAnnotationCall -> call(
        "firAnnotationCall", listOf(
            annotations.buildList(),
            useSiteTarget.buildNullable(),
            annotationTypeRef.build(),
            calleeReference.build(),
            argumentList.arguments.buildList()
        )
    )
    is FirUserTypeRef -> call(
        "firUserTypeRef", listOf(
            annotations.buildList(),
            isMarkedNullable.build(),
            qualifier.buildList()
        )
    )
    is FirSimpleNamedReference -> call(
        "firSimpleNamedReference", listOf(
            name.asString().build()
        )
    )
    FirNoReceiverExpression -> call("firNoReceiverExpression", emptyList())
    is FirConstExpression<*> -> when (kind) {
        ConstantValueKind.Boolean -> if (value as Boolean) call("firTrue", listOf()) else call("firFalse", listOf())
        ConstantValueKind.Byte -> call("firByte", listOf(firByte(value as Byte)))
        ConstantValueKind.Char -> call("firChar", listOf(firChar(value as Char)))
        ConstantValueKind.Double -> call("firDouble", listOf(firDouble(value as Double)))
        ConstantValueKind.Float -> call("firFloat", listOf(firFloat(value as Float)))
        ConstantValueKind.Int -> call("firInt", listOf(firInt(value as Int)))
        ConstantValueKind.IntegerLiteral -> call("firIntegerLiteral", listOf(firIntegerLiteral(value as Long)))
        ConstantValueKind.Long -> call("firLong", listOf(firLong(value as Long)))
        ConstantValueKind.Null -> call("firNull", listOf())
        ConstantValueKind.Short -> call("firShort", listOf(firShort(value as Short)))
        ConstantValueKind.String -> call("firString", listOf(firString(value as String)))
        ConstantValueKind.UnsignedByte -> call("firUnsignedByte", listOf(firUnsignedByte(value as Byte)))
        ConstantValueKind.UnsignedInt -> call("firUnsignedInt", listOf(firUnsignedInt(value as Int)))
        ConstantValueKind.UnsignedIntegerLiteral -> call(
            "firUnsignedIntegerLiteral",
            listOf(firUnsignedIntegerLiteral(value as Long))
        )
        ConstantValueKind.UnsignedLong -> call("firUnsignedLong", listOf(firUnsignedLong(value as Long)))
        ConstantValueKind.UnsignedShort -> call("firUnsignedShort", listOf(firUnsignedShort(value as Short)))
    }
    is FirReturnExpression -> call(
        "firReturnExpression", listOf(
            annotations.buildList(),
            (target as FirFunctionTarget).build(),
            result.build()
        )
    )
    is FirImplicitNothingTypeRef -> call(
        "firImplicitNothingTypeRef", emptyList()
    )
    is FirProperty -> call(
        "firProperty", listOf(
            annotations.buildList(),
            status.build(),
            typeParameters.buildList(),
            receiverTypeRef.buildNullable(),
            name.asString().build(),
            returnTypeRef.build(),
            isVar.build(),
            isLocal.build(),
            getter.buildNullable(),
            setter.buildNullable(),
            symbol.callableId.build(),
            initializer.buildNullable(),
            delegate.buildNullable()
        )
    )
    is FirElseIfTrueCondition -> call(
        "firElseIfTrueCondition", listOf(
            annotations.buildList()
        )
    )
    is FirBinaryLogicExpression -> call(
        "firBinaryLogicExpression", listOf(
            annotations.buildList(),
            leftOperand.build(),
            rightOperand.build(),
            kind.build()
        )
    )
    is FirWhenSubjectExpression -> call(
        "firWhenSubjectExpression", listOf(
            annotations.buildList()
        )
    )
    is FirTypeOperatorCall -> call(
        "firTypeOperatorCall", listOf(
            annotations.buildList(),
            argumentList.arguments.buildList(),
            operation.build(),
            conversionTypeRef.build()
        )
    )
    is FirExplicitThisReference -> call(
        "firExplicitThisReference", listOf(
            labelName.buildNullable()
        )
    )
    is FirImplicitThisReference -> call(
        "firImplicitThisReference", emptyList()
    )
    is FirSafeCallExpression -> call(
        "firSafeCallExpression", listOf(
            annotations.buildList(),
            receiver.build(),
            regularQualifiedAccess.build()
        )
    )
    is FirCheckedSafeCallSubject -> call(
        "firCheckedSafeCallSubject", listOf()
    )
    is FirVariableAssignment -> call(
        "firVariableAssignment", listOf(
            annotations.buildList(),
            typeArguments.buildList(),
            explicitReceiver.buildNullable(),
            dispatchReceiver.buildNullable(),
            extensionReceiver.buildNullable(),
            rValue.build(),
            calleeReference.build()
        )
    )
    is FirGetClassCall -> call(
        "firGetClassCall", listOf(
            annotations.buildList(),
            argumentList.arguments.buildList()
        )
    )
    is FirNamedReference -> call(
        "firNamedReference", listOf(
            name.asString().build()
        )
    )
    is FirAssignmentOperatorStatement -> call(
        "firAssignmentOperatorStatement", listOf(
            annotations.buildList(),
            operation.build(),
            leftArgument.build(),
            rightArgument.build()
        )
    )
    is FirWhileLoop -> call(
        "firWhileLoop", listOf(
            annotations.buildList(),
            label?.name.buildNullable(),
            condition.build(),
            block.build()
        )
    )
    is FirLabel -> call(
        "firLabel", listOf(
            name.build()
        )
    )
    is FirContinueExpression -> call(
        "firContinueExpression", listOf(
            annotations.buildList(),
            target.labelName.buildNullable()
        )
    )
    is FirRegularClass -> call(
        "firRegularClass", listOf(
            annotations.buildList(),
            status.build(),
            classKind.build(),
            name.asString().build(),
            typeParameters.buildList(),
            declarations.buildList(),
            companionObject.buildNullable(),
            symbol.classId.build()
        )
    )
    is FirDeclarationStatus -> call(
        "firDeclarationStatus", listOf(
            visibility.build(),
            modality.buildNullable(),
            isExpect.build(),
            isActual.build(),
            isOverride.build(),
            isOperator.build(),
            isInfix.build(),
            isInline.build(),
            isTailRec.build(),
            isExternal.build(),
            isConst.build(),
            isLateInit.build(),
            isInner.build(),
            isCompanion.build(),
            isData.build(),
            isSuspend.build(),
            isStatic.build(),
            isFromSealedClass.build(),
            isFromEnumClass.build(),
            isFun.build()
        )
    )
    is FirPrimaryConstructor -> call(
        "firPrimaryConstructor", listOf(
            annotations.buildList(),
            status.build(),
            returnTypeRef.build(),
            receiverTypeRef.buildNullable(),
            typeParameters.buildList(),
            valueParameters.buildList(),
            delegatedConstructor.buildNullable(),
            body.buildNullable(),
            symbol.callableId.build()
        )
    )
    is FirResolvedTypeRef -> call(
        "firResolvedTypeRef", listOf(
            annotations.buildList(),
            type.classId.buildNullable(),
            (type.nullability == ConeNullability.NULLABLE).build()
        )
    )
    is FirValueParameter -> call(
        "firValueParameter", listOf(
            annotations.buildList(),
            returnTypeRef.build(),
            name.asString().build(),
            defaultValue.buildNullable(),
            isCrossinline.build(),
            isNoinline.build(),
            isVararg.build()
        )
    )
    is FirDelegatedConstructorCall -> call(
        "firDelegatedConstructorCall", listOf(
            annotations.buildList(),
            argumentList.arguments.buildList(),
            constructedTypeRef.build(),
            dispatchReceiver.build(),
            calleeReference.build(),
            isThis.build()
        )
    )
    is FirExplicitSuperReference -> call(
        "firExplicitSuperReference", listOf(
            labelName.buildNullable(),
            superTypeRef.build()
        )
    )
    is FirDefaultPropertyGetter -> call(
        "firDefaultPropertyGetter", listOf(
            returnTypeRef.build(),
            visibility.build()
        )
    )
    is FirAnonymousInitializer -> call(
        "firAnonymousInitializer", listOf(
            body.buildNullable()
        )
    )
    is FirUnitExpression -> call(
        "firUnitExpression", listOf(
            annotations.buildList()
        )
    )
    is FirSimpleFunction -> call(
        "firSimpleFunction", listOf(
            annotations.buildList(),
            status.build(),
            symbol.callableId.build(),
            typeParameters.buildList(),
            receiverTypeRef.buildNullable(),
            name.asString().build(),
            valueParameters.buildList(),
            returnTypeRef.build(),
            body.buildNullable()
        )
    )
    is FirFunctionTypeRef -> call(
        "firFunctionTypeRef", listOf(
            annotations.buildList(),
            isMarkedNullable.build(),
            receiverTypeRef.buildNullable(),
            valueParameters.buildList(),
            returnTypeRef.buildNullable(),
            isSuspend.build()
        )
    )
    is FirTypeProjectionWithVariance -> call(
        "firTypeProjectionWithVariance", listOf(
            typeRef.build(),
            variance.build()
        )
    )
    is FirTryExpression -> call(
        "firTryExpression", listOf(
            annotations.buildList(),
            tryBlock.build(),
            catches.buildList(),
            finallyBlock.buildNullable()
        )
    )
    is FirThrowExpression -> call(
        "firThrowExpression", listOf(
            annotations.buildList(),
            exception.build()
        )
    )
    is FirCatch -> call(
        "firCatch", listOf(
            parameter.build(),
            block.build()
        )
    )
    is FirPropertyAccessor -> call(
        "firPropertyAccessor", listOf(
            annotations.buildList(),
            status.build(),
            isGetter.build(),
            typeParameters.buildList(),
            valueParameters.buildList(),
            body.buildNullable(),
            returnTypeRef.build()
        )
    )
    is FirBreakExpression -> call(
        "firBreakExpression", listOf(
            annotations.buildList(),
            target.labelName.buildNullable()
        )
    )
    is FirDoWhileLoop -> call(
        "firDoWhileLoop", listOf(
            annotations.buildList(),
            label?.name.buildNullable(),
            block.build(),
            condition.build()
        )
    )
    is FirTypeParameter -> call(
        "firTypeParameter", listOf(
            annotations.buildList(),
            name.asString().build(),
            variance.build(),
            isReified.build(),
            bounds.buildList()
        )
    )
    is FirElvisExpression -> call(
        "firElvisExpression", listOf(
            annotations.buildList(),
            lhs.build(),
            rhs.build()
        )
    )
    is FirCheckNotNullCall -> call(
        "firCheckNotNullCall", listOf(
            annotations.buildList(),
            argumentList.arguments.buildList()
        )
    )
    is FirThisReceiverExpression -> call(
        "firThisReceiverExpression", listOf(
            annotations.buildList(),
            typeArguments.buildList(),
            calleeReference.build()
        )
    )
    is FirFunctionCall -> call(
        "firFunctionCall", listOf(
            annotations.buildList(),
            typeArguments.buildList(),
            explicitReceiver.buildNullable(),
            dispatchReceiver.build(),
            extensionReceiver.build(),
            argumentList.arguments.buildList(),
            calleeReference.build()
        )
    )
    is FirStringConcatenationCall -> call(
        "firStringConcatenationCall", listOf(
            annotations.buildList(),
            argumentList.arguments.buildList()
        )
    )
    is FirNamedArgumentExpression -> call(
        "firNamedArgumentExpression", listOf(
            annotations.buildList(),
            expression.build(),
            isSpread.build(),
            name.asString().build()
        )
    )
    is FirCallableReferenceAccess -> call(
        "firCallableReferenceAccess", listOf(
            annotations.buildList(),
            typeArguments.buildList(),
            explicitReceiver.buildNullable(),
            dispatchReceiver.build(),
            extensionReceiver.build(),
            calleeReference.build(),
            hasQuestionMarkAtLHS.build()
        )
    )
    is FirAnonymousObjectExpression -> call(
        "firAnonymousObjectExpression", listOf(
            anonymousObject.build()
        )
    )
    is FirAnonymousObject -> call(
        "firAnonymousObject", listOf(
            annotations.buildList(),
            typeParameters.buildList(),
            classKind.build(),
            superTypeRefs.buildList(),
            declarations.buildList()
        )
    )
    is FirErrorExpression -> call(
        "firErrorExpression", listOf(
            annotations.buildList(),
            expression.buildNullable(),
            diagnostic.reason.build()
        )
    )
    else -> error("$this is unsupported")
}

private fun Variance.build(): FirFunctionCall = when (this) {
    Variance.INVARIANT -> call("firVariance_INVARIANT", emptyList())
    Variance.IN_VARIANCE -> call("firVariance_IN_VARIANCE", emptyList())
    Variance.OUT_VARIANCE -> call("firVariance_OUT_VARIANCE", emptyList())
}

private fun CallableId.build(): FirExpression = call(
    "firCallableId", listOf(
        packageName.asString().build(),
        className?.asString().buildNullable(),
        callableName.asString().build()
    )
)

private fun ClassId?.buildNullable(): FirExpression = this?.build() ?: firNull()

private fun ClassId.build(): FirFunctionCall = call(
    "firClassId", listOf(
        packageFqName.asString().build(),
        relativeClassName.asString().build(),
        isLocal.build()
    )
)

private fun Modality?.buildNullable(): FirExpression = this?.build() ?: firNull()

private fun Modality.build(): FirFunctionCall = when (this) {
    Modality.FINAL -> call("firModality_FINAL", emptyList())
    Modality.SEALED -> call("firModality_SEALED", emptyList())
    Modality.OPEN -> call("firModality_OPEN", emptyList())
    Modality.ABSTRACT -> call("firModality_ABSTRACT", emptyList())
}

private fun Visibility.build(): FirFunctionCall = when (this) {
    Visibilities.Public -> call("firVisibilities_Public", emptyList())
    Visibilities.Protected -> call("firVisibilities_Protected", emptyList())
    Visibilities.Private -> call("firVisibilities_Private", emptyList())
    Visibilities.Local -> call("firVisibilities_Local", emptyList())
    Visibilities.Unknown -> call("firVisibilities_Unknown", emptyList())
    else -> error("Unknown visibility $this")
}

private fun ClassKind.build(): FirExpression = when (this) {
    ClassKind.CLASS -> call("firClassKind_CLASS", emptyList())
    ClassKind.INTERFACE -> call("firClassKind_INTERFACE", emptyList())
    ClassKind.ENUM_CLASS -> call("firClassKind_ENUM_CLASS", emptyList())
    ClassKind.ENUM_ENTRY -> call("firClassKind_ENUM_ENTRY", emptyList())
    ClassKind.ANNOTATION_CLASS -> call("firClassKind_ANNOTATION_CLASS", emptyList())
    ClassKind.OBJECT -> call("firClassKind_OBJECT", emptyList())
}

private fun LogicOperationKind.build(): FirFunctionCall = when (this) {
    LogicOperationKind.AND -> call("firLogicOperationKind_AND", listOf())
    LogicOperationKind.OR -> call("firLogicOperationKind_OR", listOf())
}

private fun FirFunctionTarget.build(): FirFunctionCall = call(
    "firFunctionTarget", listOf(
        labelName.buildNullable(),
        isLambda.build()
    )
)

private fun String?.buildNullable(): FirExpression = this?.build() ?: firNull()

@JvmName("buildFirQualifierPartList")
private fun List<FirQualifierPart>.buildList(): FirFunctionCall = map { it.build() }.callListOf()

private fun FirQualifierPart.build(): FirFunctionCall = call(
    "firQualifierPart", listOf(
        name.asString().build(),
        typeArgumentList.typeArguments.buildList()
    )
)

private fun String.build(): FirExpression = firString(this)

private fun AnnotationUseSiteTarget?.buildNullable(): FirExpression = this?.build() ?: firNull()

private fun AnnotationUseSiteTarget.build(): FirFunctionCall = when (this) {
    AnnotationUseSiteTarget.FIELD -> call("annotationUseSiteTarget_FIELD", emptyList())
    AnnotationUseSiteTarget.FILE -> call("annotationUseSiteTarget_FILE", emptyList())
    AnnotationUseSiteTarget.PROPERTY -> call("annotationUseSiteTarget_PROPERTY", emptyList())
    AnnotationUseSiteTarget.PROPERTY_GETTER -> call("annotationUseSiteTarget_PROPERTY_GETTER", emptyList())
    AnnotationUseSiteTarget.PROPERTY_SETTER -> call("annotationUseSiteTarget_PROPERTY_SETTER", emptyList())
    AnnotationUseSiteTarget.RECEIVER -> call("annotationUseSiteTarget_RECEIVER", emptyList())
    AnnotationUseSiteTarget.CONSTRUCTOR_PARAMETER -> call("annotationUseSiteTarget_CONSTRUCTOR_PARAMETER", emptyList())
    AnnotationUseSiteTarget.SETTER_PARAMETER -> call("annotationUseSiteTarget_SETTER_PARAMETER", emptyList())
    AnnotationUseSiteTarget.PROPERTY_DELEGATE_FIELD -> call("annotationUseSiteTarget_PROPERTY_DELEGATE_FIELD", emptyList())
}

private fun FirOperation.build(): FirFunctionCall = when (this) {
    FirOperation.EQ -> call("firOperation_EQ", emptyList())
    FirOperation.NOT_EQ -> call("firOperation_NOT_EQ", emptyList())
    FirOperation.IDENTITY -> call("firOperation_IDENTITY", emptyList())
    FirOperation.NOT_IDENTITY -> call("firOperation_NOT_IDENTITY", emptyList())
    FirOperation.LT -> call("firOperation_LT", emptyList())
    FirOperation.GT -> call("firOperation_GT", emptyList())
    FirOperation.LT_EQ -> call("firOperation_LT_EQ", emptyList())
    FirOperation.GT_EQ -> call("firOperation_GT_EQ", emptyList())
    FirOperation.ASSIGN -> call("firOperation_ASSIGN", emptyList())
    FirOperation.PLUS_ASSIGN -> call("firOperation_PLUS_ASSIGN", emptyList())
    FirOperation.MINUS_ASSIGN -> call("firOperation_MINUS_ASSIGN", emptyList())
    FirOperation.TIMES_ASSIGN -> call("firOperation_TIMES_ASSIGN", emptyList())
    FirOperation.DIV_ASSIGN -> call("firOperation_DIV_ASSIGN", emptyList())
    FirOperation.REM_ASSIGN -> call("firOperation_REM_ASSIGN", emptyList())
    FirOperation.EXCL -> call("firOperation_EXCL", emptyList())
    FirOperation.IS -> call("firOperation_IS", emptyList())
    FirOperation.NOT_IS -> call("firOperation_NOT_IS", emptyList())
    FirOperation.AS -> call("firOperation_AS", emptyList())
    FirOperation.SAFE_AS -> call("firOperation_SAFE_AS", emptyList())
    FirOperation.OTHER -> call("firOperation_OTHER", emptyList())
}

private fun Boolean.build(): FirExpression = if (this) firTrue() else firFalse()

private fun FirElement?.buildNullable(): FirExpression = this?.build() ?: firNull()

private fun List<FirElement>.buildList(): FirFunctionCall = map { it.build() }.callListOf()

private fun call(name: String, arguments: List<FirExpression>): FirFunctionCall = FirFunctionCallBuilder().apply {
    calleeReference = FirSimpleNamedReference(null, Name.identifier(name), null)
    argumentList = FirArgumentListBuilder().apply { this.arguments.addAll(arguments) }.build()
}.build()

private fun List<FirExpression>.callListOf() = call("listOf", this)

internal fun expressionTreeFirFakeSourceElement() = FirFakeSourceElement(object : FakePsiElement() {
    override fun getParent(): PsiElement = filePsiElement!!
}, FirFakeSourceElementKind.ExpressionTree)