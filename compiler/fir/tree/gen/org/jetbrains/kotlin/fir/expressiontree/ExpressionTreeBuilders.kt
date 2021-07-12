/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:OptIn(FirImplementationDetail::class, FirContractViolation::class)

@file:Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")

package org.jetbrains.kotlin.fir.expressiontree

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.builder.buildLabel
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.builder.*
import org.jetbrains.kotlin.fir.declarations.impl.FirDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyGetter
import org.jetbrains.kotlin.fir.declarations.impl.FirPrimaryConstructor
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.builder.*
import org.jetbrains.kotlin.fir.expressions.impl.FirElseIfTrueCondition
import org.jetbrains.kotlin.fir.expressions.impl.FirNoReceiverExpression
import org.jetbrains.kotlin.fir.expressions.impl.FirUnitExpression
import org.jetbrains.kotlin.fir.impl.FirLabelImpl
import org.jetbrains.kotlin.fir.references.*
import org.jetbrains.kotlin.fir.references.builder.*
import org.jetbrains.kotlin.fir.references.impl.FirExplicitSuperReference
import org.jetbrains.kotlin.fir.references.impl.FirExplicitThisReference
import org.jetbrains.kotlin.fir.references.impl.FirSimpleNamedReference
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertyAccessorSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.builder.FirUserTypeRefBuilder
import org.jetbrains.kotlin.fir.types.builder.buildFunctionTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.builder.buildUserTypeRef
import org.jetbrains.kotlin.fir.types.impl.FirImplicitNothingTypeRef
import org.jetbrains.kotlin.fir.types.impl.FirQualifierPartImpl
import org.jetbrains.kotlin.fir.types.impl.FirTypeArgumentListImpl
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.ConstantValueKind
import org.jetbrains.kotlin.types.Variance

fun firWhenBranch(
    condition: FirExpression,
    result: FirBlock
): FirWhenBranch = buildWhenBranch {
    this.condition = condition
    this.result = result
}

fun firWhenExpression(
    annotations: List<FirAnnotationCall>,
    subject: FirExpression?,
    subjectVariable: FirVariable?,
    branches: List<FirWhenBranch>,
    usedAsExpression: Boolean
): FirWhenExpression = buildWhenExpression {
    this.annotations += annotations
    this.subject = subject
    this.subjectVariable = subjectVariable
    this.branches += branches
    this.usedAsExpression = usedAsExpression
}.also { (it.subject as? FirWhenSubjectExpression)?.whenRef?.bind(it) }

fun firLambdaArgumentExpression(expression: FirExpression): FirLambdaArgumentExpression =
    buildLambdaArgumentExpression { this.expression = expression }

fun firAnonymousFunctionExpression(anonymousFunction: FirAnonymousFunction): FirAnonymousFunctionExpression =
    buildAnonymousFunctionExpression { this.anonymousFunction = anonymousFunction }

