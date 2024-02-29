/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.transformers

import org.jetbrains.kotlin.analysis.low.level.api.fir.api.targets.LLFirResolveTarget
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.throwUnexpectedFirElementError
import org.jetbrains.kotlin.fir.FirElementWithResolveState
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.isConst
import org.jetbrains.kotlin.fir.resolve.transformers.FirConstantEvaluationBodyResolveTransformer

internal object LLFirConstantEvaluationLazyResolver : LLFirLazyResolver(FirResolvePhase.CONSTANT_EVALUATION) {
    override fun createTargetResolver(target: LLFirResolveTarget): LLFirTargetResolver = LLFirConstantEvaluationTargetResolver(target)

    override fun phaseSpecificCheckIsResolved(target: FirElementWithResolveState) {}
}

/**
 * This resolver is responsible for [CONSTANT_EVALUATION][FirResolvePhase.CONSTANT_EVALUATION] phase.
 *
 * This resolver doesn't do anything yet (see KT-64151).
 *
 * @see FirResolvePhase.CONSTANT_EVALUATION
 */
private class LLFirConstantEvaluationTargetResolver(resolveTarget: LLFirResolveTarget) : LLFirAbstractBodyTargetResolver(
    resolveTarget,
    FirResolvePhase.CONSTANT_EVALUATION,
) {
    override val transformer = FirConstantEvaluationBodyResolveTransformer(
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
            is FirConstructor -> {
                if (target.symbol.isAnnotationConstructor(resolveTargetSession)) {
                    resolve(target, BodyStateKeepers.CONSTRUCTOR)
                }
            }
            is FirRegularClass, is FirTypeAlias, is FirFile, is FirCodeFragment, is FirAnonymousInitializer, is FirDanglingModifierList,
            is FirEnumEntry, is FirErrorProperty, is FirScript, is FirFunction, is FirField
            -> {
                // No necessary to resolve these declarations
            }
            else -> throwUnexpectedFirElementError(target)
        }
    }
}
