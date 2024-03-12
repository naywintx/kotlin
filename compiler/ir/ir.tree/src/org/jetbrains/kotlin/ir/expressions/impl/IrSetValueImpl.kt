/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.expressions.impl

import org.jetbrains.kotlin.ir.declarations.IrAttributeContainer
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.isAssignable

class IrSetValueImpl(
    override val startOffset: Int,
    override val endOffset: Int,
    override var type: IrType,
    override var symbol: IrValueSymbol,
    override var value: IrExpression,
    override var origin: IrStatementOrigin?
) : IrSetValue() {
    override var attributeOwnerId: IrAttributeContainer = this
    override var originalBeforeInline: IrAttributeContainer? = null

    init {
        if (symbol.isBound) {
            assert(symbol.owner.isAssignable) { "Only assignable IrValues can be set" }
        }
    }
}
