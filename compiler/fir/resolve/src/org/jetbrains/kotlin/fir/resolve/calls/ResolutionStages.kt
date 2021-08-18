/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.calls

import org.jetbrains.kotlin.descriptors.DeprecationLevelValue
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirVisibilityChecker
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.isInfix
import org.jetbrains.kotlin.fir.declarations.utils.isOperator
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.references.FirSuperReference
import org.jetbrains.kotlin.fir.resolve.inference.*
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.scopes.FirUnstableSmartcastTypeScope
import org.jetbrains.kotlin.fir.symbols.SyntheticSymbol
import org.jetbrains.kotlin.fir.symbols.ensureResolved
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.typeContext
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.visibilityChecker
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind.*
import org.jetbrains.kotlin.resolve.calls.tower.CandidateApplicability
import org.jetbrains.kotlin.types.AbstractNullabilityChecker

abstract class ResolutionStage {
    abstract suspend fun check(candidate: Candidate, callInfo: CallInfo, sink: CheckerSink, context: ResolutionContext)
}

abstract class CheckerStage : ResolutionStage()

internal object CheckExplicitReceiverConsistency : ResolutionStage() {
    override suspend fun check(candidate: Candidate, callInfo: CallInfo, sink: CheckerSink, context: ResolutionContext) {
        val receiverKind = candidate.explicitReceiverKind
        val explicitReceiver = callInfo.explicitReceiver
        // TODO: add invoke cases
        when (receiverKind) {
            NO_EXPLICIT_RECEIVER -> {
                if (explicitReceiver != null && explicitReceiver !is FirResolvedQualifier && !explicitReceiver.isSuperReferenceExpression()) {
                    return sink.yieldDiagnostic(InapplicableWrongReceiver(actualType = explicitReceiver.typeRef.coneTypeSafe()))
                }
            }
            EXTENSION_RECEIVER, DISPATCH_RECEIVER -> {
                if (explicitReceiver == null) {
                    return sink.yieldDiagnostic(InapplicableWrongReceiver())
                }
            }
            BOTH_RECEIVERS -> {
                if (explicitReceiver == null) {
                    return sink.yieldDiagnostic(InapplicableWrongReceiver())
                }
                // Here we should also check additional invoke receiver
            }
        }
    }
}

object CheckExtensionReceiver : ResolutionStage() {
    override suspend fun check(candidate: Candidate, callInfo: CallInfo, sink: CheckerSink, context: ResolutionContext) {
        val expectedReceiverType = candidate.getReceiverType(context) ?: return

        val argumentExtensionReceiverValue = candidate.extensionReceiverValue ?: return
        val expectedType = candidate.substitutor.substituteOrSelf(expectedReceiverType.type)
        val argumentType = captureFromTypeParameterUpperBoundIfNeeded(
            argumentType = argumentExtensionReceiverValue.type,
            expectedType = expectedType,
            session = context.session
        )
        candidate.resolvePlainArgumentType(
            candidate.csBuilder,
            argumentExtensionReceiverValue.receiverExpression,
            argumentType = argumentType,
            expectedType = expectedType,
            sink = sink,
            context = context,
            isReceiver = true,
            isDispatch = false,
        )

        sink.yieldIfNeed()
    }

    private fun Candidate.getReceiverType(context: ResolutionContext): ConeKotlinType? {
        val callableSymbol = symbol as? FirCallableSymbol<*> ?: return null
        val callable = callableSymbol.fir
        val receiverType = callable.receiverTypeRef?.coneType
        if (receiverType != null) return receiverType
        val returnTypeRef = callable.returnTypeRef as? FirResolvedTypeRef ?: return null
        if (!returnTypeRef.type.isExtensionFunctionType(context.session)) return null
        return (returnTypeRef.type.typeArguments.firstOrNull() as? ConeKotlinTypeProjection)?.type
    }
}

