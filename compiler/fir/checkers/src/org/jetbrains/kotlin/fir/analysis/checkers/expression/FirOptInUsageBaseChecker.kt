/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.expression

import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.analysis.checkers.*
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.analysis.diagnostics.reportOn
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.FirConstExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toFirRegularClass
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.scopes.ProcessorAction
import org.jetbrains.kotlin.fir.scopes.processDirectlyOverriddenFunctions
import org.jetbrains.kotlin.fir.scopes.processDirectlyOverriddenProperties
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.checkers.Experimentality
import org.jetbrains.kotlin.resolve.checkers.OptInNames
import org.jetbrains.kotlin.utils.SmartSet
import org.jetbrains.kotlin.utils.addIfNotNull

object FirOptInUsageBaseChecker {
    internal fun loadExperimentalitiesFromTypeArguments(
        context: CheckerContext,
        typeArguments: List<FirTypeProjection>
    ): Set<Experimentality> {
        if (typeArguments.isEmpty()) return emptySet()
        return loadExperimentalitiesFromConeArguments(context, typeArguments.map { it.toConeTypeProjection() })
    }

    internal fun loadExperimentalitiesFromConeArguments(
        context: CheckerContext,
        typeArguments: List<ConeTypeProjection>
    ): Set<Experimentality> {
        if (typeArguments.isEmpty()) return emptySet()
        return typeArguments.flatMapTo(mutableSetOf()) {
            if (it.isStarProjection) emptySet()
            else it.type?.loadExperimentalities(context).orEmpty()
        }
    }

    internal fun FirAnnotatedDeclaration.loadExperimentalities(
        context: CheckerContext,
        visited: MutableSet<FirAnnotatedDeclaration> = mutableSetOf(),
        fromSetter: Boolean = false,
    ): Set<Experimentality> {
        if (!visited.add(this)) return emptySet()
        val result = SmartSet.create<Experimentality>()
        val session = context.session
        if (this is FirCallableMemberDeclaration<*>) {
            val parentClass = containingClass()?.toFirRegularClass(session)
            if (this.isSubstitutionOrIntersectionOverride) {
                val parentClassScope = parentClass?.unsubstitutedScope(context)
                if (this is FirSimpleFunction) {
                    parentClassScope?.processDirectlyOverriddenFunctions(symbol) {
                        result.addAll(it.fir.loadExperimentalities(context, visited))
                        ProcessorAction.NEXT
                    }
                } else if (this is FirProperty) {
                    parentClassScope?.processDirectlyOverriddenProperties(symbol) {
                        result.addAll(it.fir.loadExperimentalities(context, visited, fromSetter))
                        ProcessorAction.NEXT
                    }
                }
            }
            if (this !is FirConstructor) {
                // Note: coneType here crashes on overridden members
                result.addAll(returnTypeRef.coneTypeSafe<ConeKotlinType>().loadExperimentalities(context, visited))
                result.addAll(receiverTypeRef?.coneTypeSafe<ConeKotlinType>().loadExperimentalities(context, visited))
                if (this is FirSimpleFunction) {
                    valueParameters.forEach {
                        result.addAll(it.returnTypeRef.coneTypeSafe<ConeKotlinType>().loadExperimentalities(context, visited))
                    }
                }
            }
            if (parentClass != null) {
                result.addAll(parentClass.loadExperimentalities(context, visited))
            }
            if (fromSetter && this is FirProperty) {
                result.addAll(setter?.loadExperimentalities(context, visited).orEmpty())
            }
        } else if (this is FirRegularClass && !this.isLocal) {
            val parentClass = this.outerClass(context)
            if (parentClass != null) {
                result.addAll(parentClass.loadExperimentalities(context, visited))
            }
        }

        for (annotation in annotations) {
            val annotationType = annotation.annotationTypeRef.coneTypeSafe<ConeClassLikeType>()
            if (annotation.useSiteTarget != AnnotationUseSiteTarget.PROPERTY_SETTER || fromSetter) {
                result.addIfNotNull(
                    annotationType?.fullyExpandedType(session)?.lookupTag?.toFirRegularClass(
                        session
                    )?.loadExperimentalityForMarkerAnnotation()
                )
            }
        }

        if (this is FirTypeAlias) {
            result.addAll(expandedTypeRef.coneType.loadExperimentalities(context, visited))
        }

        if (getAnnotationByFqName(OptInNames.WAS_EXPERIMENTAL_FQ_NAME) != null) {
            val accessibility = checkSinceKotlinVersionAccessibility(context)
            if (accessibility is FirSinceKotlinAccessibility.NotAccessibleButWasExperimental) {
                result.addAll(accessibility.markerClasses.mapNotNull { it.fir.loadExperimentalityForMarkerAnnotation() })
            }
        }

        // TODO: getAnnotationsOnContainingModule

        return result
    }

