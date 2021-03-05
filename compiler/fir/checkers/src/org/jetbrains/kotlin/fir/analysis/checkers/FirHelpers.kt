/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.analysis.cfa.FirReturnsImpliesAnalyzer.isSupertypeOf
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.diagnostics.modalityModifier
import org.jetbrains.kotlin.fir.analysis.diagnostics.overrideModifier
import org.jetbrains.kotlin.fir.analysis.diagnostics.visibilityModifier
import org.jetbrains.kotlin.fir.analysis.getChild
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.impl.FirEmptyExpressionBlock
import org.jetbrains.kotlin.fir.references.FirErrorNamedReference
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.resolve.transformers.firClassLike
import org.jetbrains.kotlin.fir.scopes.ProcessorAction
import org.jetbrains.kotlin.fir.scopes.impl.FirIntegerOperatorCall
import org.jetbrains.kotlin.fir.scopes.processOverriddenFunctions
import org.jetbrains.kotlin.fir.scopes.unsubstitutedScope
import org.jetbrains.kotlin.fir.symbols.ConeClassLikeLookupTag
import org.jetbrains.kotlin.fir.symbols.StandardClassIds
import org.jetbrains.kotlin.fir.symbols.impl.ConeClassLikeLookupTagImpl
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtParameter.VAL_VAR_TOKEN_SET
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal fun FirClass<*>.unsubstitutedScope(context: CheckerContext) =
    this.unsubstitutedScope(context.sessionHolder.session, context.sessionHolder.scopeSession, withForcedTypeCalculator = false)

/**
 * Returns true if this is a superclass of other.
 */
fun FirClass<*>.isSuperclassOf(other: FirClass<*>): Boolean {
    /**
     * Hides additional parameters.
     */
    fun FirClass<*>.isSuperclassOf(other: FirClass<*>, exclude: MutableSet<FirClass<*>>): Boolean {
        for (it in other.superTypeRefs) {
            val that = it.firClassLike(session)
                ?.followAllAlias(session)
                ?.safeAs<FirClass<*>>()
                ?: continue

            if (that in exclude) {
                continue
            }

            if (that.classKind == ClassKind.CLASS) {
                if (that == this) {
                    return true
                }

                exclude.add(that)
                return this.isSuperclassOf(that, exclude)
            }
        }

        return false
    }

    return isSuperclassOf(other, mutableSetOf())
}

/**
 * Returns true if this is a supertype of other.
 */
fun FirClass<*>.isSupertypeOf(other: FirClass<*>): Boolean {
    /**
     * Hides additional parameters.
     */
    fun FirClass<*>.isSupertypeOf(other: FirClass<*>, exclude: MutableSet<FirClass<*>>): Boolean {
        for (it in other.superTypeRefs) {
            val candidate = it.firClassLike(session)
                ?.followAllAlias(session)
                ?.safeAs<FirClass<*>>()
                ?: continue

            if (candidate in exclude) {
                continue
            }

            exclude.add(candidate)

            if (candidate == this) {
                return true
            }

            if (this.isSupertypeOf(candidate, exclude)) {
                return true
            }
        }

        return false
    }

    return isSupertypeOf(other, mutableSetOf())
}

/**
 * Returns the FirRegularClass associated with this
 * or null of something goes wrong.
 */
fun ConeClassLikeType.toRegularClass(session: FirSession): FirRegularClass? {
    return lookupTag.toSymbol(session).safeAs<FirRegularClassSymbol>()?.fir
}

/**
 * Returns the FirRegularClass associated with this
 * or null of something goes wrong.
 */
fun ConeKotlinType.toRegularClass(session: FirSession): FirRegularClass? {
    return safeAs<ConeClassLikeType>()?.fullyExpandedType(session)?.toRegularClass(session)
}

fun ConeKotlinType.isInline(session: FirSession): Boolean = toRegularClass(session)?.isInline == true

/**
 * Returns the FirRegularClass associated with this
 * or null of something goes wrong.
 */
fun FirTypeRef.toRegularClass(session: FirSession): FirRegularClass? {
    return coneType.toRegularClass(session)
}

/**
 * Returns FirSimpleFunction based on the given FirFunctionCall
 */
inline fun <reified T : Any> FirQualifiedAccessExpression.getDeclaration(): T? {
    return this.calleeReference.safeAs<FirResolvedNamedReference>()
        ?.resolvedSymbol
        ?.fir.safeAs()
}

/**
 * Returns the ClassLikeDeclaration where the Fir object has been defined
 * or null if no proper declaration has been found.
 */
