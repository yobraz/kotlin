/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.frontend.api.fir.components

import org.jetbrains.kotlin.descriptors.Deprecation
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.fir.declarations.getDeprecationForCallSite
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.idea.asJava.getJvmNameFromAnnotation
import org.jetbrains.kotlin.idea.frontend.api.components.KtSymbolInfoProvider
import org.jetbrains.kotlin.idea.frontend.api.fir.KtFirAnalysisSession
import org.jetbrains.kotlin.idea.frontend.api.fir.symbols.KtFirBackingFieldSymbol
import org.jetbrains.kotlin.idea.frontend.api.fir.symbols.KtFirSymbol
import org.jetbrains.kotlin.idea.frontend.api.fir.symbols.KtFirSyntheticJavaPropertySymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtSymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtSyntheticJavaPropertySymbol
import org.jetbrains.kotlin.idea.frontend.api.tokens.ValidityToken
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.Name

internal class KtFirSymbolInfoProvider(
    override val analysisSession: KtFirAnalysisSession,
    override val token: ValidityToken,
) : KtSymbolInfoProvider(), KtFirAnalysisSessionComponent {
    override fun getDeprecation(symbol: KtSymbol): Deprecation? {
        if (symbol is KtFirBackingFieldSymbol) return null
        require(symbol is KtFirSymbol<*>)
        return symbol.firRef.withFir {
            val firSymbol = it.symbol
            if (firSymbol is FirPropertySymbol) {
                firSymbol.getDeprecationForCallSite(AnnotationUseSiteTarget.PROPERTY)
            } else {
                firSymbol.getDeprecationForCallSite()
            }
        }
    }

    override fun getGetterDeprecation(symbol: KtPropertySymbol): Deprecation? {
        require(symbol is KtFirSymbol<*>)
        return symbol.firRef.withFir {
            it.symbol.getDeprecationForCallSite(AnnotationUseSiteTarget.PROPERTY_GETTER, AnnotationUseSiteTarget.PROPERTY)
        }
    }

    override fun getSetterDeprecation(symbol: KtPropertySymbol): Deprecation? {
        require(symbol is KtFirSymbol<*>)
        return symbol.firRef.withFir {
            it.symbol.getDeprecationForCallSite(AnnotationUseSiteTarget.PROPERTY_SETTER, AnnotationUseSiteTarget.PROPERTY)
        }
    }

    override fun getJavaGetterName(symbol: KtPropertySymbol): Name {
        require(symbol is KtFirSymbol<*>)
        if (symbol is KtFirSyntheticJavaPropertySymbol) {
            return symbol.firRef.withFir { it.getter.delegate.name }
        }
        val jvmName = symbol.getJvmNameFromAnnotation(AnnotationUseSiteTarget.PROPERTY_GETTER)
        return Name.identifier(jvmName ?: JvmAbi.getterName(symbol.name.identifier))
    }

    override fun getJavaSetterName(symbol: KtPropertySymbol): Name? {
        require(symbol is KtFirSymbol<*>)
        if (symbol is KtFirSyntheticJavaPropertySymbol) {
            symbol.firRef.withFir { it.setter?.delegate?.name }
        }
        return if (symbol.isVal) null
        else {
            val jvmName = symbol.getJvmNameFromAnnotation(AnnotationUseSiteTarget.PROPERTY_SETTER)
            Name.identifier(jvmName ?: JvmAbi.setterName(symbol.name.identifier))
        }
    }
}