object CheckDispatchReceiver : ResolutionStage() {
    override suspend fun check(candidate: Candidate, callInfo: CallInfo, sink: CheckerSink, context: ResolutionContext) {
        val explicitReceiverExpression = callInfo.explicitReceiver
        if (explicitReceiverExpression.isSuperCall()) {
            val status = candidate.symbol.fir as? FirMemberDeclaration
            if (status?.modality == Modality.ABSTRACT) {
                sink.reportDiagnostic(ResolvedWithLowPriority)
            }
        }

        val dispatchReceiverValueType = candidate.dispatchReceiverValue?.type ?: return
        val isReceiverNullable = !AbstractNullabilityChecker.isSubtypeOfAny(context.session.typeContext, dispatchReceiverValueType)

        val isCandidateFromUnstableSmartcast =
            (candidate.originScope as? FirUnstableSmartcastTypeScope)?.isSymbolFromUnstableSmartcast(candidate.symbol) == true

        if (explicitReceiverExpression is FirExpressionWithSmartcast &&
            !explicitReceiverExpression.isStable &&
            (isCandidateFromUnstableSmartcast || (isReceiverNullable && !explicitReceiverExpression.smartcastType.canBeNull))
        ) {
            sink.yieldDiagnostic(UnstableSmartCast(explicitReceiverExpression, explicitReceiverExpression.smartcastType.coneType))
        } else if (isReceiverNullable) {
            sink.yieldDiagnostic(UnsafeCall(dispatchReceiverValueType))
        }
    }
}

private fun FirExpression?.isSuperCall(): Boolean {
    if (this !is FirQualifiedAccessExpression) return false
    return calleeReference is FirSuperReference
}

private fun FirExpression.isSuperReferenceExpression(): Boolean {
    return if (this is FirQualifiedAccessExpression) {
        val calleeReference = calleeReference
        calleeReference is FirSuperReference
    } else false
}

internal object MapArguments : ResolutionStage() {
    override suspend fun check(candidate: Candidate, callInfo: CallInfo, sink: CheckerSink, context: ResolutionContext) {
        val symbol = candidate.symbol as? FirFunctionSymbol<*> ?: return sink.reportDiagnostic(HiddenCandidate)
        val function = symbol.fir

        val mapping = context.bodyResolveComponents.mapArguments(callInfo.arguments, function, candidate.originScope)
        candidate.argumentMapping = mapping.toArgumentToParameterMapping()
        candidate.numDefaults = mapping.numDefaults()

        mapping.diagnostics.forEach(sink::reportDiagnostic)
        sink.yieldIfNeed()
    }
}

internal object CheckArguments : CheckerStage() {
    override suspend fun check(candidate: Candidate, callInfo: CallInfo, sink: CheckerSink, context: ResolutionContext) {
        candidate.symbol.ensureResolved(FirResolvePhase.STATUS)
        val argumentMapping =
            candidate.argumentMapping ?: error("Argument should be already mapped while checking arguments!")
        for (argument in callInfo.arguments) {
            val parameter = argumentMapping[argument]
            candidate.resolveArgument(
                callInfo,
                argument,
                parameter,
                isReceiver = false,
                sink = sink,
                context = context
            )
        }
        if (candidate.system.hasContradiction && callInfo.arguments.isNotEmpty()) {
            sink.yieldDiagnostic(InapplicableCandidate)
        }
    }
}

internal object EagerResolveOfCallableReferences : CheckerStage() {
    override suspend fun check(candidate: Candidate, callInfo: CallInfo, sink: CheckerSink, context: ResolutionContext) {
        if (candidate.postponedAtoms.isEmpty()) return
        for (atom in candidate.postponedAtoms) {
            if (atom is ResolvedCallableReferenceAtom) {
                val (applicability, success) =
                    context.bodyResolveComponents.callResolver.resolveCallableReference(candidate.csBuilder, atom)
                if (!success) {
                    sink.yieldDiagnostic(InapplicableCandidate)
                } else when (applicability) {
                    CandidateApplicability.RESOLVED_NEED_PRESERVE_COMPATIBILITY ->
                        candidate.addDiagnostic(LowerPriorityToPreserveCompatibilityDiagnostic)
                    else -> {
                    }
                }
            }
        }
    }
}

internal object DiscriminateSynthetics : CheckerStage() {
    override suspend fun check(candidate: Candidate, callInfo: CallInfo, sink: CheckerSink, context: ResolutionContext) {
        if (candidate.symbol is SyntheticSymbol) {
            sink.reportDiagnostic(ResolvedWithLowPriority)
        }
    }
}

internal object CheckVisibility : CheckerStage() {
    override suspend fun check(candidate: Candidate, callInfo: CallInfo, sink: CheckerSink, context: ResolutionContext) {
        val visibilityChecker = callInfo.session.visibilityChecker
        val symbol = candidate.symbol
        val declaration = symbol.fir
        if (declaration is FirMemberDeclaration) {
            if (!checkVisibility(declaration, sink, candidate, visibilityChecker)) {
                return
            }
        }

        if (declaration is FirConstructor) {
            // TODO: Should be some other form
            val classSymbol = declaration.returnTypeRef.coneTypeUnsafe<ConeClassLikeType>().lookupTag.toSymbol(context.session)

            if (classSymbol is FirRegularClassSymbol) {
                if (classSymbol.fir.classKind.isSingleton) {
                    sink.yieldDiagnostic(HiddenCandidate)
                }
                checkVisibility(classSymbol.fir, sink, candidate, visibilityChecker)
            }
        }
    }

