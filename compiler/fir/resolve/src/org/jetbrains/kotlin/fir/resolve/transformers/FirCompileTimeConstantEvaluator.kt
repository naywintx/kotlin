/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.transformers

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.utils.isConst
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid

class FirCompileTimeConstantEvaluator(private val session: FirSession) : FirVisitorVoid() {
    private val evaluator = FirExpressionEvaluator()

    override fun visitElement(element: FirElement) {
        element.acceptChildren(this)
    }

    override fun visitProperty(property: FirProperty) {
        if (!property.isConst || property.initializer is FirLiteralExpression<*>) {
            return super.visitProperty(property)
        }

        // TODO check first
        val evaluated = evaluator.evaluate(property.initializer) ?: return
        property.replaceInitializer(evaluated)
    }

    override fun visitAnnotationCall(annotationCall: FirAnnotationCall) {
        // TODO transform argumentList or argumentMapping?
        super.visitAnnotationCall(annotationCall)
    }

    override fun visitConstructor(constructor: FirConstructor) {
        // TODO evaluate default arguments for primary constructor of an annotation
        super.visitConstructor(constructor)
    }

    override fun visitExpression(expression: FirExpression) {
        // TODO try to evaluate in a special mode
        super.visitExpression(expression)
    }
}

private class FirExpressionEvaluator : FirTransformer<Nothing?>() {
    fun evaluate(expression: FirExpression?): FirLiteralExpression<*>? {
        return null
    }

    override fun <E : FirElement> transformElement(element: E, data: Nothing?): E {
        error("FIR element ${element::class} is not supported in constant evaluation")
    }

    override fun <T> transformLiteralExpression(literalExpression: FirLiteralExpression<T>, data: Nothing?): FirStatement {
        return literalExpression
    }

    override fun transformPropertyAccessExpression(propertyAccessExpression: FirPropertyAccessExpression, data: Nothing?): FirStatement {
        // TODO
        return super.transformPropertyAccessExpression(propertyAccessExpression, data)
    }

    override fun transformFunctionCall(functionCall: FirFunctionCall, data: Nothing?): FirStatement {
        val calleeReference = functionCall.calleeReference
        if (calleeReference !is FirResolvedNamedReference) return functionCall

        return when (val symbol = calleeReference.resolvedSymbol) {
            is FirNamedFunctionSymbol -> visitNamedFunction(functionCall, symbol)
            is FirConstructorSymbol -> visitConstructorCall(functionCall, symbol)
            else -> super.transformFunctionCall(functionCall, data)
        }
    }

    private fun visitNamedFunction(functionCall: FirFunctionCall, symbol: FirNamedFunctionSymbol): FirExpression {
        // TODO
        return functionCall
    }

    private fun visitConstructorCall(constructorCall: FirFunctionCall, symbol: FirConstructorSymbol): FirExpression {
        // TODO
        return constructorCall
    }
}