fun FirSymbolOwner<*>.getContainingClass(context: CheckerContext): FirClassLikeDeclaration<*>? =
    this.safeAs<FirCallableMemberDeclaration<*>>()?.containingClass()?.toSymbol(context.session)?.fir

/**
 * Returns the FirClassLikeDeclaration that the
 * sequence of FirTypeAlias'es points to starting
 * with `this`. Or null if something goes wrong.
 */
fun FirClassLikeDeclaration<*>.followAllAlias(session: FirSession): FirClassLikeDeclaration<*>? {
    var it: FirClassLikeDeclaration<*>? = this

    while (it is FirTypeAlias) {
        it = it.expandedTypeRef.firClassLike(session)
    }

    return it
}

/**
 * Returns the closest to the end of context.containingDeclarations
 * item like FirRegularClass or FirAnonymousObject
 * or null if no such item could be found.
 */
fun CheckerContext.findClosestClassOrObject(): FirClass<*>? {
    for (it in containingDeclarations.asReversed()) {
        if (
            it is FirRegularClass ||
            it is FirAnonymousObject
        ) {
            return it as FirClass<*>
        }
    }

    return null
}

/**
 * Returns the list of functions that overridden by given
 */
fun FirSimpleFunction.overriddenFunctions(
    containingClass: FirClass<*>,
    context: CheckerContext
): List<FirFunctionSymbol<*>> {
    val firTypeScope = containingClass.unsubstitutedScope(
        context.sessionHolder.session,
        context.sessionHolder.scopeSession,
        withForcedTypeCalculator = true
    )

    val overriddenFunctions = mutableListOf<FirFunctionSymbol<*>>()
    firTypeScope.processFunctionsByName(symbol.fir.name) { }
    firTypeScope.processOverriddenFunctions(symbol) {
        overriddenFunctions.add(it)
        ProcessorAction.NEXT
    }

    return overriddenFunctions
}

/**
 * Returns the visibility by given KtModifierList
 */
fun KtModifierList?.getVisibility() = this?.visibilityModifierType()?.toVisibilityOrNull()

/**
 * Returns Visibility by token or null
 */
fun KtModifierKeywordToken.toVisibilityOrNull(): Visibility? {
    return when (this) {
        KtTokens.PUBLIC_KEYWORD -> Visibilities.Public
        KtTokens.PRIVATE_KEYWORD -> Visibilities.Private
        KtTokens.PROTECTED_KEYWORD -> Visibilities.Protected
        KtTokens.INTERNAL_KEYWORD -> Visibilities.Internal
        else -> null
    }
}

/**
 * Returns the modality of the class
 */
fun FirClass<*>.modality(): Modality? {
    return when (this) {
        is FirRegularClass -> modality
        else -> Modality.FINAL
    }
}

/**
 * returns implicit modality by FirMemberDeclaration
 */
fun FirMemberDeclaration.implicitModality(context: CheckerContext): Modality {
    if (this is FirRegularClass && (this.classKind == ClassKind.CLASS || this.classKind == ClassKind.OBJECT)) {
        if (this.classKind == ClassKind.INTERFACE) return Modality.ABSTRACT
        return Modality.FINAL
    }

    val klass = context.findClosestClassOrObject() ?: return Modality.FINAL
    val source = source ?: return Modality.FINAL
    val tree = source.treeStructure
    if (tree.overrideModifier(source.lighterASTNode) != null) {
        val klassModalityTokenType = klass.source?.let { tree.modalityModifier(it.lighterASTNode)?.tokenType }
        if (klassModalityTokenType == KtTokens.ABSTRACT_KEYWORD ||
            klassModalityTokenType == KtTokens.OPEN_KEYWORD ||
            klassModalityTokenType == KtTokens.SEALED_KEYWORD
        ) {
            return Modality.OPEN
        }
    }

    if (klass is FirRegularClass
        && klass.classKind == ClassKind.INTERFACE
        && tree.visibilityModifier(source.lighterASTNode)?.tokenType != KtTokens.PRIVATE_KEYWORD
    ) {
        return if (this.hasBody()) Modality.OPEN else Modality.ABSTRACT
    }

    return Modality.FINAL
}

private fun FirDeclaration.hasBody(): Boolean = when (this) {
    is FirSimpleFunction -> this.body != null && this.body !is FirEmptyExpressionBlock
    is FirProperty -> this.setter?.body !is FirEmptyExpressionBlock? || this.getter?.body !is FirEmptyExpressionBlock?
    else -> false
}

/**
 * Finds any non-interface supertype and returns it
 * or null if couldn't find any.
 */