    private fun ConeKotlinType?.loadExperimentalities(
        context: CheckerContext,
        visited: MutableSet<FirAnnotatedDeclaration> = mutableSetOf()
    ): Set<Experimentality> =
        when (this) {
            !is ConeClassLikeType -> emptySet()
            else -> {
                val expandedType = fullyExpandedType(context.session)
                if (this === expandedType) {
                    expandedType.lookupTag.toFirRegularClass(context.session)?.loadExperimentalities(
                        context, visited
                    ).orEmpty() + typeArguments.flatMap {
                        if (it.isStarProjection) emptySet()
                        else it.type?.loadExperimentalities(context, visited).orEmpty()
                    }
                } else {
                    lookupTag.toSymbol(context.session)?.fir?.loadExperimentalities(
                        context, visited
                    ).orEmpty() + expandedType.typeArguments.flatMap {
                        if (it.isStarProjection) emptySet()
                        else it.type?.loadExperimentalities(context, visited).orEmpty()
                    }
                }
            }
        }

    internal fun FirRegularClass.loadExperimentalityForMarkerAnnotation(): Experimentality? {
        val experimental = getAnnotationByFqName(OptInNames.REQUIRES_OPT_IN_FQ_NAME)
            ?: getAnnotationByFqName(OptInNames.OLD_EXPERIMENTAL_FQ_NAME)
            ?: return null

        val levelArgument = experimental.findArgumentByName(LEVEL) as? FirQualifiedAccessExpression
        val levelName = (levelArgument?.calleeReference as? FirResolvedNamedReference)?.name?.asString()
        val level = OptInLevel.values().firstOrNull { it.name == levelName } ?: OptInLevel.DEFAULT
        val message = (experimental.findArgumentByName(MESSAGE) as? FirConstExpression<*>)?.value as? String
        return Experimentality(symbol.classId.asSingleFqName(), level.severity, message)
    }

    internal fun reportNotAcceptedExperimentalities(
        experimentalities: Collection<Experimentality>,
        element: FirElement,
        context: CheckerContext,
        reporter: DiagnosticReporter
    ) {
        for ((annotationFqName, severity, message) in experimentalities) {
            if (!isExperimentalityAcceptableInContext(annotationFqName, element, context)) {
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
        element: FirElement,
        context: CheckerContext
    ): Boolean {
        val languageVersionSettings = context.session.languageVersionSettings
        val fqNameAsString = annotationFqName.asString()
        if (fqNameAsString in languageVersionSettings.getFlag(AnalysisFlags.experimental) ||
            fqNameAsString in languageVersionSettings.getFlag(AnalysisFlags.useExperimental)
        ) {
            return true
        }
        for (annotationContainer in context.annotationContainers) {
            if (annotationContainer.isExperimentalityAcceptable(annotationFqName)) {
                return true
            }
        }
        if (element !is FirAnnotationContainer) return false
        return element.isExperimentalityAcceptable(annotationFqName)
    }

    private fun FirAnnotationContainer.isExperimentalityAcceptable(annotationFqName: FqName): Boolean {
        return getAnnotationByFqName(annotationFqName) != null || isAnnotatedWithUseExperimentalOf(annotationFqName)
    }

    private fun FirAnnotationContainer.isAnnotatedWithUseExperimentalOf(annotationFqName: FqName): Boolean {
        for (annotation in annotations) {
            val coneType = annotation.annotationTypeRef.coneType as? ConeClassLikeType
            if (coneType?.lookupTag?.classId?.asSingleFqName() !in OptInNames.USE_EXPERIMENTAL_FQ_NAMES) {
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

    private val LEVEL = Name.identifier("level")
    private val MESSAGE = Name.identifier("message")

    private enum class OptInLevel(val severity: Experimentality.Severity) {
        WARNING(Experimentality.Severity.WARNING),
        ERROR(Experimentality.Severity.ERROR),
        DEFAULT(Experimentality.DEFAULT_SEVERITY)
    }
}