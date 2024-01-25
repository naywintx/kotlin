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
import org.jetbrains.kotlin.fir.declarations.utils.evaluatedInitializer
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
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
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
        if (this == null || this is FirLazyExpression || !isResolved) return false

        if (expectedType != null && !resolvedType.isSubtypeOf(expectedType, session)) return false

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

        (tryToEvaluateExpression(initializer) as? FirLiteralExpression<*>)?.let {
            property.evaluatedInitializer = it
        }
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
            ?: return propertyAccessExpression
        return when (propertySymbol) {
            is FirPropertySymbol -> {
                val initializer = propertySymbol.fir.initializer
                evaluate(initializer)
            }
            is FirFieldSymbol -> {
                val initializer = propertySymbol.fir.initializer
                evaluate(initializer)
            }
            is FirEnumEntrySymbol -> TODO()
            else -> TODO()
        } ?: propertyAccessExpression
    }

    override fun visitFunctionCall(functionCall: FirFunctionCall, data: Nothing?): FirElement? {
        val calleeReference = functionCall.calleeReference
        if (calleeReference !is FirResolvedNamedReference) return functionCall

        return when (val symbol = calleeReference.resolvedSymbol) {
            is FirNamedFunctionSymbol -> visitNamedFunction(functionCall, symbol)
            is FirConstructorSymbol -> visitConstructorCall(functionCall, symbol)
            else -> super.visitFunctionCall(functionCall, data)
        }
    }

    private fun visitNamedFunction(functionCall: FirFunctionCall, symbol: FirNamedFunctionSymbol): FirStatement? {
        val receivers = listOfNotNull(functionCall.dispatchReceiver, functionCall.extensionReceiver)
        val evaluatedArgs = receivers.plus(functionCall.arguments).map { evaluate(it) as? FirLiteralExpression<*> }
        if (evaluatedArgs.any { it == null }) return null

        val opr1 = functionCall.explicitReceiver as? FirLiteralExpression<*> ?: return null
        evaluate(opr1, symbol.callableId)?.let {
            return it.adjustType(functionCall.resolvedType)
        }

        val opr2 = functionCall.arguments.firstOrNull() as? FirLiteralExpression<*> ?: return null
        evaluate(symbol.callableId, opr1, opr2)?.let {
            return it.adjustType(functionCall.resolvedType)
        }

        return null
    }

    private fun visitConstructorCall(constructorCall: FirFunctionCall, symbol: FirConstructorSymbol): FirStatement? {
        // TODO
        return constructorCall
    }
}

