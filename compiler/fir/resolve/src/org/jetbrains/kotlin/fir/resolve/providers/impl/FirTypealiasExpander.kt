/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.providers.impl

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeAliasSymbol
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.toSymbol
import org.jetbrains.kotlin.fir.visitors.FirDefaultTransformer
import org.jetbrains.kotlin.fir.visitors.transformSingle

class FirTypealiasExpander(private val session: FirSession) : FirDefaultTransformer<Unit>() {
    override fun <E : FirElement> transformElement(element: E, data: Unit): E = element.also {
        it.transformChildren(this@FirTypealiasExpander, data)
    }

    override fun transformResolvedTypeRef(resolvedTypeRef: FirResolvedTypeRef, data: Unit): FirTypeRef {
        if (resolvedTypeRef.type.toSymbol(session) !is FirTypeAliasSymbol) {
            return resolvedTypeRef
        }

        return buildResolvedTypeRef {
            source = resolvedTypeRef.source
            type = resolvedTypeRef.type.fullyExpandedType(session)
            delegatedTypeRef = resolvedTypeRef // Keep the info about the typealias
            annotations.addAll(resolvedTypeRef.annotations.transformAll(data))
        }
    }

    private fun <T : FirElement> List<T>.transformAll(data: Unit): List<T> {
        return map { it.transform(this@FirTypealiasExpander, data) }
    }

    override fun transformTypeAlias(typeAlias: FirTypeAlias, data: Unit): FirStatement {
        return typeAlias.apply {
            typeAlias.annotations.transformAll(data)
            replaceExpandedTypeRef(typeAlias.expandedTypeRef.transform(this@FirTypealiasExpander, data))
        }
    }

    override fun transformReceiverParameter(receiverParameter: FirReceiverParameter, data: Unit): FirReceiverParameter {
        return receiverParameter.apply {
            receiverParameter.annotations.transformAll(data)
            replaceTypeRef(receiverParameter.typeRef.transform(this@FirTypealiasExpander, data))
        }
    }

    override fun transformContextReceiver(contextReceiver: FirContextReceiver, data: Unit): FirContextReceiver {
        return contextReceiver.apply {
            replaceTypeRef(contextReceiver.typeRef.transform(this@FirTypealiasExpander, data))
        }
    }

    override fun transformTypeParameter(typeParameter: FirTypeParameter, data: Unit): FirTypeParameterRef {
        return typeParameter.apply {
            replaceBounds(bounds.transformAll(data))
            annotations.transformAll(data)
        }
    }

    override fun transformSimpleFunction(simpleFunction: FirSimpleFunction, data: Unit): FirStatement {
        return simpleFunction.apply {
            receiverParameter?.transformSingle(this@FirTypealiasExpander, data)
            contractDescription.transformSingle(this@FirTypealiasExpander, data)
            valueParameters.transformAll(data)
            typeParameters.transformAll(data)
            annotations.transformAll(data)
            replaceReturnTypeRef(simpleFunction.returnTypeRef.transform(this@FirTypealiasExpander, data))
        }
    }

    override fun transformProperty(property: FirProperty, data: Unit): FirStatement {
        return property.apply {
            annotations.transformAll(data)
            replaceReturnTypeRef(property.returnTypeRef.transform(this@FirTypealiasExpander, data))
            typeParameters.transformAll(data)
            getter?.transformSingle(this@FirTypealiasExpander, data)
            setter?.transformSingle(this@FirTypealiasExpander, data)
            backingField?.transformSingle(this@FirTypealiasExpander, data)
        }
    }

    override fun transformField(field: FirField, data: Unit): FirStatement {
        return field.apply {
            annotations.transformAll(data)
            replaceReturnTypeRef(field.returnTypeRef.transform(this@FirTypealiasExpander, data))
            typeParameters.transformAll(data)
        }
    }

    override fun transformBackingField(backingField: FirBackingField, data: Unit): FirStatement {
        return backingField.apply {
            annotations.transformAll(data)
            replaceReturnTypeRef(backingField.returnTypeRef.transform(this@FirTypealiasExpander, data))
            typeParameters.transformAll(data)
        }
    }
}
