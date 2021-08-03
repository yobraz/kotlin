/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.expressiontree

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirFunctionTarget
import org.jetbrains.kotlin.fir.FirLabel
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyGetter
import org.jetbrains.kotlin.fir.declarations.impl.FirPrimaryConstructor
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.impl.FirElseIfTrueCondition
import org.jetbrains.kotlin.fir.expressions.impl.FirNoReceiverExpression
import org.jetbrains.kotlin.fir.expressions.impl.FirUnitExpression
import org.jetbrains.kotlin.fir.references.FirNamedReference
import org.jetbrains.kotlin.fir.references.impl.FirExplicitSuperReference
import org.jetbrains.kotlin.fir.references.impl.FirExplicitThisReference
import org.jetbrains.kotlin.fir.references.impl.FirImplicitThisReference
import org.jetbrains.kotlin.fir.references.impl.FirSimpleNamedReference
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.impl.FirImplicitNothingTypeRef
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.types.ConstantValueKind
import org.jetbrains.kotlin.types.Variance

private class ExpressionTreeDumper {
    val buffer = StringBuffer()

    private var indent = 0

    private fun indented(block: () -> Unit) {
        indent += 2
        block()
        indent -= 2
    }

    private fun putIndent() {
        buffer.append((0..indent).joinToString(separator = "") { " " })
    }

    private fun putPrefix(prefix: String?) {
        if (prefix != null) {
            buffer.append(prefix)
            buffer.append(" = ")
        }
    }

    private fun dumpCall(name: String, prefix: String?, emptyArgs: Boolean = false, block: () -> Unit = {}) {
        putIndent()
        putPrefix(prefix)
        buffer.append(name)
        if (emptyArgs) {
            buffer.append("(),\n")
        } else {
            buffer.append("(\n")
            indented(block)
            putIndent()
            buffer.append("),\n")
        }
    }

    fun dump(list: List<FirElement>, prefix: String?) {
        dumpCall("listOf", prefix, list.isEmpty()) {
            for (element in list) {
                dump(element, null)
            }
        }
    }

    @JvmName("dumpFirQualifierPart")
    fun dump(list: List<FirQualifierPart>, prefix: String?) {
        dumpCall("listOf", prefix, list.isEmpty()) {
            for (element in list) {
                dump(element, null)
            }
        }
    }

    fun dump(firQualifierPart: FirQualifierPart, prefix: String?) {
        dumpCall("firQualifierPart", prefix) {
            dump(firQualifierPart.name.asString(), "name")
            dump(firQualifierPart.typeArgumentList.typeArguments, "typeArgumentList")
        }
    }

    fun dump(b: Boolean, prefix: String?) {
        putIndent()
        putPrefix(prefix)
        buffer.append(b)
        buffer.append(",\n")
    }

    fun dump(b: Byte, prefix: String?) {
        putIndent()
        putPrefix(prefix)
        buffer.append(b)
        buffer.append(",\n")
    }

    fun dump(b: Short, prefix: String?) {
        putIndent()
        putPrefix(prefix)
        buffer.append(b)
        buffer.append(",\n")
    }

    fun dump(b: Int, prefix: String?) {
        putIndent()
        putPrefix(prefix)
        buffer.append(b)
        buffer.append(",\n")
    }

    fun dump(b: Long, prefix: String?) {
        putIndent()
        putPrefix(prefix)
        buffer.append(b)
        buffer.append(",\n")
    }

    fun dump(b: Char, prefix: String?) {
        putIndent()
        putPrefix(prefix)
        buffer.append("'")
        buffer.append(b)
        buffer.append("',\n")
    }

    fun dump(b: Double, prefix: String?) {
        putIndent()
        putPrefix(prefix)
        buffer.append(b)
        buffer.append(",\n")
    }

    fun dump(b: Float, prefix: String?) {
        putIndent()
        putPrefix(prefix)
        buffer.append(b)
        buffer.append(",\n")
    }

    fun dump(b: String?, prefix: String?) {
        putIndent()
        putPrefix(prefix)
        buffer.append(b)
        buffer.append(",\n")
    }

    fun dumpNull(prefix: String?) {
        putIndent()
        putPrefix(prefix)
        buffer.append("null,\n")
    }

    fun dump(target: FirFunctionTarget, prefix: String?) {
        dumpCall("firFunctionTarget", prefix) {
            dump(target.labelName, "labelName")
            dump(target.isLambda, "isLambda")
        }
    }