fun FirClass<*>.findNonInterfaceSupertype(context: CheckerContext): FirTypeRef? {
    for (superTypeRef in superTypeRefs) {
        val lookupTag = superTypeRef.coneType.safeAs<ConeClassLikeType>()?.lookupTag ?: continue

        val fir = lookupTag.toSymbol(context.session)
            ?.fir.safeAs<FirClass<*>>()
            ?: continue

        if (fir.classKind != ClassKind.INTERFACE) {
            return superTypeRef
        }
    }

    return null
}

/**
 * Returns KtModifierToken by Modality
 */
fun Modality.toToken(): KtModifierKeywordToken = when (this) {
    Modality.FINAL -> KtTokens.FINAL_KEYWORD
    Modality.SEALED -> KtTokens.SEALED_KEYWORD
    Modality.OPEN -> KtTokens.OPEN_KEYWORD
    Modality.ABSTRACT -> KtTokens.ABSTRACT_KEYWORD
}

val FirFunctionCall.isIterator
    get() = this.calleeReference.name.asString() == "<iterator>"

internal fun throwableClassLikeType(session: FirSession) = session.builtinTypes.throwableType.type

fun ConeKotlinType.isSubtypeOfThrowable(session: FirSession) =
    throwableClassLikeType(session).isSupertypeOf(session.typeContext, this.fullyExpandedType(session))

val FirValueParameter.hasValOrVar: Boolean
    get() {
        val source = this.source ?: return false
        return source.getChild(VAL_VAR_TOKEN_SET) != null
    }

fun ConeKotlinType.canBeUsedForConstVal(): Boolean {
    val classId = if (this is ConeFlexibleType) {
        val lb = this.lowerBoundIfFlexible()
        if (lb is ConeClassLikeType) lb.lookupTag.classId
        else this.classId
    } else this.classId
    return (classId in StandardClassIds.primitiveTypes || classId in StandardClassIds.unsignedTypes) && !this.isNullable ||
            classId == StandardClassIds.String
}