fun firTrue(): FirConstExpression<Boolean> = buildConstExpression(null, ConstantValueKind.Boolean, true)
fun firFalse(): FirConstExpression<Boolean> = buildConstExpression(null, ConstantValueKind.Boolean, false)
fun firNull(): FirConstExpression<Nothing?> = buildConstExpression(null, ConstantValueKind.Null, null)
fun firByte(b: Byte): FirConstExpression<Byte> = buildConstExpression(null, ConstantValueKind.Byte, b)
fun firChar(c: Char): FirConstExpression<Char> = buildConstExpression(null, ConstantValueKind.Char, c)
fun firDouble(d: Double): FirConstExpression<Double> = buildConstExpression(null, ConstantValueKind.Double, d)
fun firFloat(f: Float): FirConstExpression<Float> = buildConstExpression(null, ConstantValueKind.Float, f)
fun firInt(i: Int): FirConstExpression<Int> = buildConstExpression(null, ConstantValueKind.Int, i)
fun firIntegerLiteral(l: Long): FirConstExpression<Long> = buildConstExpression(null, ConstantValueKind.IntegerLiteral, l)
fun firLong(l: Long): FirConstExpression<Long> = buildConstExpression(null, ConstantValueKind.Long, l)
fun firShort(s: Short): FirConstExpression<Short> = buildConstExpression(null, ConstantValueKind.Short, s)
fun firUnsignedByte(b: Byte): FirConstExpression<Byte> = buildConstExpression(null, ConstantValueKind.UnsignedByte, b)
fun firUnsignedShort(s: Short): FirConstExpression<Short> = buildConstExpression(null, ConstantValueKind.UnsignedShort, s)
fun firUnsignedInt(i: Int): FirConstExpression<Int> = buildConstExpression(null, ConstantValueKind.UnsignedInt, i)
fun firUnsignedIntegerLiteral(l: Long): FirConstExpression<Long> = buildConstExpression(null, ConstantValueKind.UnsignedIntegerLiteral, l)
fun firUnsignedLong(l: Long): FirConstExpression<Long> = buildConstExpression(null, ConstantValueKind.UnsignedLong, l)
fun firString(s: String): FirConstExpression<String> = buildConstExpression(null, ConstantValueKind.String, s)

fun firBlock(annotations: List<FirAnnotationCall>, statements: List<FirStatement>): FirBlock = buildBlock {
    this.annotations += annotations
    this.statements += statements
}

fun firAnonymousFunction(
    annotations: List<FirAnnotationCall>,
    isLambda: Boolean,
    receiverType: FirTypeRef?,
    valueParameters: List<FirValueParameter>,
    returnType: FirTypeRef,
    body: FirBlock
): FirAnonymousFunction = buildAnonymousFunction {
    this.annotations += annotations
    this.isLambda = isLambda
    this.receiverTypeRef = receiverType
    this.valueParameters += valueParameters
    this.receiverTypeRef = receiverType
    this.body = body
}

fun firOperation_EQ(): FirOperation = FirOperation.EQ
fun firOperation_NOT_EQ(): FirOperation = FirOperation.NOT_EQ
fun firOperation_IDENTITY(): FirOperation = FirOperation.IDENTITY
fun firOperation_NOT_IDENTITY(): FirOperation = FirOperation.NOT_IDENTITY
fun firOperation_LT(): FirOperation = FirOperation.LT
fun firOperation_GT(): FirOperation = FirOperation.GT
fun firOperation_LT_EQ(): FirOperation = FirOperation.LT_EQ
fun firOperation_GT_EQ(): FirOperation = FirOperation.GT_EQ
fun firOperation_ASSIGN(): FirOperation = FirOperation.ASSIGN
fun firOperation_PLUS_ASSIGN(): FirOperation = FirOperation.PLUS_ASSIGN
fun firOperation_MINUS_ASSIGN(): FirOperation = FirOperation.MINUS_ASSIGN
fun firOperation_TIMES_ASSIGN(): FirOperation = FirOperation.TIMES_ASSIGN
fun firOperation_DIV_ASSIGN(): FirOperation = FirOperation.DIV_ASSIGN
fun firOperation_REM_ASSIGN(): FirOperation = FirOperation.REM_ASSIGN
fun firOperation_EXCL(): FirOperation = FirOperation.EXCL
fun firOperation_IS(): FirOperation = FirOperation.IS
fun firOperation_NOT_IS(): FirOperation = FirOperation.NOT_IS
fun firOperation_AS(): FirOperation = FirOperation.AS
fun firOperation_SAFE_AS(): FirOperation = FirOperation.SAFE_AS
fun firOperation_OTHER(): FirOperation = FirOperation.OTHER

fun firEqualityOperatorCall(
    annotations: List<FirAnnotationCall>,
    arguments: List<FirExpression>,
    operation: FirOperation
): FirEqualityOperatorCall = buildEqualityOperatorCall {
    this.annotations += annotations
    this.argumentList = FirArgumentListBuilder().apply { this.arguments += arguments }.build()
    this.operation = operation
}

