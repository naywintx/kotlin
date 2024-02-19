/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.transformers

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.utils.isConst
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildLiteralExpression
import org.jetbrains.kotlin.fir.expressions.impl.FirResolvedArgumentList
import org.jetbrains.kotlin.fir.references.FirReference
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.resolve.diagnostics.canBeEvaluatedAtCompileTime
import org.jetbrains.kotlin.fir.resolve.diagnostics.canBeUsedForConstVal
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.symbols.ConeClassLikeLookupTag
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.FirVisitor
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.resolve.constants.evaluate.CompileTimeType
import org.jetbrains.kotlin.resolve.constants.evaluate.evalBinaryOp
import org.jetbrains.kotlin.resolve.constants.evaluate.evalUnaryOp
import org.jetbrains.kotlin.types.ConstantValueKind
import org.jetbrains.kotlin.util.OperatorNameConventions

class FirCompileTimeConstantEvaluator(private val session: FirSession) : FirTransformer<Nothing?>() {
    private val evaluator = FirExpressionEvaluator(session)

    override fun <E : FirElement> transformElement(element: E, data: Nothing?): E {
        @Suppress("UNCHECKED_CAST")
        return element.transformChildren(this, data) as E
    }

    private fun FirExpression?.canBeEvaluated(expectedType: ConeKotlinType? = null): Boolean {
        if (this == null || !isResolved) return false

        if (!resolvedType.isSubtypeOf(expectedType ?: this.resolvedType, session)) return false

        return canBeEvaluatedAtCompileTime(this, session, allowErrors = false)
    }

    private fun tryToEvaluateExpression(expression: FirExpression): FirExpression {
        val result = evaluator.evaluate(expression)
        return result ?: error("Couldn't evaluate FIR expression: ${expression.render()}")
    }

    override fun transformProperty(property: FirProperty, data: Nothing?): FirStatement {
        if (!property.isConst) {
            return super.transformProperty(property, data)
        }

        val type = property.returnTypeRef.coneTypeOrNull?.fullyExpandedType(session)
        if (type == null || type is ConeErrorType || !type.canBeUsedForConstVal()) {
            return super.transformProperty(property, data)
        }

        val initializer = property.initializer
        if (initializer == null || !initializer.canBeEvaluated(type)) {
            return super.transformProperty(property, data)
        }

        property.replaceInitializer(tryToEvaluateExpression(initializer))
        return super.transformProperty(property, data)
    }

    override fun transformAnnotationCall(annotationCall: FirAnnotationCall, data: Nothing?): FirStatement {
        // TODO transform argumentList or argumentMapping?
        return super.transformAnnotationCall(annotationCall, data)
    }

    override fun transformConstructor(constructor: FirConstructor, data: Nothing?): FirStatement {
        // TODO evaluate default arguments for primary constructor of an annotation
        return super.transformConstructor(constructor, data)
    }

    override fun transformExpression(expression: FirExpression, data: Nothing?): FirStatement {
        // TODO try to evaluate in a special mode
        return super.transformExpression(expression, data)
    }
}

private class FirExpressionEvaluator(private val session: FirSession) : FirVisitor<FirElement?, Nothing?>() {
    fun evaluate(expression: FirExpression?): FirExpression? {
        return expression?.accept(this, null) as? FirExpression
    }

    override fun visitElement(element: FirElement, data: Nothing?): FirElement? {
        error("FIR element \"${element::class}\" is not supported in constant evaluation")
    }

    override fun <T> visitLiteralExpression(literalExpression: FirLiteralExpression<T>, data: Nothing?): FirStatement {
        return literalExpression
    }

    override fun visitResolvedNamedReference(resolvedNamedReference: FirResolvedNamedReference, data: Nothing?): FirReference {
        return resolvedNamedReference
    }

    override fun visitResolvedQualifier(resolvedQualifier: FirResolvedQualifier, data: Nothing?): FirStatement {
        return resolvedQualifier
    }

    override fun visitArgumentList(argumentList: FirArgumentList, data: Nothing?): FirArgumentList? {
        when (argumentList) {
            is FirResolvedArgumentList -> return buildResolvedArgumentList(
                argumentList.mapping.mapKeysTo(LinkedHashMap()) { evaluate(it.key) ?: return null },
                argumentList.source
            )
            else -> return buildArgumentList {
                source = argumentList.source
                arguments.addAll(argumentList.arguments.map { evaluate(it) ?: return null })
            }
        }
    }

