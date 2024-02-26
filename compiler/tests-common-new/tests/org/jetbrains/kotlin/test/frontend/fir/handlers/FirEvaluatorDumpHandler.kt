/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.frontend.fir.handlers

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.codeMetaInfo.model.ParsedCodeMetaInfo
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.languageVersionSettings
import org.jetbrains.kotlin.fir.resolve.transformers.compileTimeEvaluator
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.visitors.FirVisitor
import org.jetbrains.kotlin.test.frontend.fir.FirOutputArtifact
import org.jetbrains.kotlin.test.model.FrontendKinds
import org.jetbrains.kotlin.test.model.FrontendOutputHandler
import org.jetbrains.kotlin.test.model.TestFile
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.globalMetadataInfoHandler
import org.jetbrains.kotlin.test.services.sourceFileProvider

private const val stopEvaluation = "// STOP_EVALUATION_CHECKS"
private const val startEvaluation = "// START_EVALUATION_CHECKS"

class FirEvaluatorDumpHandler(testServices: TestServices) : FrontendOutputHandler<FirOutputArtifact>(
    testServices,
    FrontendKinds.FIR,
    failureDisablesNextSteps = true,
    doNotRunIfThereWerePreviousFailures = true
) {
    private val globalMetadataInfoHandler
        get() = testServices.globalMetadataInfoHandler

    override fun processModule(module: TestModule, info: FirOutputArtifact) {
        info.partsForDependsOnModules.forEach {
            it.firFiles.forEach { (testFile, firFile) ->
                val intrinsicConstEvaluation = it.session.languageVersionSettings.supportsFeature(LanguageFeature.IntrinsicConstEvaluation)
                if (intrinsicConstEvaluation) {
                    // Ignore this test. Copy the expected result and treat it as actual.
                    val expected = globalMetadataInfoHandler.getExistingMetaInfosForFile(testFile)
                    globalMetadataInfoHandler.addMetadataInfosForFile(testFile, expected)
                } else {
                    processFile(testFile, firFile, it.session)
                }
            }
        }
    }

    override fun processAfterAllModules(someAssertionWasFailed: Boolean) {}

    private fun processFile(testFile: TestFile, firFile: FirFile, session: FirSession) {
        val rangesThatAreNotSupposedToBeRendered = testFile.extractRangesWithoutRender()

        fun render(result: FirLiteralExpression<*>, source: KtSourceElement?) {
            val start = source?.startOffset ?: return
            val end = result.source?.endOffset ?: return
            if (rangesThatAreNotSupposedToBeRendered.any { start >= it.first && start <= it.second }) return

            val message = result.value.toString()
            val metaInfo = ParsedCodeMetaInfo(
                start, end,
                attributes = mutableListOf(),
                tag = "EVALUATED",
                description = StringUtil.escapeLineBreak(message)
            )
            globalMetadataInfoHandler.addMetadataInfosForFile(testFile, listOf(metaInfo))
        }

        data class Options(val renderLiterals: Boolean)

        class EvaluateAndRenderExpressions : FirVisitor<Unit, Options>() {
            // This set is used to avoid double rendering
            private val visitedElements = mutableSetOf<FirElement>()

            override fun visitElement(element: FirElement, data: Options) {
                element.acceptChildren(this, data)
            }

            override fun <T> visitLiteralExpression(literalExpression: FirLiteralExpression<T>, data: Options) {
                if (!data.renderLiterals) return
                render(literalExpression, literalExpression.source)
            }

            override fun visitProperty(property: FirProperty, data: Options) {
                if (property in visitedElements) return
                visitedElements.add(property)

                super.visitProperty(property, data)
                session.compileTimeEvaluator.evaluatePropertyInitializer(property)?.let { result ->
                    val source = (property.initializer as? FirQualifiedAccessExpression)?.calleeReference?.source
                        ?: result.source
                        ?: return
                    render(result, source)
                }
            }

            override fun visitAnnotationCall(annotationCall: FirAnnotationCall, data: Options) {
                if (annotationCall in visitedElements) return
                visitedElements.add(annotationCall)

                super.visitAnnotationCall(annotationCall, data)
                session.compileTimeEvaluator.evaluateAnnotationArgs(annotationCall)?.let { result ->
                    result.mapping.values.forEach {
                        it.accept(this, data.copy(renderLiterals = true))
                    }
                }
            }

            override fun visitConstructor(constructor: FirConstructor, data: Options) {
                if (constructor in visitedElements) return
                visitedElements.add(constructor)

                super.visitConstructor(constructor, data)
                session.compileTimeEvaluator.evaluateDefaultsOfAnnotationConstructor(constructor)?.let { result ->
                    result.values.forEach {
                        it.accept(this, data.copy(renderLiterals = true))
                    }
                }
            }

            override fun visitResolvedTypeRef(resolvedTypeRef: FirResolvedTypeRef, data: Options) {
                // Visit annotations on type arguments
                resolvedTypeRef.delegatedTypeRef?.accept(this, data)
                return super.visitResolvedTypeRef(resolvedTypeRef, data)
            }
        }, Options(renderLiterals = false))
        }

        firFile.accept(EvaluateAndRenderExpressions(), Options(renderLiterals = false))
    }

    private fun TestFile.extractRangesWithoutRender(): List<Pair<Int, Int>> {
        val content = testServices.sourceFileProvider.getContentOfSourceFile(this)
        return buildList {
            var indexOfStop = -1
            do {
                indexOfStop = content.indexOf(stopEvaluation, indexOfStop + 1)
                if (indexOfStop < 0) break

                val indexOfStart = content.indexOf(startEvaluation, indexOfStop).takeIf { it != -1 } ?: content.length
                add(indexOfStop to indexOfStart)
            } while (true)
        }
    }
}