internal fun checkConstantArguments(
    expression: FirExpression,
    session: FirSession,
): ConstantArgumentKind? {
    val expressionSymbol = expression.toResolvedCallableSymbol()
        ?.fir
    val classKindOfParent = (expressionSymbol
        ?.getReferencedClass(session) as? FirRegularClass)
        ?.classKind

    when {
        expression is FirTypeOperatorCall -> {
            if (expression.operation == FirOperation.AS) return ConstantArgumentKind.NOT_CONST
        }
        expression is FirConstExpression<*>
                || expressionSymbol is FirEnumEntry
                || (expressionSymbol as? FirMemberDeclaration)?.isConst == true
                || expressionSymbol is FirConstructor && classKindOfParent == ClassKind.ANNOTATION_CLASS -> {
            //DO NOTHING
        }
        classKindOfParent == ClassKind.ENUM_CLASS -> {
            return ConstantArgumentKind.ENUM_NOT_CONST
        }
        expression is FirComparisonExpression -> {
            return checkConstantArguments(expression.compareToCall, session)
        }
        expression is FirIntegerOperatorCall -> {
            for (exp in (expression as FirCall).arguments.plus(expression.dispatchReceiver))
                checkConstantArguments(exp, session).let { return it }
        }
        expression is FirStringConcatenationCall || expression is FirEqualityOperatorCall -> {
            for (exp in (expression as FirCall).arguments)
                checkConstantArguments(exp, session).let { return it }
        }
        (expression is FirGetClassCall) -> {
            var coneType = (expression as? FirCall)
                ?.argument
                ?.typeRef
                ?.coneType

            if (coneType is ConeClassErrorType)
                return ConstantArgumentKind.NOT_CONST

            while (coneType?.classId == StandardClassIds.Array)
                coneType = (coneType.lowerBoundIfFlexible().typeArguments.first() as? ConeKotlinTypeProjection)?.type ?: break

            return when {
                coneType is ConeTypeParameterType ->
                    ConstantArgumentKind.KCLASS_LITERAL_OF_TYPE_PARAMETER_ERROR
                (expression as FirCall).argument !is FirResolvedQualifier ->
                    ConstantArgumentKind.NOT_KCLASS_LITERAL
                else ->
                    null
            }
        }
        expressionSymbol == null -> {
            //DO NOTHING
        }
        expressionSymbol is FirField -> {
            //TODO: fix checking of Java fields initializer
            if (
                !(expressionSymbol as FirMemberDeclaration).status.isStatic
                || (expressionSymbol as FirMemberDeclaration).status.modality != Modality.FINAL
            )
                return ConstantArgumentKind.NOT_CONST
        }
        expression is FirFunctionCall -> {
            val calleeReference = expression.calleeReference
            if (calleeReference is FirErrorNamedReference) {
                return null
            }
            if (expression.typeRef.coneType.classId == StandardClassIds.KClass) {
                return ConstantArgumentKind.NOT_KCLASS_LITERAL
            }

            //TODO: UNRESOLVED REFERENCE
            if (expression.dispatchReceiver is FirThisReceiverExpression) {
                return null
            }

            when (calleeReference.name) {
                in OperatorNameConventions.BINARY_OPERATION_NAMES, in OperatorNameConventions.UNARY_OPERATION_NAMES -> {
                    val coneType =
                        expression.dispatchReceiver.typeRef.coneTypeSafe<ConeKotlinType>() ?: return ConstantArgumentKind.NOT_CONST
                    val receiverClassId = coneType.classId


                    if ((calleeReference.name == OperatorNameConventions.DIV || calleeReference.name == OperatorNameConventions.REM)
                        && expression.typeRef.coneType.classId == StandardClassIds.Int
                    ) {
                        val value = expression.arguments.first() as? FirConstExpression<*>
                        if (value?.value == 0L) return ConstantArgumentKind.NOT_CONST
                    }

                    for (exp in (expression as FirCall).arguments.plus(expression.dispatchReceiver)) {
                        val expClassId = exp.typeRef.coneType.classId

                        if (calleeReference.name == OperatorNameConventions.PLUS
                            && expClassId != receiverClassId
                            && (expClassId !in StandardClassIds.primitiveTypesAndString || receiverClassId !in StandardClassIds.primitiveTypesAndString)
                        )
                            return ConstantArgumentKind.NOT_CONST
                        checkConstantArguments(exp, session)?.let { return it }
                    }
                }
                else -> {
                    if (expression.arguments.isNotEmpty() || calleeReference !is FirResolvedNamedReference) {
                        return ConstantArgumentKind.NOT_CONST
                    }
                    val symbol = calleeReference.resolvedSymbol as? FirCallableSymbol
                    if (calleeReference.name == OperatorNameConventions.TO_STRING ||
                        calleeReference.name in CONVERSION_NAMES && symbol?.callableId?.packageName?.asString() == "kotlin"
                    ) {
                        return checkConstantArguments(expression.dispatchReceiver, session)
                    }
                    return ConstantArgumentKind.NOT_CONST
                }
            }
        }
        expression is FirQualifiedAccessExpression -> {

            when {
                (expressionSymbol as FirProperty).isLocal || expressionSymbol.symbol.callableId.className?.isRoot == false ->
                    return ConstantArgumentKind.NOT_CONST
                expression.typeRef.coneType.classId == StandardClassIds.KClass ->
                    return ConstantArgumentKind.NOT_KCLASS_LITERAL

                //TODO: UNRESOLVED REFERENCE
                expression.dispatchReceiver is FirThisReceiverExpression ->
                    return null
            }

            return when ((expressionSymbol as? FirProperty)?.initializer) {
                is FirConstExpression<*> -> {
                    if ((expressionSymbol as? FirVariable)?.isVal == true)
                        ConstantArgumentKind.NOT_CONST_VAL_IN_CONST_EXPRESSION
                    else
                        ConstantArgumentKind.NOT_CONST
                }
                is FirGetClassCall ->
                    ConstantArgumentKind.NOT_KCLASS_LITERAL
                else ->
                    ConstantArgumentKind.NOT_CONST
            }
        }
        else ->
            return ConstantArgumentKind.NOT_CONST
    }
    return null
}

private fun FirTypedDeclaration?.getReferencedClass(session: FirSession): FirSymbolOwner<*>? =
    this?.returnTypeRef
        ?.coneTypeSafe<ConeLookupTagBasedType>()
        ?.lookupTag
        ?.toSymbol(session)
        ?.fir

private val CONVERSION_NAMES = listOf(
    "toInt", "toLong", "toShort", "toByte", "toFloat", "toDouble", "toChar", "toBoolean"
).mapTo(hashSetOf()) { Name.identifier(it) }

internal enum class ConstantArgumentKind {
    NOT_CONST,
    ENUM_NOT_CONST,
    NOT_KCLASS_LITERAL,
    NOT_CONST_VAL_IN_CONST_EXPRESSION,
    KCLASS_LITERAL_OF_TYPE_PARAMETER_ERROR
}
