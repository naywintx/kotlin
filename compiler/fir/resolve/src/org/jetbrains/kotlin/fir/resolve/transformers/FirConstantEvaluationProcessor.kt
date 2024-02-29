/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.transformers

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.isLocal
import org.jetbrains.kotlin.fir.resolve.ResolutionMode
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirBodyResolveTransformer
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.withFileAnalysisExceptionWrapping

@OptIn(AdapterForResolveProcessor::class)
class FirConstantEvaluationProcessor(
    session: FirSession,
    scopeSession: ScopeSession
) : FirTransformerBasedResolveProcessor(session, scopeSession, FirResolvePhase.CONSTANT_EVALUATION) {
    override val transformer = FirConstantEvaluationTransformerAdapter(session, scopeSession)
}

@AdapterForResolveProcessor
class FirConstantEvaluationTransformerAdapter(session: FirSession, scopeSession: ScopeSession) : FirTransformer<Any?>() {
    private val transformer = FirConstantEvaluationBodyResolveTransformer(session, scopeSession)

    override fun <E : FirElement> transformElement(element: E, data: Any?): E {
        error("Should only be called via transformFile()")
    }

    override fun transformFile(file: FirFile, data: Any?): FirFile {
        return withFileAnalysisExceptionWrapping(file) {
            file.transform(transformer, ResolutionMode.ContextIndependent)
        }
    }
}

class FirConstantEvaluationBodyResolveTransformer(
    session: FirSession,
    scopeSession: ScopeSession,
) : FirBodyResolveTransformer(
    session,
    FirResolvePhase.CONSTANT_EVALUATION,
    implicitTypeOnly = true,
    scopeSession,
) {
    private val firCompileTimeConstantEvaluator = FirCompileTimeConstantEvaluator(session)

    // This is required to avoid unnecessary transformation. For example, avoid visiting lambdas in annotation arguments.
    override fun transformDeclarationContent(
        declaration: FirDeclaration,
        data: ResolutionMode,
    ): FirDeclaration = if (declaration is FirRegularClass && !declaration.isLocal) {
        declaration.transformDeclarations(this, data)
    } else {
        super.transformDeclarationContent(declaration, data)
    }

    override fun transformProperty(property: FirProperty, data: ResolutionMode): FirProperty {
        property.accept(firCompileTimeConstantEvaluator, null)
        return property
    }

    override fun transformConstructor(constructor: FirConstructor, data: ResolutionMode): FirConstructor {
        constructor.accept(firCompileTimeConstantEvaluator, null)
        return constructor
    }
}