    fun dump(target: AnnotationUseSiteTarget?, prefix: String?) {
        when (target) {
            null -> dumpNull(prefix)
            AnnotationUseSiteTarget.FIELD -> dumpCall("firAnnotationUseSiteTarget_FIELD", prefix, true)
            AnnotationUseSiteTarget.FILE -> dumpCall("firAnnotationUseSiteTarget_FILE", prefix, true)
            AnnotationUseSiteTarget.PROPERTY -> dumpCall("firAnnotationUseSiteTarget_PROPERTY", prefix, true)
            AnnotationUseSiteTarget.PROPERTY_GETTER -> dumpCall("firAnnotationUseSiteTarget_PROPERTY_GETTER", prefix, true)
            AnnotationUseSiteTarget.PROPERTY_SETTER -> dumpCall("firAnnotationUseSiteTarget_PROPERTY_SETTER", prefix, true)
            AnnotationUseSiteTarget.RECEIVER -> dumpCall("firAnnotationUseSiteTarget_RECEIVER", prefix, true)
            AnnotationUseSiteTarget.CONSTRUCTOR_PARAMETER -> dumpCall("firAnnotationUseSiteTarget_CONSTRUCTOR_PARAMETER", prefix, true)
            AnnotationUseSiteTarget.SETTER_PARAMETER -> dumpCall("firAnnotationUseSiteTarget_SETTER_PARAMETER", prefix, true)
            AnnotationUseSiteTarget.PROPERTY_DELEGATE_FIELD -> dumpCall("firAnnotationUseSiteTarget_PROPERTY_DELEGATE_FIELD", prefix, true)
        }
    }

    fun dump(operation: FirOperation, prefix: String?) {
        when (operation) {
            FirOperation.EQ -> dumpCall("firOperation_EQ", prefix, true)
            FirOperation.NOT_EQ -> dumpCall("firOperation_NOT_EQ", prefix, true)
            FirOperation.IDENTITY -> dumpCall("firOperation_IDENTITY", prefix, true)
            FirOperation.NOT_IDENTITY -> dumpCall("firOperation_NOT_IDENTITY", prefix, true)
            FirOperation.LT -> dumpCall("firOperation_LT", prefix, true)
            FirOperation.GT -> dumpCall("firOperation_GT", prefix, true)
            FirOperation.LT_EQ -> dumpCall("firOperation_LT_EQ", prefix, true)
            FirOperation.GT_EQ -> dumpCall("firOperation_GT_EQ", prefix, true)
            FirOperation.ASSIGN -> dumpCall("firOperation_ASSIGN", prefix, true)
            FirOperation.PLUS_ASSIGN -> dumpCall("firOperation_PLUS_ASSIGN", prefix, true)
            FirOperation.MINUS_ASSIGN -> dumpCall("firOperation_MINUS_ASSIGN", prefix, true)
            FirOperation.TIMES_ASSIGN -> dumpCall("firOperation_TIMES_ASSIGN", prefix, true)
            FirOperation.DIV_ASSIGN -> dumpCall("firOperation_DIV_ASSIGN", prefix, true)
            FirOperation.REM_ASSIGN -> dumpCall("firOperation_REM_ASSIGN", prefix, true)
            FirOperation.EXCL -> dumpCall("firOperation_EXCL", prefix, true)
            FirOperation.IS -> dumpCall("firOperation_IS", prefix, true)
            FirOperation.NOT_IS -> dumpCall("firOperation_NOT_IS", prefix, true)
            FirOperation.AS -> dumpCall("firOperation_AS", prefix, true)
            FirOperation.SAFE_AS -> dumpCall("firOperation_SAFE_AS", prefix, true)
            FirOperation.OTHER -> dumpCall("firOperation_OTHER", prefix, true)
        }
    }

    fun dump(callableId: CallableId, prefix: String?) {
        dumpCall("firCallableId", prefix) {
            dump(callableId.packageName.asString(), "packageName")
            dump(callableId.className?.asString(), "className")
            dump(callableId.callableName.asString(), "callableName")
        }
    }

    fun dump(kind: LogicOperationKind, prefix: String?) {
        when (kind) {
            LogicOperationKind.AND -> dumpCall("firLogicOperationKind_AND", prefix, true)
            LogicOperationKind.OR -> dumpCall("firLogicOperationKind_OR", prefix, true)
        }
    }

