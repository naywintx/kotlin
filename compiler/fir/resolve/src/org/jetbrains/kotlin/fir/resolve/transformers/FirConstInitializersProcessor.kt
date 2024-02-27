/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.transformers

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.resolve.ResolutionMode
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirBodyResolveTransformer
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.transformSingle
import org.jetbrains.kotlin.fir.withFileAnalysisExceptionWrapping

@OptIn(AdapterForResolveProcessor::class)
class FirConstInitializersProcessor(
    session: FirSession,
    scopeSession: ScopeSession
) : FirTransformerBasedResolveProcessor(session, scopeSession, FirResolvePhase.CONST_INITIALIZERS) {
    override val transformer = FirConstInitializersTransformerAdapter(session, scopeSession)
}

@AdapterForResolveProcessor
class FirConstInitializersTransformerAdapter(session: FirSession, scopeSession: ScopeSession) : FirTransformer<Any?>() {
    private val transformer = FirConstInitializerBodyResolveTransformer(session, scopeSession)

    override fun <E : FirElement> transformElement(element: E, data: Any?): E {
        error("Should only be called via transformFile()")
    }

    override fun transformFile(file: FirFile, data: Any?): FirFile {
        return withFileAnalysisExceptionWrapping(file) {
            file.transform(transformer, ResolutionMode.ContextIndependent)
        }
    }
}

class FirConstInitializerBodyResolveTransformer(
    session: FirSession,
    scopeSession: ScopeSession,
) : FirBodyResolveTransformer(
    session,
    FirResolvePhase.CONST_INITIALIZERS,
    implicitTypeOnly = false,
    scopeSession,
) {
    override fun transformProperty(property: FirProperty, data: ResolutionMode): FirProperty {
        return property.transformSingle(session.compileTimeEvaluator, FirEvaluationMode.ONLY_NECESSARY)
    }

    override fun transformConstructor(constructor: FirConstructor, data: ResolutionMode): FirConstructor {
        return constructor.transformSingle(session.compileTimeEvaluator, FirEvaluationMode.ONLY_NECESSARY)
    }
}