fun firQualifiedAccessExpression(
    annotations: List<FirAnnotationCall>,
    typeArguments: List<FirTypeProjection>,
    dispatchReceiver: FirExpression,
    extensionReceiver: FirExpression,
    explicitReceiver: FirExpression,
    calleeReference: FirReference
): FirQualifiedAccessExpression = buildQualifiedAccessExpression {
    this.annotations += annotations
    this.typeArguments += typeArguments
    this.dispatchReceiver = dispatchReceiver
    this.extensionReceiver = extensionReceiver
    this.explicitReceiver = explicitReceiver
    this.calleeReference = calleeReference
}

fun firAnnotationCall(
    annotations: List<FirAnnotationCall>,
    useSiteTarget: AnnotationUseSiteTarget?,
    annotationTypeRef: FirTypeRef,
    calleeReference: FirReference,
    arguments: List<FirExpression>
): FirAnnotationCall = buildAnnotationCall {
    this.annotations += annotations
    this.useSiteTarget = useSiteTarget
    this.annotationTypeRef = annotationTypeRef
    this.calleeReference = calleeReference
    this.argumentList = FirArgumentListBuilder().apply { this.arguments += arguments }.build()
}

fun annotationUseSiteTarget_FIELD(): AnnotationUseSiteTarget = AnnotationUseSiteTarget.FIELD
fun annotationUseSiteTarget_FILE(): AnnotationUseSiteTarget = AnnotationUseSiteTarget.FILE
fun annotationUseSiteTarget_PROPERTY(): AnnotationUseSiteTarget = AnnotationUseSiteTarget.PROPERTY
fun annotationUseSiteTarget_PROPERTY_GETTER(): AnnotationUseSiteTarget = AnnotationUseSiteTarget.PROPERTY_GETTER
fun annotationUseSiteTarget_PROPERTY_SETTER(): AnnotationUseSiteTarget = AnnotationUseSiteTarget.PROPERTY_SETTER
fun annotationUseSiteTarget_RECEIVER(): AnnotationUseSiteTarget = AnnotationUseSiteTarget.RECEIVER
fun annotationUseSiteTarget_CONSTRUCTOR_PARAMETER(): AnnotationUseSiteTarget = AnnotationUseSiteTarget.CONSTRUCTOR_PARAMETER
fun annotationUseSiteTarget_SETTER_PARAMETER(): AnnotationUseSiteTarget = AnnotationUseSiteTarget.SETTER_PARAMETER
fun annotationUseSiteTarget_PROPERTY_DELEGATE_FIELD(): AnnotationUseSiteTarget = AnnotationUseSiteTarget.PROPERTY_DELEGATE_FIELD

fun firUserTypeRef(
    annotations: List<FirAnnotationCall>,
    isMarkedNullable: Boolean,
    qualifier: List<FirQualifierPart>
): FirUserTypeRef = buildUserTypeRef {
    this.annotations += annotations
    this.isMarkedNullable = isMarkedNullable
    this.qualifier += qualifier
}

fun firQualifierPart(
    name: String,
    typeArguments: List<FirTypeProjection>
): FirQualifierPart = FirQualifierPartImpl(
    Name.identifier(name),
    FirTypeArgumentListImpl(
        expressionTreeFirFakeSourceElement(filePsiElement!!)
    ).apply { this.typeArguments += typeArguments }
)

fun firSimpleNamedReference(
    name: String
): FirSimpleNamedReference = FirSimpleNamedReference(
    null, Name.identifier(name), null
)

fun firNoReceiverExpression(): FirNoReceiverExpression = FirNoReceiverExpression

fun firReturnExpression(
    annotations: List<FirAnnotationCall>,
    target: FirTarget<FirFunction>,
    result: FirExpression
): FirReturnExpression = buildReturnExpression {
    this.annotations += annotations
    this.target = target
    this.result = result
}

