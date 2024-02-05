/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.transformers

import org.jetbrains.kotlin.contracts.description.LogicOperationKind
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.isAnnotationConstructor
import org.jetbrains.kotlin.fir.declarations.utils.evaluatedDefaultValue
import org.jetbrains.kotlin.fir.declarations.utils.evaluatedInitializer
import org.jetbrains.kotlin.fir.declarations.utils.isConst
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.builder.*
import org.jetbrains.kotlin.fir.expressions.impl.FirResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.impl.toAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.references.FirReference
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.diagnostics.canBeEvaluatedAtCompileTime
import org.jetbrains.kotlin.fir.resolve.diagnostics.canBeUsedForConstVal
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toSymbol
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

val FirSession.compileTimeEvaluator: FirCompileTimeConstantEvaluator by FirSession.sessionComponentAccessor()

class FirCompileTimeConstantEvaluator(
    private val session: FirSession,
) : FirTransformer<Nothing?>(), FirSessionComponent {
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
        if (annotationCall.annotationTypeRef is FirErrorTypeRef) {
            return super.transformAnnotationCall(annotationCall, data)
        }

        val argumentList = annotationCall.argumentList as? FirResolvedArgumentList
            ?: return super.transformAnnotationCall(annotationCall, data)

        if (argumentList.mapping.any { (expression, parameter) -> !expression.canBeEvaluated(parameter.returnTypeRef.coneTypeOrNull) }) {
            return super.transformAnnotationCall(annotationCall, data)
        }

        val evaluatedMapping = buildAnnotationArgumentMapping {
            source = annotationCall.argumentMapping.source
            mapping.putAll(
                argumentList.mapping.map { (expression, parameter) -> parameter.name to tryToEvaluateExpression(expression).unwrapArgument() }
            )
        }
        annotationCall.replaceArgumentMapping(evaluatedMapping)

        return super.transformAnnotationCall(annotationCall, data)
    }

    override fun transformConstructor(constructor: FirConstructor, data: Nothing?): FirStatement {
        // We should evaluate default arguments for primary constructor of an annotation
        if (!constructor.symbol.isAnnotationConstructor(session)) return super.transformConstructor(constructor, data)

        constructor.getParametersWithDefaultValueToBeEvaluated().forEach { parameter ->
            val defaultValueToEvaluate = parameter.defaultValue ?: return@forEach
            parameter.evaluatedDefaultValue = tryToEvaluateExpression(defaultValueToEvaluate)
        }

        return super.transformConstructor(constructor, data)
    }

    override fun transformExpression(expression: FirExpression, data: Nothing?): FirStatement {
        // TODO try to evaluate in a special mode
        return super.transformExpression(expression, data)
    }

    private fun FirConstructor.getParametersWithDefaultValueToBeEvaluated(): List<FirValueParameter> {
        if (!isPrimary) {
            return emptyList()
        }

        return buildList {
            for (parameter in valueParameters) {
                val defaultValue = parameter.defaultValue
                if (defaultValue != null && defaultValue.canBeEvaluated(parameter.returnTypeRef.coneTypeOrNull)) {
                    add(parameter)
                }
            }
        }
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

    override fun visitGetClassCall(getClassCall: FirGetClassCall, data: Nothing?): FirStatement {
        return getClassCall
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

    override fun visitNamedArgumentExpression(namedArgumentExpression: FirNamedArgumentExpression, data: Nothing?): FirStatement? {
        return buildNamedArgumentExpression {
            source = namedArgumentExpression.source
            annotations.addAll(namedArgumentExpression.annotations)
            expression = evaluate(namedArgumentExpression.expression) ?: return null
            isSpread = namedArgumentExpression.isSpread
            name = namedArgumentExpression.name
        }
    }

    @OptIn(UnresolvedExpressionTypeAccess::class)
    override fun visitArrayLiteral(arrayLiteral: FirArrayLiteral, data: Nothing?): FirStatement? {
        val newArgumentList = visitArgumentList(arrayLiteral.argumentList, data) ?: return null
        return buildArrayLiteral {
            source = arrayLiteral.source
            coneTypeOrNull = arrayLiteral.coneTypeOrNull
            annotations.addAll(arrayLiteral.annotations)
            argumentList = newArgumentList
        }
    }

    @OptIn(UnresolvedExpressionTypeAccess::class)
    override fun visitVarargArgumentsExpression(varargArgumentsExpression: FirVarargArgumentsExpression, data: Nothing?): FirStatement? {
        return buildVarargArgumentsExpression {
            source = varargArgumentsExpression.source
            coneTypeOrNull = varargArgumentsExpression.coneTypeOrNull
            annotations.addAll(varargArgumentsExpression.annotations)
            arguments.addAll(varargArgumentsExpression.arguments.map { evaluate(it) ?: return null })
            coneElementTypeOrNull = varargArgumentsExpression.coneElementTypeOrNull
        }
    }

    override fun visitSpreadArgumentExpression(spreadArgumentExpression: FirSpreadArgumentExpression, data: Nothing?): FirStatement? {
        return buildSpreadArgumentExpression {
            source = spreadArgumentExpression.source
            annotations.addAll(spreadArgumentExpression.annotations)
            expression = evaluate(spreadArgumentExpression.expression) ?: return null
        }
    }

    override fun visitPropertyAccessExpression(propertyAccessExpression: FirPropertyAccessExpression, data: Nothing?): FirStatement? {
        val propertySymbol = propertyAccessExpression.toReference(session)?.toResolvedCallableSymbol(discardErrorReference = true)
            ?: return null

        fun evaluateOrCopy(initializer: FirExpression?): FirExpression? {
            return if (initializer is FirLiteralExpression<*>) {
                // We need a copy here to copy a source of the original expression
                initializer.copy(propertyAccessExpression)
            } else {
                evaluate(initializer)
            }
        }

        return when (propertySymbol) {
            is FirPropertySymbol -> {
                when {
                    propertySymbol.callableId.isStringLength || propertySymbol.callableId.isCharCode -> {
                        evaluate(propertyAccessExpression.explicitReceiver)?.let { receiver ->
                            evaluateUnary(receiver, propertySymbol.callableId)
                                ?.adjustTypeAndConvertToLiteral(propertyAccessExpression)
                        }
                    }
                    else -> evaluateOrCopy(propertySymbol.fir.initializer)
                }
            }
            is FirFieldSymbol -> evaluateOrCopy(propertySymbol.fir.initializer)
            is FirEnumEntrySymbol -> propertyAccessExpression // Can't be evaluated, should be returned as is.
            else -> error("FIR symbol \"${propertySymbol::class}\" is not supported in constant evaluation")
        }
    }

    override fun visitFunctionCall(functionCall: FirFunctionCall, data: Nothing?): FirElement? {
        val calleeReference = functionCall.calleeReference
        if (calleeReference !is FirResolvedNamedReference) return null

        return when (val symbol = calleeReference.resolvedSymbol) {
            is FirNamedFunctionSymbol -> visitNamedFunction(functionCall, symbol)
            is FirConstructorSymbol -> visitConstructorCall(functionCall)
            else -> null
        }
    }

    private fun visitNamedFunction(functionCall: FirFunctionCall, symbol: FirNamedFunctionSymbol): FirStatement? {
        val receivers = listOfNotNull(functionCall.dispatchReceiver, functionCall.extensionReceiver)
        val evaluatedArgs = receivers.plus(functionCall.arguments).map { evaluate(it) as? FirLiteralExpression<*> }
        if (evaluatedArgs.any { it == null }) return null

        val opr1 = evaluatedArgs.getOrNull(0) ?: return null
        evaluateUnary(opr1, symbol.callableId)
            ?.adjustTypeAndConvertToLiteral(functionCall)
            ?.let { return it }

        val opr2 = evaluatedArgs.getOrNull(1) ?: return null
        evaluateBinary(opr1, symbol.callableId, opr2)
            ?.adjustTypeAndConvertToLiteral(functionCall)
            ?.let { return it }

        return null
    }

    @OptIn(UnresolvedExpressionTypeAccess::class)
    private fun visitConstructorCall(constructorCall: FirFunctionCall): FirStatement? {
        val type = constructorCall.resolvedType.fullyExpandedType(session).lowerBoundIfFlexible()
        when {
            type.toRegularClassSymbol(session)?.classKind == ClassKind.ANNOTATION_CLASS -> {
                val evaluatedArgs = constructorCall.argumentList.accept(this, null) as? FirResolvedArgumentList ?: return null
                return buildFunctionCall {
                    coneTypeOrNull = constructorCall.coneTypeOrNull
                    annotations.addAll(constructorCall.annotations)
                    typeArguments.addAll(constructorCall.typeArguments)
                    source = constructorCall.source
                    nonFatalDiagnostics.addAll(constructorCall.nonFatalDiagnostics)
                    argumentList = evaluatedArgs
                    calleeReference = constructorCall.calleeReference
                    origin = constructorCall.origin
                }
            }
            type.isUnsignedType -> {
                val argument = (evaluate(constructorCall.argument) as? FirLiteralExpression<*>)?.value ?: return null
                return argument.adjustTypeAndConvertToLiteral(constructorCall)
            }
            else -> return null
        }
    }

    override fun visitIntegerLiteralOperatorCall(
        integerLiteralOperatorCall: FirIntegerLiteralOperatorCall,
        data: Nothing?
    ): FirElement? {
        return visitFunctionCall(integerLiteralOperatorCall, data)
    }

    override fun visitComparisonExpression(comparisonExpression: FirComparisonExpression, data: Nothing?): FirElement? {
        return visitFunctionCall(comparisonExpression.compareToCall, data).let {
            if (it !is FirLiteralExpression<*>) return@let it
            val intResult = it.value as Int
            val compareToResult = when (comparisonExpression.operation) {
                FirOperation.LT -> intResult < 0
                FirOperation.LT_EQ -> intResult <= 0
                FirOperation.GT -> intResult > 0
                FirOperation.GT_EQ -> intResult >= 0
                else -> error("Unsupported comparison operation type \"${comparisonExpression.operation.name}\"")
            }
            compareToResult.adjustTypeAndConvertToLiteral(comparisonExpression)
        }
    }

    override fun visitEqualityOperatorCall(equalityOperatorCall: FirEqualityOperatorCall, data: Nothing?): FirStatement? {
        val evaluatedArgs = equalityOperatorCall.arguments.map { evaluate(it) as? FirLiteralExpression<*> }
        if (evaluatedArgs.any { it == null } || evaluatedArgs.size != 2) return null

        val result = when (equalityOperatorCall.operation) {
            FirOperation.EQ -> evaluatedArgs[0]?.value == evaluatedArgs[1]?.value
            FirOperation.NOT_EQ -> evaluatedArgs[0]?.value != evaluatedArgs[1]?.value
            else -> error("Operation \"${equalityOperatorCall.operation}\" is not supported in compile time evaluation")
        }

        return result.toConstExpression(ConstantValueKind.Boolean, equalityOperatorCall)
    }

    override fun visitBinaryLogicExpression(binaryLogicExpression: FirBinaryLogicExpression, data: Nothing?): FirStatement? {
        val left = evaluate(binaryLogicExpression.leftOperand)
        val right = evaluate(binaryLogicExpression.rightOperand)

        val leftBoolean = (left as? FirLiteralExpression<*>)?.value as? Boolean ?: return null
        val rightBoolean = (right as? FirLiteralExpression<*>)?.value as? Boolean ?: return null
        val result = when (binaryLogicExpression.kind) {
            LogicOperationKind.AND -> leftBoolean && rightBoolean
            LogicOperationKind.OR -> leftBoolean || rightBoolean
            else -> error("Boolean logic expression of a kind \"${binaryLogicExpression.kind}\" is not supported in compile time evaluation")
        }

        return result.toConstExpression(ConstantValueKind.Boolean, binaryLogicExpression)
    }

    override fun visitStringConcatenationCall(stringConcatenationCall: FirStringConcatenationCall, data: Nothing?): FirStatement? {
        val strings = stringConcatenationCall.argumentList.arguments.map { evaluate(it) as? FirLiteralExpression<*> }
        if (strings.any { it == null }) return null
        val result = strings.joinToString(separator = "") { it!!.value.toString() }
        return result.toConstExpression(ConstantValueKind.String, stringConcatenationCall)
    }

    override fun visitTypeOperatorCall(typeOperatorCall: FirTypeOperatorCall, data: Nothing?): FirElement? {
        if (typeOperatorCall.operation != FirOperation.AS) return null
        val result = evaluate(typeOperatorCall.argument) as? FirLiteralExpression<*> ?: return null
        if (result.resolvedType.isSubtypeOf(typeOperatorCall.resolvedType, session)) {
            return result
        }
        return typeOperatorCall
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
private fun evaluateUnary(arg: FirExpression, callableId: CallableId): Any? {
    if (arg !is FirLiteralExpression<*> || arg.value == null) return null

    val opr = arg.kind.convertToGivenKind(arg.value) ?: return null
    return evalUnaryOp(
        callableId.callableName.asString(),
        arg.kind.toCompileTimeType(),
        opr
    )
}

// Binary operators
private fun evaluateBinary(
    arg1: FirExpression,
    callableId: CallableId,
    arg2: FirExpression
): Any? {
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
    )
}

private fun Any.adjustTypeAndConvertToLiteral(original: FirExpression): FirLiteralExpression<*>? {
    val expectedType = original.resolvedType
    val expectedKind = expectedType.toConstantValueKind() ?: return null
    val typeAdjustedValue = expectedKind.convertToGivenKind(this) ?: return null
    return typeAdjustedValue.toConstExpression(expectedKind, original)
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

        StandardClassIds.UByte -> ConstantValueKind.UnsignedByte
        StandardClassIds.UShort -> ConstantValueKind.UnsignedShort
        StandardClassIds.UInt -> ConstantValueKind.UnsignedInt
        StandardClassIds.ULong -> ConstantValueKind.UnsignedLong

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
        ConstantValueKind.UnsignedByte -> (value as Number).toLong().toUByte()
        ConstantValueKind.UnsignedShort -> (value as Number).toLong().toUShort()
        ConstantValueKind.UnsignedInt -> (value as Number).toLong().toUInt()
        ConstantValueKind.UnsignedLong -> (value as Number).toLong().toULong()
        ConstantValueKind.UnsignedIntegerLiteral -> (value as Number).toLong().toULong()
        else -> null
    }
}

private fun <T> Any?.toConstExpression(
    kind: ConstantValueKind<T>,
    originalExpression: FirExpression
): FirLiteralExpression<T> {
    @Suppress("UNCHECKED_CAST")
    return buildLiteralExpression(
        originalExpression.source,
        kind,
        this as T,
        originalExpression.annotations.takeIf { it.isNotEmpty() }?.toMutableList(),
        setType = false,
    ).apply { replaceConeTypeOrNull(originalExpression.resolvedType) }
}

private fun <T> FirLiteralExpression<T>.copy(originalExpression: FirExpression): FirLiteralExpression<T> {
    return this.value.toConstExpression(this.kind, originalExpression)
}