    override fun visitPropertyAccessExpression(propertyAccessExpression: FirPropertyAccessExpression, data: Nothing?): FirStatement? {
        val propertySymbol = propertyAccessExpression.toReference(session)?.toResolvedCallableSymbol(discardErrorReference = true)
            ?: return null

        fun evaluate(initializer: FirExpression?): FirExpression? {
            return if (initializer is FirLiteralExpression<*>) {
                initializer
            } else {
                this.evaluate(initializer)
            }
        }

        return when (propertySymbol) {
            is FirPropertySymbol -> {
                when {
                    propertySymbol.callableId.isStringLength || propertySymbol.callableId.isCharCode -> {
                        this.evaluate(propertyAccessExpression.explicitReceiver)?.let { receiver ->
                            propertyAccessExpression.evaluate(receiver, propertySymbol.callableId)
                        }
                    }
                    else -> evaluate(propertySymbol.fir.initializer)
                }
            }
            is FirFieldSymbol -> evaluate(propertySymbol.fir.initializer)
            is FirEnumEntrySymbol -> propertyAccessExpression // Can't be evaluated, should be returned as is.
            else -> error("FIR symbol \"${propertySymbol::class}\" is not supported in constant evaluation")
        }
    }

    override fun visitFunctionCall(functionCall: FirFunctionCall, data: Nothing?): FirElement? {
        val calleeReference = functionCall.calleeReference
        if (calleeReference !is FirResolvedNamedReference) return null

        return when (val symbol = calleeReference.resolvedSymbol) {
            is FirNamedFunctionSymbol -> visitNamedFunction(functionCall, symbol)
            is FirConstructorSymbol -> visitConstructorCall(functionCall, symbol)
            else -> null
        }
    }

    private fun visitNamedFunction(functionCall: FirFunctionCall, symbol: FirNamedFunctionSymbol): FirStatement? {
        val receivers = listOfNotNull(functionCall.dispatchReceiver, functionCall.extensionReceiver)
        val evaluatedArgs = receivers.plus(functionCall.arguments).map { evaluate(it) as? FirLiteralExpression<*> }
        if (evaluatedArgs.any { it == null }) return null

        val opr1 = evaluatedArgs.getOrNull(0) ?: return null
        functionCall.evaluate(opr1, symbol.callableId)?.let { return it }

        val opr2 = evaluatedArgs.getOrNull(1) ?: return null
        functionCall.evaluate(symbol.callableId, opr1, opr2)?.let { return it }

        return null
    }

    private fun visitConstructorCall(constructorCall: FirFunctionCall, symbol: FirConstructorSymbol): FirStatement? {
        // TODO
        return constructorCall
    }

}

private fun <T> ConstantValueKind<T>.toCompileTimeType(): CompileTimeType {
    return when (this) {
        ConstantValueKind.Byte -> CompileTimeType.BYTE
        ConstantValueKind.Short -> CompileTimeType.SHORT
        ConstantValueKind.Int -> CompileTimeType.INT
        ConstantValueKind.Long -> CompileTimeType.LONG
        ConstantValueKind.Double -> CompileTimeType.DOUBLE
        ConstantValueKind.Float -> CompileTimeType.FLOAT
        ConstantValueKind.Char -> CompileTimeType.CHAR
        ConstantValueKind.Boolean -> CompileTimeType.BOOLEAN
        ConstantValueKind.String -> CompileTimeType.STRING

        else -> CompileTimeType.ANY
    }
}

// Unary operators
private fun FirExpression.evaluate(arg: FirExpression, callableId: CallableId): FirLiteralExpression<*>? {
    if (arg !is FirLiteralExpression<*> || arg.value == null) return null

    val opr = arg.kind.convertToGivenKind(arg.value) ?: return null
    return evalUnaryOp(
        callableId.callableName.asString(),
        arg.kind.toCompileTimeType(),
        opr
    )?.adjustTypeAndConvertToLiteral(this)
}