fun firFunctionTarget(
    labelName: String,
    isLambda: Boolean
): FirFunctionTarget = FirFunctionTarget(labelName, isLambda)

fun firImplicitNothingTypeRef(): FirImplicitNothingTypeRef = FirImplicitNothingTypeRef(null)

fun firProperty(
    typeParameters: List<FirTypeParameter>,
    receiverType: FirTypeRef?,
    name: String,
    returnType: FirTypeRef,
    isVar: Boolean,
    isLocal: Boolean,
    getter: FirPropertyAccessor?,
    setter: FirPropertyAccessor?
): FirProperty = buildProperty {
    this.typeParameters += typeParameters
    this.receiverTypeRef = receiverType
    this.name = Name.identifier(name)
    this.returnTypeRef = returnType
    this.isVar = isVar
    this.isLocal = isLocal
    this.getter = getter
    this.getter = setter
}

fun firComparisonExpression(
    annotations: List<FirAnnotationCall>,
    operation: FirOperation,
    compareToCall: FirFunctionCall
): FirComparisonExpression = buildComparisonExpression {
    this.annotations += annotations
    this.operation = operation
    this.compareToCall = compareToCall
}

fun firElseIfTrueCondition(
    annotations: List<FirAnnotationCall>
): FirExpression = buildElseIfTrueCondition {
    this.annotations += annotations
}

fun firLogicOperationKind_AND(): LogicOperationKind = LogicOperationKind.AND
fun firLogicOperationKind_OR(): LogicOperationKind = LogicOperationKind.OR

fun firWhenSubjectExpression(
    annotations: List<FirAnnotationCall>
): FirWhenSubjectExpression = buildWhenSubjectExpression {
    this.annotations += annotations
}

fun firTypeOperatorCall(
    annotations: List<FirAnnotationCall>,
    arguments: List<FirExpression>,
    operation: FirOperation,
    conversionTypeRef: FirTypeRef
): FirTypeOperatorCall = buildTypeOperatorCall {
    this.annotations += annotations
    this.argumentList = buildArgumentList { this.arguments += arguments }
    this.operation = operation
    this.conversionTypeRef = conversionTypeRef
}

fun firExplicitThisReference(
    labelName: String?
): FirThisReference = buildExplicitThisReference {
    this.labelName = labelName
}

fun firImplicitThisReference(): FirThisReference = buildImplicitThisReference()

fun firSafeCallExpression(
    annotations: List<FirAnnotationCall>,
    receiver: FirExpression,
    regularQualifiedAccess: FirQualifiedAccess
): FirSafeCallExpression = buildSafeCallExpression {
    this.annotations += annotations
    this.receiver = receiver
    this.regularQualifiedAccess = regularQualifiedAccess
}

fun firCheckedSafeCallSubject(): FirCheckedSafeCallSubject = buildCheckedSafeCallSubject {}

fun firVariableAssignment(
    annotations: List<FirAnnotationCall>,
    typeArguments: List<FirTypeProjection>,
    explicitReceiver: FirExpression?,
    dispatchReceiver: FirExpression,
    extensionReceiver: FirExpression,
    rValue: FirExpression
): FirVariableAssignment = buildVariableAssignment {
    this.annotations += annotations
    this.typeArguments += typeArguments
    this.explicitReceiver = explicitReceiver
    this.dispatchReceiver = dispatchReceiver
    this.extensionReceiver = extensionReceiver
    this.rValue = rValue
}

fun firGetClassCall(
    annotations: List<FirAnnotationCall>,
    arguments: List<FirExpression>
): FirGetClassCall = buildGetClassCall {
    this.annotations += annotations
    this.argumentList = buildArgumentList { this.arguments += arguments }
}

fun firNamedReference(
    name: String
): FirNamedReference = buildSimpleNamedReference {
    this.name = Name.identifier(name)
}