    fun dump(kind: ClassKind, prefix: String?) {
        when (kind) {
            ClassKind.CLASS -> dumpCall("firClassKind_CLASS", prefix, true)
            ClassKind.INTERFACE -> dumpCall("firClassKind_INTERFACE", prefix, true)
            ClassKind.ENUM_CLASS -> dumpCall("firClassKind_ENUM_CLASS", prefix, true)
            ClassKind.ENUM_ENTRY -> dumpCall("firClassKind_ENUM_ENTRY", prefix, true)
            ClassKind.ANNOTATION_CLASS -> dumpCall("firClassKind_ANNOTATION_CLASS", prefix, true)
            ClassKind.OBJECT -> dumpCall("firClassKind_OBJECT", prefix, true)
        }
    }

    fun dump(classId: ClassId?, prefix: String?) {
        if (classId == null) dumpNull(prefix)
        else dumpCall("firClassId", prefix) {
            dump(classId.packageFqName.asString(), "packageFqName")
            dump(classId.relativeClassName.asString(), "relativeClassName")
            dump(classId.isLocal, "isLocal")
        }
    }

    fun dump(visibility: Visibility, prefix: String?) {
        when (visibility) {
            Visibilities.Public -> dumpCall("firVisibilities_Public", prefix, true)
            Visibilities.Protected -> dumpCall("firVisibilities_Protected", prefix, true)
            Visibilities.Private -> dumpCall("firVisibilities_Private", prefix, true)
            Visibilities.Local -> dumpCall("firVisibilities_Local", prefix, true)
            Visibilities.Unknown -> dumpCall("firVisibilities_Unknown", prefix, true)
            else -> error("Unknown visibility $this")
        }
    }

    fun dump(modality: Modality?, prefix: String?) {
        when (modality) {
            null -> dumpNull(prefix)
            Modality.FINAL -> dumpCall("firModality_FINAL", prefix, true)
            Modality.SEALED -> dumpCall("firModality_SEALED", prefix, true)
            Modality.OPEN -> dumpCall("firModality_OPEN", prefix, true)
            Modality.ABSTRACT -> dumpCall("firModality_ABSTRACT", prefix, true)
        }
    }

    fun dump(variance: Variance, prefix: String?) {
        when (variance) {
            Variance.INVARIANT -> dumpCall("firVariance_INVARIANT", prefix, true)
            Variance.IN_VARIANCE -> dumpCall("firVariance_IN_VARIANCE", prefix, true)
            Variance.OUT_VARIANCE -> dumpCall("firVariance_OUT_VARIANCE", prefix, true)
        }
    }