// Binary operators
private fun FirExpression.evaluate(
    callableId: CallableId,
    arg1: FirExpression,
    arg2: FirExpression
): FirExpression? {
    if (arg1 !is FirLiteralExpression<*> || arg1.value == null) return null
    if (arg2 !is FirLiteralExpression<*> || arg2.value == null) return null
    // NB: some utils accept very general types, and due to the way operation map works, we should up-cast rhs type.
    val rightType = when {
        callableId.isStringEquals -> CompileTimeType.ANY
        callableId.isStringPlus -> CompileTimeType.ANY
        else -> arg2.kind.toCompileTimeType()
    }

    val opr1 = arg1.kind.convertToGivenKind(arg1.value) ?: return null
    val opr2 = arg2.kind.convertToGivenKind(arg2.value) ?: return null

    val functionName = callableId.callableName.asString()
    return evalBinaryOp(
        functionName,
        arg1.kind.toCompileTimeType(),
        opr1,
        rightType,
        opr2
    )?.adjustTypeAndConvertToLiteral(this)
}

private fun Any.adjustTypeAndConvertToLiteral(original: FirExpression): FirLiteralExpression<*>? {
    val expectedType = original.resolvedType
    val expectedKind = expectedType.toConstantValueKind() ?: return null
    val typeAdjustedValue = expectedKind.convertToGivenKind(this) ?: return null
    return expectedKind.toConstExpression(original.source, typeAdjustedValue, original).apply {
        replaceConeTypeOrNull(expectedType)
    }
}

private val CallableId.isStringLength: Boolean
    get() = classId == StandardClassIds.String && callableName.identifierOrNullIfSpecial == "length"

private val CallableId.isStringEquals: Boolean
    get() = classId == StandardClassIds.String && callableName == OperatorNameConventions.EQUALS

private val CallableId.isStringPlus: Boolean
    get() = classId == StandardClassIds.String && callableName == OperatorNameConventions.PLUS

private val CallableId.isCharCode: Boolean
    get() = packageName == StandardClassIds.BASE_KOTLIN_PACKAGE && classId == null && callableName.identifierOrNullIfSpecial == "code"

////// KINDS

private fun ConeKotlinType.toConstantValueKind(): ConstantValueKind<*>? =
    when (this) {
        is ConeErrorType -> null
        is ConeLookupTagBasedType -> (lookupTag as? ConeClassLikeLookupTag)?.classId?.toConstantValueKind()
        is ConeFlexibleType -> upperBound.toConstantValueKind()
        is ConeCapturedType -> lowerType?.toConstantValueKind() ?: constructor.supertypes!!.first().toConstantValueKind()
        is ConeDefinitelyNotNullType -> original.toConstantValueKind()
        is ConeIntersectionType -> intersectedTypes.first().toConstantValueKind()
        is ConeStubType, is ConeIntegerLiteralType, is ConeTypeVariableType -> null
    }

private fun ClassId.toConstantValueKind(): ConstantValueKind<*>? =
    when (this) {
        StandardClassIds.Byte -> ConstantValueKind.Byte
        StandardClassIds.Double -> ConstantValueKind.Double
        StandardClassIds.Float -> ConstantValueKind.Float
        StandardClassIds.Int -> ConstantValueKind.Int
        StandardClassIds.Long -> ConstantValueKind.Long
        StandardClassIds.Short -> ConstantValueKind.Short

        StandardClassIds.Char -> ConstantValueKind.Char
        StandardClassIds.String -> ConstantValueKind.String
        StandardClassIds.Boolean -> ConstantValueKind.Boolean

        else -> null
    }

private fun ConstantValueKind<*>.convertToGivenKind(value: Any?): Any? {
    if (value == null) {
        return null
    }
    return when (this) {
        ConstantValueKind.Boolean -> value as Boolean
        ConstantValueKind.Char -> value as Char
        ConstantValueKind.String -> value as String
        ConstantValueKind.Byte -> (value as Number).toByte()
        ConstantValueKind.Double -> (value as Number).toDouble()
        ConstantValueKind.Float -> (value as Number).toFloat()
        ConstantValueKind.Int -> (value as Number).toInt()
        ConstantValueKind.Long -> (value as Number).toLong()
        ConstantValueKind.Short -> (value as Number).toShort()
        else -> null
    }
}

private fun <T> ConstantValueKind<T>.toConstExpression(
    source: KtSourceElement?,
    value: Any?,
    originalExpression: FirExpression
): FirLiteralExpression<T> {
    @Suppress("UNCHECKED_CAST")
    return (buildLiteralExpression(source, this, value as T, setType = false, originalExpression = originalExpression))
}