    private suspend fun <T : FirMemberDeclaration> checkVisibility(
        declaration: T,
        sink: CheckerSink,
        candidate: Candidate,
        visibilityChecker: FirVisibilityChecker
    ): Boolean {
        if (!visibilityChecker.isVisible(declaration, candidate)) {
            sink.yieldDiagnostic(HiddenCandidate)
            return false
        }
        return true
    }
}

internal object CheckLowPriorityInOverloadResolution : CheckerStage() {
    private val LOW_PRIORITY_IN_OVERLOAD_RESOLUTION_CLASS_ID: ClassId =
        ClassId(FqName("kotlin.internal"), Name.identifier("LowPriorityInOverloadResolution"))

    override suspend fun check(candidate: Candidate, callInfo: CallInfo, sink: CheckerSink, context: ResolutionContext) {
        val annotations = when (val fir = candidate.symbol.fir) {
            is FirSimpleFunction -> fir.annotations
            is FirProperty -> fir.annotations
            else -> return
        }

        val hasLowPriorityAnnotation = annotations.any {
            val lookupTag = it.annotationTypeRef.coneTypeSafe<ConeClassLikeType>()?.lookupTag ?: return@any false
            lookupTag.classId == LOW_PRIORITY_IN_OVERLOAD_RESOLUTION_CLASS_ID
        }

        if (hasLowPriorityAnnotation) {
            sink.reportDiagnostic(ResolvedWithLowPriority)
        }
    }
}

internal object PostponedVariablesInitializerResolutionStage : ResolutionStage() {

    override suspend fun check(candidate: Candidate, callInfo: CallInfo, sink: CheckerSink, context: ResolutionContext) {
        val argumentMapping = candidate.argumentMapping ?: return
        // TODO: convert type argument mapping to map [FirTypeParameterSymbol, FirTypedProjection?]
        if (candidate.typeArgumentMapping is TypeArgumentMapping.Mapped) return
        for (parameter in argumentMapping.values) {
            if (!parameter.hasBuilderInferenceAnnotation()) continue
            val type = parameter.returnTypeRef.coneType
            val receiverType = type.receiverType(callInfo.session) ?: continue

            for (freshVariable in candidate.freshVariables) {
                if (candidate.csBuilder.isPostponedTypeVariable(freshVariable)) continue
                if (freshVariable !is ConeTypeParameterBasedTypeVariable) continue
                val typeParameterSymbol = freshVariable.typeParameterSymbol
                val typeHasVariable = receiverType.contains {
                    (it as? ConeTypeParameterType)?.lookupTag?.typeParameterSymbol == typeParameterSymbol
                }
                if (typeHasVariable) {
                    candidate.csBuilder.markPostponedVariable(freshVariable)
                }
            }
        }
    }
}

internal object CheckCallModifiers : CheckerStage() {
    override suspend fun check(candidate: Candidate, callInfo: CallInfo, sink: CheckerSink, context: ResolutionContext) {
        if (callInfo.callSite is FirFunctionCall) {
            val functionSymbol = candidate.symbol as? FirNamedFunctionSymbol ?: return
            when {
                callInfo.callSite.origin == FirFunctionCallOrigin.Infix && !functionSymbol.fir.isInfix ->
                    sink.reportDiagnostic(InfixCallOfNonInfixFunction(functionSymbol))
                callInfo.callSite.origin == FirFunctionCallOrigin.Operator && !functionSymbol.fir.isOperator ->
                    sink.reportDiagnostic(OperatorCallOfNonOperatorFunction(functionSymbol))
                callInfo.isImplicitInvoke && !functionSymbol.fir.isOperator ->
                    sink.reportDiagnostic(OperatorCallOfNonOperatorFunction(functionSymbol))
            }
        }
    }
}

internal object CheckDeprecatedSinceKotlin : ResolutionStage() {
    override suspend fun check(candidate: Candidate, callInfo: CallInfo, sink: CheckerSink, context: ResolutionContext) {
        val symbol = candidate.symbol as? FirCallableSymbol<*> ?: return
        val deprecation = symbol.getDeprecation(callInfo.callSite)
        if (deprecation != null && deprecation.level == DeprecationLevelValue.HIDDEN) {
            sink.yieldDiagnostic(HiddenCandidate)
        }
    }
}