fun firAssignmentOperatorStatement(
    annotations: List<FirAnnotationCall>,
    operation: FirOperation,
    leftArgument: FirExpression,
    rightArgument: FirExpression
): FirAssignmentOperatorStatement = buildAssignmentOperatorStatement {
    this.annotations += annotations
    this.operation = operation
    this.leftArgument = leftArgument
    this.rightArgument = rightArgument
}

fun firWhileLoop(
    annotations: List<FirAnnotationCall>,
    label: FirLabel?,
    condition: FirExpression,
    block: FirBlock
): FirWhileLoop = buildWhileLoop {
    this.annotations += annotations
    this.label = label
    this.condition = condition
    this.block = block
}

fun firLabel(name: String): FirLabel = buildLabel { this.name = name }

fun firContinueExpression(
    annotations: List<FirAnnotationCall>
): FirContinueExpression = buildContinueExpression {
    this.annotations += annotations
}

fun firClassKind_CLASS(): ClassKind = ClassKind.CLASS
fun firClassKind_INTERFACE(): ClassKind = ClassKind.INTERFACE
fun firClassKind_ENUM_CLASS(): ClassKind = ClassKind.ENUM_CLASS
fun firClassKind_ENUM_ENTRY(): ClassKind = ClassKind.ENUM_ENTRY
fun firClassKind_ANNOTATION_CLASS(): ClassKind = ClassKind.ANNOTATION_CLASS
fun firClassKind_OBJECT(): ClassKind = ClassKind.OBJECT

fun firRegularClass(
    annotations: List<FirAnnotationCall>,
    status: FirDeclarationStatus,
    classKind: ClassKind,
    name: String,
    typeParameters: List<FirTypeParameter>,
    declarations: List<FirDeclaration>,
    companionObject: FirRegularClass?
): FirRegularClass = buildRegularClass {
    this.annotations += annotations
    this.status = status
    this.classKind = classKind
    this.name = Name.identifier(name)
    this.typeParameters += typeParameters
    this.declarations += declarations
    this.companionObject = companionObject
}

fun firVisibilities_Public(): Visibility = Visibilities.Public
fun firVisibilities_Protected(): Visibility = Visibilities.Protected
fun firVisibilities_Private(): Visibility = Visibilities.Private
fun firVisibilities_Local(): Visibility = Visibilities.Local
fun firVisibilities_Unknown(): Visibility = Visibilities.Unknown

fun firModality_FINAL(): Modality = Modality.FINAL
fun firModality_SEALED(): Modality = Modality.SEALED
fun firModality_OPEN(): Modality = Modality.OPEN
fun firModality_ABSTRACT(): Modality = Modality.ABSTRACT

fun firDeclarationStatus(
    visibility: Visibility,
    modality: Modality?,
    isExpect: Boolean,
    isActual: Boolean,
    isOverride: Boolean,
    isOperator: Boolean,
    isInfix: Boolean,
    isInline: Boolean,
    isTailRec: Boolean,
    isExternal: Boolean,
    isConst: Boolean,
    isLateInit: Boolean,
    isInner: Boolean,
    isCompanion: Boolean,
    isData: Boolean,
    isSuspend: Boolean,
    isStatic: Boolean,
    isFromSealedClass: Boolean,
    isFromEnumClass: Boolean,
    isFun: Boolean
): FirDeclarationStatus = FirDeclarationStatusImpl(visibility, modality).apply {
    this.isExpect = isExpect
    this.isActual = isActual
    this.isOverride = isOverride
    this.isOperator = isOperator
    this.isInfix = isInfix
    this.isInline = isInline
    this.isTailRec = isTailRec
    this.isExternal = isExternal
    this.isConst = isConst
    this.isLateInit = isLateInit
    this.isInner = isInner
    this.isCompanion = isCompanion
    this.isData = isData
    this.isSuspend = isSuspend
    this.isStatic = isStatic
    this.isFromSealedClass = isFromSealedClass
    this.isFromEnumClass = isFromEnumClass
    this.isFun = isFun
}

