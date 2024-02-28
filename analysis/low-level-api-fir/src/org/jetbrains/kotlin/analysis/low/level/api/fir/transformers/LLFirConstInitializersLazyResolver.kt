/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.transformers

import org.jetbrains.kotlin.analysis.low.level.api.fir.api.targets.LLFirResolveTarget
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.throwUnexpectedFirElementError
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.llFirSession
import org.jetbrains.kotlin.analysis.low.level.api.fir.util.checkDefaultValueIsResolved
import org.jetbrains.kotlin.analysis.low.level.api.fir.util.checkInitializerIsResolved
import org.jetbrains.kotlin.fir.FirElementWithResolveState
import org.jetbrains.kotlin.fir.FirFileAnnotationsContainer
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.isConst
import org.jetbrains.kotlin.fir.resolve.transformers.FirConstInitializerBodyResolveTransformer

internal object LLFirConstInitializersLazyResolver : LLFirLazyResolver(FirResolvePhase.CONST_INITIALIZERS) {
    override fun createTargetResolver(target: LLFirResolveTarget): LLFirTargetResolver = LLFirConstInitializersTargetResolver(target)

    override fun phaseSpecificCheckIsResolved(target: FirElementWithResolveState) {
        when (target) {
            is FirProperty -> if (target.isConst) {
                checkInitializerIsResolved(target)
            }
            is FirValueParameter -> if (target.containingFunctionSymbol.isAnnotationConstructor(target.llFirSession)) {
                checkDefaultValueIsResolved(target)
            }
        }
    }
}

private class LLFirConstInitializersTargetResolver(resolveTarget: LLFirResolveTarget) : LLFirAbstractBodyTargetResolver(
    resolveTarget,
    FirResolvePhase.CONST_INITIALIZERS,
) {
    override val transformer = FirConstInitializerBodyResolveTransformer(
        resolveTargetSession,
        resolveTargetScopeSession,
    )

    override fun doLazyResolveUnderLock(target: FirElementWithResolveState) {
        when (target) {
            is FirProperty -> {
                if (target.isConst) {
                    resolve(target, BodyStateKeepers.PROPERTY)
                }
            }
            is FirValueParameter -> {
                if (target.containingFunctionSymbol.isAnnotationConstructor(resolveTargetSession)) {
                    resolve(target, BodyStateKeepers.VARIABLE)
                }
            }
            is FirRegularClass, is FirTypeAlias, is FirFile, is FirCodeFragment, is FirAnonymousInitializer, is FirDanglingModifierList,
            is FirFileAnnotationsContainer, is FirEnumEntry, is FirErrorProperty, is FirScript, is FirFunction, is FirField
            -> {
                // No necessary to resolve these declarations
            }
            else -> throwUnexpectedFirElementError(target)
        }
    }
}
