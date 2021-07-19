/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.expression

import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.extractClassesFromArgument
import org.jetbrains.kotlin.fir.analysis.checkers.outerClassSymbol
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.analysis.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.analysis.diagnostics.reportOn
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.resolve.calculateOwnExperimentalities
import org.jetbrains.kotlin.fir.resolve.loadExperimentalities
import org.jetbrains.kotlin.fir.resolve.toFirRegularClass
import org.jetbrains.kotlin.fir.scopes.ProcessorAction
import org.jetbrains.kotlin.fir.scopes.processDirectlyOverriddenFunctions
import org.jetbrains.kotlin.fir.scopes.processDirectlyOverriddenProperties
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.ensureResolved
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.checkers.Experimentality
import org.jetbrains.kotlin.resolve.checkers.OptInNames
import org.jetbrains.kotlin.utils.SmartSet

object FirOptInUsageBaseChecker {
    internal fun MutableSet<Experimentality>.addExperimentalitiesFromTypeArguments(
        context: CheckerContext,
        typeArguments: List<FirTypeProjection>
    ) {
        if (typeArguments.isEmpty()) return
        typeArguments.forEach {
            val coneArgument = it.toConeTypeProjection()
            if (!coneArgument.isStarProjection) {
                addAll(coneArgument.type?.loadExperimentalities(context.session).orEmpty())
            }
        }
    }

    internal fun MutableSet<Experimentality>.addExperimentalitiesFromConeArguments(
        context: CheckerContext,
        typeArguments: List<ConeTypeProjection>
    ) {
        if (typeArguments.isEmpty()) return
        typeArguments.forEach {
            if (!it.isStarProjection) {
                addAll(it.type?.loadExperimentalities(context.session).orEmpty())
            }
        }
    }

    @OptIn(SymbolInternals::class)
    internal fun FirBasedSymbol<*>.loadExperimentalities(
        context: CheckerContext,
        fromSetter: Boolean,
    ): SmartSet<Experimentality> {
        ensureResolved(FirResolvePhase.STATUS)
        val result = SmartSet.create<Experimentality>()
        val fir = fir as? FirAnnotatedDeclaration ?: return result
        val session = context.session
        if (fir is FirCallableDeclaration && fir.isSubstitutionOrIntersectionOverride) {
            val parentClassScope = fir.containingClass()?.toFirRegularClass(session)?.unsubstitutedScope(context)
            if (fir is FirSimpleFunction) {
                parentClassScope?.processDirectlyOverriddenFunctions(fir.symbol) {
                    result.addAll(it.loadExperimentalities(context, fromSetter = false))
                    ProcessorAction.NEXT
                }
            } else if (fir is FirProperty) {
                parentClassScope?.processDirectlyOverriddenProperties(fir.symbol) {
                    result.addAll(it.loadExperimentalities(context, fromSetter))
                    ProcessorAction.NEXT
                }
            }
        }

        if (fir is FirMemberDeclaration) {
            result.addAll(fir.experimentalities)
        }
        if (fromSetter && fir is FirProperty) {
            result.addAll(fir.calculateOwnExperimentalities(context.session, fromSetter = true))
            result.addAll(fir.setter?.calculateOwnExperimentalities(context.session, fromSetter = true).orEmpty())
        }

        if (fir.origin == FirDeclarationOrigin.Library || fir.origin == FirDeclarationOrigin.Enhancement) {
            result.addAll(loadExperimentalitiesFromDeserialized(context))
        }

        return result
    }

    @OptIn(SymbolInternals::class)
    private fun FirBasedSymbol<*>.loadExperimentalitiesFromDeserialized(context: CheckerContext): SmartSet<Experimentality> {
        val result = SmartSet.create<Experimentality>()
        val fir = fir as? FirAnnotatedDeclaration ?: return result
        val session = context.session
        if (fir is FirCallableDeclaration) {
            result.addAll(fir.returnTypeRef.coneTypeSafe<ConeKotlinType>().loadExperimentalities(context.session))
            result.addAll(fir.receiverTypeRef?.coneTypeSafe<ConeKotlinType>().loadExperimentalities(context.session))
            if (fir is FirSimpleFunction) {
                fir.valueParameters.forEach {
                    result.addAll(it.returnTypeRef.coneTypeSafe<ConeKotlinType>().loadExperimentalities(context.session))
                }
            }
            val parentClass = fir.containingClass()?.toFirRegularClass(session)
            if (parentClass != null) {
                result.addAll(parentClass.experimentalities)
            }
        } else if (fir is FirRegularClass) {
            val parentClassSymbol = fir.symbol.outerClassSymbol(context)
            if (parentClassSymbol is FirRegularClassSymbol) {
                result.addAll(parentClassSymbol.fir.experimentalities)
            }
        }
        return result
    }

    internal fun reportNotAcceptedExperimentalities(
        experimentalities: Collection<Experimentality>,
        element: FirElement,
        context: CheckerContext,
        reporter: DiagnosticReporter
    ) {
        for ((annotationFqName, severity, message) in experimentalities) {
            if (!isExperimentalityAcceptableInContext(annotationFqName, context)) {
                val diagnostic = when (severity) {
                    Experimentality.Severity.WARNING -> FirErrors.EXPERIMENTAL_API_USAGE
                    Experimentality.Severity.ERROR -> FirErrors.EXPERIMENTAL_API_USAGE_ERROR
                }
                val reportedMessage = message ?: when (severity) {
                    Experimentality.Severity.WARNING -> "This declaration is experimental and its usage should be marked"
                    Experimentality.Severity.ERROR -> "This declaration is experimental and its usage must be marked"
                }
                reporter.reportOn(element.source, diagnostic, annotationFqName, reportedMessage, context)
            }
        }
    }

    private fun isExperimentalityAcceptableInContext(
        annotationFqName: FqName,
        context: CheckerContext
    ): Boolean {
        val languageVersionSettings = context.session.languageVersionSettings
        val fqNameAsString = annotationFqName.asString()
        if (fqNameAsString in languageVersionSettings.getFlag(AnalysisFlags.useExperimental)) {
            return true
        }
        for (annotationContainer in context.annotationContainers) {
            if (annotationContainer.isExperimentalityAcceptable(annotationFqName)) {
                return true
            }
        }
        return false
    }

    private fun FirAnnotationContainer.isExperimentalityAcceptable(annotationFqName: FqName): Boolean {
        return getAnnotationByFqName(annotationFqName) != null || isAnnotatedWithUseExperimentalOf(annotationFqName)
    }

    private fun FirAnnotationContainer.isAnnotatedWithUseExperimentalOf(annotationFqName: FqName): Boolean {
        for (annotation in annotations) {
            val coneType = annotation.annotationTypeRef.coneType as? ConeClassLikeType
            if (coneType?.lookupTag?.classId != OptInNames.OPT_IN_CLASS_ID) {
                continue
            }
            val annotationClasses = annotation.findArgumentByName(OptInNames.USE_EXPERIMENTAL_ANNOTATION_CLASS) ?: continue
            if (annotationClasses.extractClassesFromArgument().any {
                    it.classId.asSingleFqName() == annotationFqName
                }
            ) {
                return true
            }
        }
        return false
    }
}