fun firPrimaryConstructor(
    annotations: List<FirAnnotationCall>,
    returnType: FirTypeRef,
    receiverType: FirTypeRef?,
    typeParameters: List<FirTypeParameter>,
    valueParameters: List<FirValueParameter>,
    delegatedConstructor: FirDelegatedConstructorCall?,
    body: FirBlock?
): FirConstructor = buildPrimaryConstructor {
    this.annotations += annotations
    this.returnTypeRef = returnType
    this.receiverTypeRef = receiverType
    this.typeParameters += typeParameters
    this.valueParameters += valueParameters
    this.delegatedConstructor = delegatedConstructor
    this.body = body
}

fun firClassId(
    packageFqName: String,
    relativeClassName: String,
    isLocal: Boolean
): ClassId = ClassId(FqName(packageFqName), FqName(relativeClassName), isLocal)

fun firResolvedTypeRef(
    annotations: List<FirAnnotationCall>,
    classId: ClassId?
): FirTypeRef = buildUserTypeRef {
    this.annotations += annotations
    if (classId != null) {
        qualifier += classId.asSingleFqName().pathSegments().map {
            FirQualifierPartImpl(
                it, FirTypeArgumentListImpl(
                    expressionTreeFirFakeSourceElement(filePsiElement!!)
                )
            )
        }
    }
}

fun firValueParameter(
    annotations: List<FirAnnotationCall>,
    returnType: FirTypeRef,
    name: String,
    defaultValue: FirExpression?,
    isCrossinline: Boolean,
    isNoinline: Boolean,
    isVararg: Boolean
): FirValueParameter = buildValueParameter {
    this.annotations += annotations
    this.returnTypeRef = returnType
    this.name = Name.identifier(name)
    this.defaultValue = defaultValue
    this.isCrossinline = isCrossinline
    this.isNoinline = isNoinline
    this.isVararg = isVararg
}

fun firDelegatedConstructorCall(
    annotations: List<FirAnnotationCall>,
    arguments: List<FirExpression>,
    constructedTypeRef: FirTypeRef,
    dispatchReceiver: FirExpression,
    calleeReference: FirReference,
    isThis: Boolean
): FirDelegatedConstructorCall = buildDelegatedConstructorCall {
    this.annotations += annotations
    this.argumentList = buildArgumentList { this.arguments += arguments }
    this.constructedTypeRef = constructedTypeRef
    this.dispatchReceiver = dispatchReceiver
    this.calleeReference = calleeReference
    this.isThis = isThis
}

fun firExplicitSuperReference(
    labelName: String?,
    superTypeRef: FirTypeRef
): FirSuperReference = buildExplicitSuperReference {
    this.labelName = labelName
    this.superTypeRef = superTypeRef
}

fun firDefaultPropertyGetter(
    returnType: FirTypeRef,
    visibility: Visibility
): FirDefaultPropertyGetter = FirDefaultPropertyGetter(
    null,
    syntheticModuleData,
    FirDeclarationOrigin.Synthetic,
    returnType,
    visibility
)

fun firAnonymousInitializer(
    body: FirBlock?
): FirAnonymousInitializer = buildAnonymousInitializer {
    this.moduleData = syntheticModuleData
    this.origin = FirDeclarationOrigin.Synthetic
    this.body = body
}

fun firUnitExpression(
    annotations: List<FirAnnotationCall>
): FirExpression = buildUnitExpression {
    this.annotations += annotations
}

