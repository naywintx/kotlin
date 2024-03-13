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
import org.jetbrains.kotlin.fir.resolve.ResolutionMode
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirBodyResolveTransformer
import org.jetbrains.kotlin.fir.resolve.transformers.compileTimeEvaluator
import org.jetbrains.kotlin.fir.visitors.transformSingle

internal object LLFirConstantEvaluationLazyResolver : LLFirLazyResolver(FirResolvePhase.CONSTANT_EVALUATION) {
    override fun createTargetResolver(target: LLFirResolveTarget): LLFirTargetResolver = LLFirConstantEvaluationTargetResolver(target)

    override fun phaseSpecificCheckIsResolved(target: FirElementWithResolveState) {}
}

/**
 * This resolver is responsible for [CONSTANT_EVALUATION][FirResolvePhase.CONSTANT_EVALUATION] phase.
 *
 * @see FirResolvePhase.CONSTANT_EVALUATION
 */
private class LLFirConstantEvaluationTargetResolver(resolveTarget: LLFirResolveTarget) : LLFirAbstractBodyTargetResolver(
    resolveTarget,
    FirResolvePhase.CONSTANT_EVALUATION,
) {
    override val transformer = object : FirBodyResolveTransformer(
        session = resolveTargetSession,
        phase = resolverPhase,
        implicitTypeOnly = true,
        scopeSession = resolveTargetScopeSession,
    ) {
        override fun transformProperty(property: FirProperty, data: ResolutionMode): FirProperty {
            return property.transformSingle(session.compileTimeEvaluator, null)
        }
    }

    override fun doLazyResolveUnderLock(target: FirElementWithResolveState) {
        when (target) {
            is FirProperty -> {
                if (target.isConst) {
                    resolve(target, BodyStateKeepers.PROPERTY)
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