private fun FirLiteralExpression<*>.adjustType(expectedType: ConeKotlinType): FirLiteralExpression<*> {
    val expectedKind = expectedType.toConstantValueKind()
    // Note that the resolved type for the const expression is not always matched with the const kind. For example,
    //   fun foo(x: Int) {
    //     when (x) {
    //       -2_147_483_628 -> ...
    //   } }
    // That constant is encoded as `unaryMinus` call with the const 2147483628 of long type, while the resolved type is Int.
    // After computing the compile time constant, we need to adjust its type here.
    val expression =
        if (expectedKind != null && expectedKind != kind && value is Number) {
            val typeAdjustedValue = expectedKind.convertToNumber(value as Number)!!
            expectedKind.toConstExpression(source, typeAdjustedValue)
        } else {
            this
        }
    // Lastly, we should preserve the resolved type of the original function call.
    return expression.apply {
        replaceConeTypeOrNull(expectedType)
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
private fun evaluate(arg: FirLiteralExpression<*>, callableId: CallableId): FirLiteralExpression<*>? {
    if (arg.value == null) return null
    (arg.value as? String)?.let { opr ->
        evalUnaryOp(
            callableId.callableName.asString(),
            arg.kind.toCompileTimeType(),
            opr
        )?.let {
            return it.toConstantValueKind().toConstExpression(arg.source, it)
        }
    }
    return arg.kind.convertToNumber(arg.value as? Number)?.let { opr ->
        evalUnaryOp(
            callableId.callableName.asString(),
            arg.kind.toCompileTimeType(),
            opr
        )?.let {
            it.toConstantValueKind().toConstExpression(arg.source, it)
        }
    }
}

private fun FirLiteralExpression<*>.evaluateStringLength(): FirLiteralExpression<*>? {
    return (value as? String)?.length?.let {
        it.toConstantValueKind().toConstExpression(source, it)
    }
}

// Binary operators
private fun evaluate(
    callableId: CallableId,
    arg1: FirLiteralExpression<*>,
    arg2: FirLiteralExpression<*>
): FirLiteralExpression<*>? {
    if (arg1.value == null || arg2.value == null) return null
    // NB: some utils accept very general types, and due to the way operation map works, we should up-cast rhs type.
    val rightType = when {
        callableId.isStringEquals -> CompileTimeType.ANY
        callableId.isStringPlus -> CompileTimeType.ANY
        else -> arg2.kind.toCompileTimeType()
    }
    (arg1.value as? String)?.let { opr1 ->
        arg2.value?.let { opr2 ->
            evalBinaryOp(
                callableId.callableName.asString(),
                arg1.kind.toCompileTimeType(),
                opr1,
                rightType,
                opr2
            )?.let {
                return it.toConstantValueKind().toConstExpression(arg1.source, it)
            }
        }
    }
    return arg1.kind.convertToNumber(arg1.value as? Number)?.let { opr1 ->
        arg2.kind.convertToNumber(arg2.value as? Number)?.let { opr2 ->
            evalBinaryOp(
                callableId.callableName.asString(),
                arg1.kind.toCompileTimeType(),
                opr1,
                arg2.kind.toCompileTimeType(),
                opr2
            )?.let {
                it.toConstantValueKind().toConstExpression(arg1.source, it)
            }
        }
    }
}

private val CallableId.isStringLength: Boolean
    get() = classId == StandardClassIds.String && callableName.identifierOrNullIfSpecial == "length"

private val CallableId.isStringEquals: Boolean
    get() = classId == StandardClassIds.String && callableName == OperatorNameConventions.EQUALS

private val CallableId.isStringPlus: Boolean
    get() = classId == StandardClassIds.String && callableName == OperatorNameConventions.PLUS

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

private fun <T> T.toConstantValueKind(): ConstantValueKind<*> =
    when (this) {
        is Byte -> ConstantValueKind.Byte
        is Double -> ConstantValueKind.Double
        is Float -> ConstantValueKind.Float
        is Int -> ConstantValueKind.Int
        is Long -> ConstantValueKind.Long
        is Short -> ConstantValueKind.Short

        is Char -> ConstantValueKind.Char
        is String -> ConstantValueKind.String
        is Boolean -> ConstantValueKind.Boolean

        null -> ConstantValueKind.Null
        else -> error("Unknown constant value")
    }

private fun ConstantValueKind<*>.convertToNumber(value: Number?): Any? {
    if (value == null) {
        return null
    }
    return when (this) {
        ConstantValueKind.Byte -> value.toByte()
        ConstantValueKind.Double -> value.toDouble()
        ConstantValueKind.Float -> value.toFloat()
        ConstantValueKind.Int -> value.toInt()
        ConstantValueKind.Long -> value.toLong()
        ConstantValueKind.Short -> value.toShort()
        ConstantValueKind.UnsignedByte -> value.toLong().toUByte()
        ConstantValueKind.UnsignedShort -> value.toLong().toUShort()
        ConstantValueKind.UnsignedInt -> value.toLong().toUInt()
        ConstantValueKind.UnsignedLong -> value.toLong().toULong()
        ConstantValueKind.UnsignedIntegerLiteral -> value.toLong().toULong()
        else -> null
    }
}

private fun <T> ConstantValueKind<T>.toConstExpression(source: KtSourceElement?, value: Any?): FirLiteralExpression<T> =
    @Suppress("UNCHECKED_CAST")
    (buildLiteralExpression(source, this, value as T, setType = true))