fun firSimpleFunction(
    annotations: List<FirAnnotationCall>,
    status: FirDeclarationStatus,
    callableId: CallableId,
    typeParameters: List<FirTypeParameter>,
    receiverType: FirTypeRef?,
    name: String,
    valueParameters: List<FirValueParameter>,
    returnType: FirTypeRef,
    body: FirBlock?
): FirSimpleFunction = buildSimpleFunction {
    this.moduleData = syntheticModuleData
    this.origin = FirDeclarationOrigin.Synthetic
    this.annotations += annotations
    this.status = status
    this.symbol = FirNamedFunctionSymbol(callableId)
    this.typeParameters += typeParameters
    this.receiverTypeRef = receiverType
    this.name = if (name.startsWith('<')) Name.special(name) else Name.identifier(name)
    this.valueParameters += valueParameters
    this.returnTypeRef = returnType
    this.body = body
}

fun firCallableId(
    packageName: String,
    className: String?,
    callableName: String
): CallableId = CallableId(
    FqName(packageName),
    className?.let { FqName(it) },
    if (callableName.startsWith('<')) Name.special(callableName) else Name.identifier(callableName)
)

fun firFunctionTypeRef(
    annotations: List<FirAnnotationCall>,
    isMarkedNullable: Boolean,
    receiverType: FirTypeRef?,
    valueParameters: List<FirValueParameter>,
    returnType: FirTypeRef,
    isSuspend: Boolean
): FirFunctionTypeRef = buildFunctionTypeRef {
    this.annotations += annotations
    this.isMarkedNullable = isMarkedNullable
    this.receiverTypeRef = receiverType
    this.valueParameters += valueParameters
    this.returnTypeRef = returnType
    this.isSuspend = isSuspend
}

fun firTypeProjectionWithVariance(
    typeRef: FirTypeRef,
    variance: Variance
): FirTypeProjectionWithVariance = buildTypeProjectionWithVariance {
    this.typeRef = typeRef
    this.variance = variance
}

fun firVariance_INVARIANT(): Variance = Variance.INVARIANT
fun firVariance_IN_VARIANCE(): Variance = Variance.IN_VARIANCE
fun firVariance_OUT_VARIANCE(): Variance = Variance.OUT_VARIANCE

fun firTryExpression(
    annotations: List<FirAnnotationCall>,
    tryBlock: FirBlock,
    catches: List<FirCatch>,
    finallyBlock: FirBlock?
): FirTryExpression = buildTryExpression {
    this.annotations += annotations
    this.tryBlock = tryBlock
    this.catches += catches
    this.finallyBlock = finallyBlock
}

fun firThrowExpression(
    annotations: List<FirAnnotationCall>,
    exception: FirExpression
): FirThrowExpression = buildThrowExpression {
    this.annotations += annotations
    this.exception = exception
}

fun firCatch(
    parameter: FirValueParameter,
    block: FirBlock
): FirCatch = buildCatch {
    this.parameter = parameter
    this.block = block
}

fun firPropertyAccessor(
    annotations: List<FirAnnotationCall>,
    status: FirDeclarationStatus,
    isGetter: Boolean,
    typeParameters: List<FirTypeParameter>,
    valueParameters: List<FirValueParameter>,
    body: FirBlock?
): FirPropertyAccessor = buildPropertyAccessor {
    this.moduleData = syntheticModuleData
    this.origin = FirDeclarationOrigin.Synthetic
    this.symbol = FirPropertyAccessorSymbol()
    this.annotations += annotations
    this.status = status
    this.isGetter = isGetter
    this.typeParameters += typeParameters
    this.valueParameters += valueParameters
    this.body = body
}

fun firBreakExpression(
    annotations: List<FirAnnotationCall>
): FirBreakExpression = buildBreakExpression {
    this.annotations += annotations
    // TODO: target
}

fun firDoWhileLoop(
    annotations: List<FirAnnotationCall>,
    labelName: String?,
    block: FirBlock,
    condition: FirExpression
): FirDoWhileLoop = buildDoWhileLoop {
    this.annotations += annotations
    this.label = labelName?.let { FirLabelImpl(null, it) }
    this.block = block
    this.condition = condition
}
