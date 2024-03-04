/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// This file was generated automatically. See native/swift/sir/tree-generator/Readme.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "unused")

package org.jetbrains.kotlin.sir.builder

import kotlin.contracts.*
import org.jetbrains.kotlin.sir.*
import org.jetbrains.kotlin.sir.impl.SirConstructorImpl

@SirBuilderDsl
class SirConstructorBuilder {
    var origin: SirOrigin = SirOrigin.Unknown
    var visibility: SirVisibility = SirVisibility.PUBLIC
    lateinit var kind: SirCallableKind
    var body: SirFunctionBody? = null
    var isNullable: Boolean by kotlin.properties.Delegates.notNull<Boolean>()
    val parameters: MutableList<SirParameter> = mutableListOf()
    var documentation: String? = null

    fun build(): SirConstructor {
        return SirConstructorImpl(
            origin,
            visibility,
            kind,
            body,
            isNullable,
            parameters,
            documentation,
        )
    }

}

@OptIn(ExperimentalContracts::class)
inline fun buildConstructor(init: SirConstructorBuilder.() -> Unit): SirConstructor {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }
    return SirConstructorBuilder().apply(init).build()
}

@OptIn(ExperimentalContracts::class)
inline fun buildConstructorCopy(original: SirConstructor, init: SirConstructorBuilder.() -> Unit): SirConstructor {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }
    val copyBuilder = SirConstructorBuilder()
    copyBuilder.origin = original.origin
    copyBuilder.visibility = original.visibility
    copyBuilder.kind = original.kind
    copyBuilder.body = original.body
    copyBuilder.isNullable = original.isNullable
    copyBuilder.parameters.addAll(original.parameters)
    copyBuilder.documentation = original.documentation
    return copyBuilder.apply(init).build()
}
