/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.expressions.impl

import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.declarations.IrAttributeContainer
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.typeParametersCount
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.initializeParameterArguments
import org.jetbrains.kotlin.ir.util.initializeTypeArguments
import org.jetbrains.kotlin.ir.util.render

class IrCallImpl(
    override val startOffset: Int,
    override val endOffset: Int,
    override var type: IrType,
    override var symbol: IrSimpleFunctionSymbol,
    typeArgumentsCount: Int,
    valueArgumentsCount: Int,
    override var origin: IrStatementOrigin? = null,
    override var superQualifierSymbol: IrClassSymbol? = null
) : IrCall() {
    override val typeArguments: Array<IrType?> = initializeTypeArguments(typeArgumentsCount)

    override var dispatchReceiver: IrExpression? = null
    override var extensionReceiver: IrExpression? = null
    override val valueArguments: Array<IrExpression?> = initializeParameterArguments(valueArgumentsCount)

    override var contextReceiversCount = 0

    override var attributeOwnerId: IrAttributeContainer = this
    override var originalBeforeInline: IrAttributeContainer? = null

    companion object {
        // Temporary API for compatible-compose, to be removed soon.
        // Note: It cannot be marked with @Deprecated, because some usages in kotlin compiler pick this declaration up while it still exists.
        fun fromSymbolOwner(
            startOffset: Int,
            endOffset: Int,
            type: IrType,
            symbol: IrSimpleFunctionSymbol,
            typeArgumentsCount: Int = symbol.owner.typeParameters.size,
            valueArgumentsCount: Int = symbol.owner.valueParameters.size,
            origin: IrStatementOrigin? = null,
            superQualifierSymbol: IrClassSymbol? = null,
        ) =
            IrCallImpl(startOffset, endOffset, type, symbol, typeArgumentsCount, valueArgumentsCount, origin, superQualifierSymbol)
    }
}