    fun dump(expr: FirElement?, prefix: String?) {
        when (expr) {
            null -> dumpNull(prefix)
            is FirLambdaArgumentExpression -> dumpCall("firLambdaArgumentExpression", prefix) {
                dump(expr.expression, "expression")
            }
            is FirAnonymousFunctionExpression -> dumpCall("firAnonymousFunctionExpression", prefix) {
                dump(expr.anonymousFunction, "anonymousFunction")
            }
            is FirAnonymousFunction -> dumpCall("firAnonymousFunction", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.isLambda, "isLambda")
                dump(expr.receiverTypeRef, "receiverTypeRef")
                dump(expr.valueParameters, "valueParameters")
                dump(expr.returnTypeRef, "returnTypeRef")
                dump(expr.body, "body")
            }
            is FirImplicitTypeRef -> dumpCall("firImplicitTypeRef", prefix, true)
            is FirBlock -> dumpCall("firBlock", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.statements, "statements")
            }
            is FirWhenExpression -> dumpCall("firWhenExpression", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.subject, "subject")
                dump(expr.subjectVariable, "subjectVariable")
                dump(expr.branches, "branches")
                dump(expr.usedAsExpression, "usedAsExpression")
            }
            is FirWhenBranch -> dumpCall("firWhenBranch", prefix) {
                dump(expr.condition, "condition")
                dump(expr.result, "result")
            }
            is FirEqualityOperatorCall -> dumpCall("firEqualityOperatorCall", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.argumentList.arguments, "argumentList")
                dump(expr.operation, "operation")
            }
            is FirComparisonExpression -> dumpCall("firComparisonExpression", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.operation, "operation")
                dump(expr.compareToCall, "compareToCall")
            }
            is FirQualifiedAccessExpression -> dumpCall("firQualifiedAccessExpression", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.typeArguments, "typeArguments")
                dump(expr.dispatchReceiver, "dispatchReceiver")
                dump(expr.extensionReceiver, "extensionReceiver")
                dump(expr.explicitReceiver, "explicitReceiver")
                dump(expr.calleeReference, "calleeReference")
            }
            is FirAnnotationCall -> dumpCall("firAnnotationCall", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.useSiteTarget, "useSiteTarget")
                dump(expr.annotationTypeRef, "annotationTypeRef")
                dump(expr.calleeReference, "calleeReference")
                dump(expr.argumentList.arguments, "argumentList")
            }
            is FirUserTypeRef -> dumpCall("firUserTypeRef", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.isMarkedNullable, "isMarkedNullable")
                dump(expr.qualifier, "qualifier")
            }
            is FirSimpleNamedReference -> dumpCall("firSimpleNamedReference", prefix) {
                dump(expr.name.asString(), "name")
            }
            FirNoReceiverExpression -> dumpCall("firNoReceiverExpression", prefix, true)
            is FirConstExpression<*> -> when (expr.kind) {
                ConstantValueKind.Boolean -> {
                    if (expr.value as Boolean) dumpCall("firTrue", prefix, true)
                    else dumpCall("firFalse", prefix, true)
                }
                ConstantValueKind.Byte -> dumpCall("firByte", prefix) {
                    dump(expr.value as Byte, "value")
                }
                ConstantValueKind.Char -> dumpCall("firChar", prefix) {
                    dump(expr.value as Char, "value")
                }
                ConstantValueKind.Double -> dumpCall("firDouble", prefix) {
                    dump(expr.value as Double, "value")
                }
                ConstantValueKind.Float -> dumpCall("firFloat", prefix) {
                    dump(expr.value as Float, "value")
                }
                ConstantValueKind.Int -> dumpCall("firInt", prefix) {
                    dump(expr.value as Int, "value")
                }
                ConstantValueKind.IntegerLiteral -> dumpCall("firIntegerLiteral", prefix) {
                    dump(expr.value as Long, "value")
                }
                ConstantValueKind.Long -> dumpCall("firLong", prefix) {
                    dump(expr.value as Long, "value")
                }
                ConstantValueKind.Null -> dumpCall("firNull", prefix, true)
                ConstantValueKind.Short -> dumpCall("firShort", prefix) {
                    dump(expr.value as Short, "value")
                }
                ConstantValueKind.String -> dumpCall("firString", prefix) {
                    dump(expr.value as String, "value")
                }
                ConstantValueKind.UnsignedByte -> dumpCall("firUnsignedByte", prefix) {
                    dump(expr.value as Byte, "value")
                }
                ConstantValueKind.UnsignedInt -> dumpCall("firUnsignedInt", prefix) {
                    dump(expr.value as Int, "value")
                }
                ConstantValueKind.UnsignedIntegerLiteral -> dumpCall("firUnsignedIntegerLiteral", prefix) {
                    dump(expr.value as Long, "value")
                }
                ConstantValueKind.UnsignedLong -> dumpCall("firUnsignedLong", prefix) {
                    dump(expr.value as Long, "value")
                }
                ConstantValueKind.UnsignedShort -> dumpCall("firUnsignedShort", prefix) {
                    dump(expr.value as Short, "value")
                }
            }
            is FirReturnExpression -> dumpCall("firReturnExpression", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.target as FirFunctionTarget, "target")
                dump(expr.result, "result")
            }
            is FirImplicitNothingTypeRef -> dumpCall("firImplicitNothingTypeRef", prefix, true)
            is FirProperty -> dumpCall("firProperty", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.status, "status")
                dump(expr.typeParameters, "typeParameters")
                dump(expr.receiverTypeRef, "receiverTypeRef")
                dump(expr.name.asString(), "name")
                dump(expr.returnTypeRef, "returnTypeRef")
                dump(expr.isVar, "isVar")
                dump(expr.isLocal, "isLocal")
                dump(expr.getter, "getter")
                dump(expr.setter, "setter")
                dump(expr.symbol.callableId, "symbol")
            }
            is FirElseIfTrueCondition -> dumpCall("firElseIfTrueCondition", prefix) {
                dump(expr.annotations, "annotations")
            }
            is FirBinaryLogicExpression -> dumpCall("firBinaryLogicExpression", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.leftOperand, "leftOperand")
                dump(expr.rightOperand, "rightOperand")
                dump(expr.kind, "kind")
            }
            is FirWhenSubjectExpression -> dumpCall("firWhenSubjectExpression", prefix) {
                dump(expr.annotations, "annotations")
            }
            is FirTypeOperatorCall -> dumpCall("firTypeOperatorCall", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.argumentList.arguments, "argumentList")
                dump(expr.operation, "operation")
                dump(expr.conversionTypeRef, "conversionTypeRef")
            }
            is FirExplicitThisReference -> dumpCall("firExplicitThisReference", prefix) {
                dump(expr.labelName, "labelName")
            }
            is FirImplicitThisReference -> dumpCall("firImplicitThisReference", prefix, true)
            is FirSafeCallExpression -> dumpCall("firSafeCallExpression", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.receiver, "receiver")
                dump(expr.regularQualifiedAccess, "regularQualifiedAccess")
            }
            is FirCheckedSafeCallSubject -> dumpCall("firCheckedSafeCallSubject", prefix, true)
            is FirVariableAssignment -> dumpCall("firVariableAssignment", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.typeArguments, "typeArguments")
                dump(expr.explicitReceiver, "explicitReceiver")
                dump(expr.dispatchReceiver, "dispatchReceiver")
                dump(expr.extensionReceiver, "extensionReceiver")
                dump(expr.rValue, "rValue")
                dump(expr.calleeReference, "calleeReference")
            }
            is FirGetClassCall -> dumpCall("firGetClassCall", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.argumentList.arguments, "argumentList")
            }
            is FirNamedReference -> dumpCall("firNamedReference", prefix) {
                dump(expr.name.asString(), "name")
            }
            is FirAssignmentOperatorStatement -> dumpCall("firAssignmentOperatorStatement", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.operation, "operation")
                dump(expr.leftArgument, "leftArgument")
                dump(expr.rightArgument, "rightArgument")
            }
            is FirWhileLoop -> dumpCall("firWhileLoop", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.label?.name, "label")
                dump(expr.condition, "condition")
                dump(expr.block, "block")
            }
            is FirLabel -> dumpCall("firLabel", prefix) {
                dump(expr.name, "name")
            }
            is FirContinueExpression -> dumpCall("firContinueExpression", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.target.labelName, "target")
            }
            is FirRegularClass -> dumpCall("firRegularClass", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.status, "status")
                dump(expr.classKind, "classKind")
                dump(expr.name.asString(), "name")
                dump(expr.typeParameters, "typeParameters")
                dump(expr.declarations, "declarations")
                dump(expr.companionObject, "companionObject")
                dump(expr.symbol.classId, "symbol")
            }
            is FirDeclarationStatus -> dumpCall("firDeclarationStatus", prefix) {
                dump(expr.visibility, "visibility")
                dump(expr.modality, "modality")
                dump(expr.isExpect, "isExpect")
                dump(expr.isActual, "isActual")
                dump(expr.isOverride, "isOverride")
                dump(expr.isOperator, "isOperator")
                dump(expr.isInfix, "isInfix")
                dump(expr.isInline, "isInline")
                dump(expr.isTailRec, "isTailRec")
                dump(expr.isExternal, "isExternal")
                dump(expr.isConst, "isConst")
                dump(expr.isLateInit, "isLateInit")
                dump(expr.isInner, "isInner")
                dump(expr.isCompanion, "isCompanion")
                dump(expr.isData, "isData")
                dump(expr.isSuspend, "isSuspend")
                dump(expr.isStatic, "isStatic")
                dump(expr.isFromSealedClass, "isFromSealedClass")
                dump(expr.isFromEnumClass, "isFromEnumClass")
                dump(expr.isFun, "isFun")
            }
            is FirPrimaryConstructor -> dumpCall("firPrimaryConstructor", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.status, "status")
                dump(expr.returnTypeRef, "returnTypeRef")
                dump(expr.receiverTypeRef, "receiverTypeRef")
                dump(expr.typeParameters, "typeParameters")
                dump(expr.valueParameters, "valueParameters")
                dump(expr.delegatedConstructor, "delegatedConstructor")
                dump(expr.body, "body")
                dump(expr.symbol.callableId, "symbol")
            }
            is FirResolvedTypeRef -> dumpCall("firResolvedTypeRef", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.type.classId, "type")
                dump(expr.type.nullability == ConeNullability.NULLABLE, "type")
            }
            is FirValueParameter -> dumpCall("firValueParameter", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.returnTypeRef, "returnTypeRef")
                dump(expr.name.asString(), "name")
                dump(expr.defaultValue, "defaultValue")
                dump(expr.isCrossinline, "isCrossinline")
                dump(expr.isNoinline, "isNoinline")
                dump(expr.isVararg, "isVararg")
            }
            is FirDelegatedConstructorCall -> dumpCall("firDelegatedConstructorCall", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.argumentList.arguments, "argumentList")
                dump(expr.constructedTypeRef, "constructedTypeRef")
                dump(expr.dispatchReceiver, "dispatchReceiver")
                dump(expr.calleeReference, "calleeReference")
                dump(expr.isThis, "isThis")
            }
            is FirExplicitSuperReference -> dumpCall("firExplicitSuperReference", prefix) {
                dump(expr.labelName, "labelName")
                dump(expr.superTypeRef, "superTypeRef")
            }
            is FirDefaultPropertyGetter -> dumpCall("firDefaultPropertyGetter", prefix) {
                dump(expr.returnTypeRef, "returnTypeRef")
                dump(expr.visibility, "visibility")
            }
            is FirAnonymousInitializer -> dumpCall("firAnonymousInitializer", prefix) {
                dump(expr.body, "body")
            }
            is FirUnitExpression -> dumpCall("firUnitExpression", prefix) {
                dump(expr.annotations, "annotations")
            }
            is FirSimpleFunction -> dumpCall("firSimpleFunction", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.status, "status")
                dump(expr.symbol.callableId, "symbol")
                dump(expr.typeParameters, "typeParameters")
                dump(expr.receiverTypeRef, "receiverTypeRef")
                dump(expr.name.asString(), "name")
                dump(expr.valueParameters, "valueParameters")
                dump(expr.returnTypeRef, "returnTypeRef")
                dump(expr.body, "body")
            }
            is FirFunctionTypeRef -> dumpCall("firFunctionTypeRef", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.isMarkedNullable, "isMarkedNullable")
                dump(expr.receiverTypeRef, "receiverTypeRef")
                dump(expr.valueParameters, "valueParameters")
                dump(expr.returnTypeRef, "returnTypeRef")
                dump(expr.isSuspend, "isSuspend")
            }
            is FirTypeProjectionWithVariance -> dumpCall("firTypeProjectionWithVariance", prefix) {
                dump(expr.typeRef, "typeRef")
                dump(expr.variance, "variance")
            }
            is FirTryExpression -> dumpCall("firTryExpression", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.tryBlock, "tryBlock")
                dump(expr.catches, "catches")
                dump(expr.finallyBlock, "finallyBlock")
            }
            is FirThrowExpression -> dumpCall("firThrowExpression", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.exception, "exception")
            }
            is FirCatch -> dumpCall("firCatch", prefix) {
                dump(expr.parameter, "parameter")
                dump(expr.block, "block")
            }
            is FirPropertyAccessor -> dumpCall("firPropertyAccessor", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.status, "status")
                dump(expr.isGetter, "isGetter")
                dump(expr.typeParameters, "typeParameters")
                dump(expr.valueParameters, "valueParameters")
                dump(expr.body, "body")
                dump(expr.returnTypeRef, "returnTypeRef")
            }
            is FirBreakExpression -> dumpCall("firBreakExpression", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.target.labelName, "target")
            }
            is FirDoWhileLoop -> dumpCall("firDoWhileLoop", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.label?.name, "label")
                dump(expr.block, "block")
                dump(expr.condition, "condition")
            }
            is FirTypeParameter -> dumpCall("firTypeParameter", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.name.asString(), "name")
                dump(expr.variance, "variance")
                dump(expr.isReified, "isReified")
                dump(expr.bounds, "bounds")
            }
            is FirElvisExpression -> dumpCall("firElvisExpression", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.lhs, "lhs")
                dump(expr.rhs, "rhs")
            }
            is FirCheckNotNullCall -> dumpCall("firCheckNotNullCall", prefix) {
                dump(expr.annotations, "annotations")
                dump(expr.argumentList.arguments, "argumentList")
            }
            else -> error("$expr is unsupported")
        }
    }
}

fun FirElement.debugExpressionTree(): String {
    val dumper = ExpressionTreeDumper()
    dumper.dump(this, null)
    return dumper.buffer.toString()
}