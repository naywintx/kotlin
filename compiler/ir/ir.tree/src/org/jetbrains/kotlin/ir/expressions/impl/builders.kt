/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.expressions.impl

import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.typeParametersCount
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.allTypeParameters
import org.jetbrains.kotlin.ir.util.parentAsClass

@ObsoleteDescriptorBasedAPI
fun IrCallImpl.Companion.fromSymbolDescriptor(
    startOffset: Int,
    endOffset: Int,
    type: IrType,
    symbol: IrSimpleFunctionSymbol,
    typeArgumentsCount: Int = symbol.descriptor.typeParametersCount,
    valueArgumentsCount: Int = symbol.descriptor.valueParameters.size + symbol.descriptor.contextReceiverParameters.size,
    origin: IrStatementOrigin? = null,
    superQualifierSymbol: IrClassSymbol? = null,
): IrCallImpl =
    IrCallImpl(startOffset, endOffset, type, symbol, typeArgumentsCount, valueArgumentsCount, origin, superQualifierSymbol)

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun IrCallImpl.Companion.fromSymbolOwner(
    startOffset: Int,
    endOffset: Int,
    type: IrType,
    symbol: IrSimpleFunctionSymbol,
    typeArgumentsCount: Int = symbol.owner.typeParameters.size,
    valueArgumentsCount: Int = symbol.owner.valueParameters.size,
    origin: IrStatementOrigin? = null,
    superQualifierSymbol: IrClassSymbol? = null,
): IrCallImpl =
    IrCallImpl(startOffset, endOffset, type, symbol, typeArgumentsCount, valueArgumentsCount, origin, superQualifierSymbol)

fun IrCallImpl.Companion.fromSymbolOwner(
    startOffset: Int,
    endOffset: Int,
    symbol: IrSimpleFunctionSymbol,
): IrCallImpl =
    IrCallImpl(
        startOffset,
        endOffset,
        symbol.owner.returnType,
        symbol,
        typeArgumentsCount = symbol.owner.typeParameters.size,
        valueArgumentsCount = symbol.owner.valueParameters.size,
        origin = null,
        superQualifierSymbol = null
    )


@ObsoleteDescriptorBasedAPI
fun IrConstructorCallImpl.Companion.fromSymbolDescriptor(
    startOffset: Int,
    endOffset: Int,
    type: IrType,
    constructorSymbol: IrConstructorSymbol,
    origin: IrStatementOrigin? = null,
): IrConstructorCallImpl {
    val constructorDescriptor = constructorSymbol.descriptor
    val classTypeParametersCount = constructorDescriptor.constructedClass.original.declaredTypeParameters.size
    val totalTypeParametersCount = constructorDescriptor.typeParameters.size
    val valueParametersCount = constructorDescriptor.valueParameters.size + constructorDescriptor.contextReceiverParameters.size
    return IrConstructorCallImpl(
        startOffset, endOffset,
        type,
        constructorSymbol,
        typeArgumentsCount = totalTypeParametersCount,
        constructorTypeArgumentsCount = totalTypeParametersCount - classTypeParametersCount,
        valueArgumentsCount = valueParametersCount,
        origin = origin
    )
}

fun IrConstructorCallImpl.Companion.fromSymbolOwner(
    startOffset: Int,
    endOffset: Int,
    type: IrType,
    constructorSymbol: IrConstructorSymbol,
    classTypeParametersCount: Int,
    origin: IrStatementOrigin? = null,
): IrConstructorCallImpl {
    val constructor = constructorSymbol.owner
    val constructorTypeParametersCount = constructor.typeParameters.size
    val totalTypeParametersCount = classTypeParametersCount + constructorTypeParametersCount
    val valueParametersCount = constructor.valueParameters.size

    return IrConstructorCallImpl(
        startOffset, endOffset,
        type,
        constructorSymbol,
        totalTypeParametersCount,
        constructorTypeParametersCount,
        valueParametersCount,
        origin
    )
}

fun IrConstructorCallImpl.Companion.fromSymbolOwner(
    startOffset: Int,
    endOffset: Int,
    type: IrType,
    constructorSymbol: IrConstructorSymbol,
    origin: IrStatementOrigin? = null,
): IrConstructorCallImpl {
    val constructedClass = constructorSymbol.owner.parentAsClass
    val classTypeParametersCount = constructedClass.typeParameters.size
    return fromSymbolOwner(startOffset, endOffset, type, constructorSymbol, classTypeParametersCount, origin)
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun IrConstructorCallImpl.Companion.fromSymbolOwner(
    type: IrType,
    constructorSymbol: IrConstructorSymbol,
    origin: IrStatementOrigin? = null,
): IrConstructorCallImpl =
    fromSymbolOwner(
        UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, constructorSymbol, constructorSymbol.owner.parentAsClass.typeParameters.size,
        origin
    )


@ObsoleteDescriptorBasedAPI
fun IrDelegatingConstructorCallImpl.Companion.fromSymbolDescriptor(
    startOffset: Int,
    endOffset: Int,
    type: IrType,
    symbol: IrConstructorSymbol,
    typeArgumentsCount: Int = symbol.descriptor.typeParametersCount,
    valueArgumentsCount: Int = symbol.descriptor.valueParameters.size + symbol.descriptor.contextReceiverParameters.size,
): IrDelegatingConstructorCallImpl =
    IrDelegatingConstructorCallImpl(startOffset, endOffset, type, symbol, typeArgumentsCount, valueArgumentsCount)

@UnsafeDuringIrConstructionAPI
fun IrDelegatingConstructorCallImpl.Companion.fromSymbolOwner(
    startOffset: Int,
    endOffset: Int,
    type: IrType,
    symbol: IrConstructorSymbol,
    typeArgumentsCount: Int = symbol.owner.allTypeParameters.size,
    valueArgumentsCount: Int = symbol.owner.valueParameters.size,
): IrDelegatingConstructorCallImpl =
    IrDelegatingConstructorCallImpl(startOffset, endOffset, type, symbol, typeArgumentsCount, valueArgumentsCount)


@ObsoleteDescriptorBasedAPI
fun IrFunctionReferenceImpl.Companion.fromSymbolDescriptor(
    startOffset: Int,
    endOffset: Int,
    type: IrType,
    symbol: IrFunctionSymbol,
    typeArgumentsCount: Int,
    reflectionTarget: IrFunctionSymbol?,
    origin: IrStatementOrigin? = null,
): IrFunctionReferenceImpl = IrFunctionReferenceImpl(
    startOffset, endOffset,
    type,
    symbol,
    typeArgumentsCount,
    symbol.descriptor.valueParameters.size + symbol.descriptor.contextReceiverParameters.size,
    reflectionTarget,
    origin
)

fun IrFunctionReferenceImpl.Companion.fromSymbolOwner(
    startOffset: Int,
    endOffset: Int,
    type: IrType,
    symbol: IrFunctionSymbol,
    typeArgumentsCount: Int,
    reflectionTarget: IrFunctionSymbol?,
    origin: IrStatementOrigin? = null,
): IrFunctionReferenceImpl = IrFunctionReferenceImpl(
    startOffset, endOffset,
    type,
    symbol,
    typeArgumentsCount,
    symbol.owner.valueParameters.size,
    reflectionTarget,
    origin
)