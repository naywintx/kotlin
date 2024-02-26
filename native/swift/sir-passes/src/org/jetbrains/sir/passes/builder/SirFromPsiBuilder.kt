/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.sir.passes.builder

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isPublic
import org.jetbrains.kotlin.sir.*
import org.jetbrains.kotlin.sir.builder.*
import org.jetbrains.kotlin.sir.util.SirSwiftModule


public fun KtAnalysisSession.buildSirDeclarationList(from: KtElement): List<SirDeclaration> {
    val res = mutableListOf<SirDeclaration>()
    from.accept(Visitor(res, this))
    return res.toList()
}

private class Visitor(
    private val res: MutableList<SirDeclaration>,
    private val analysisSession: KtAnalysisSession
) : KtTreeVisitorVoid() {

    override fun visitClassOrObject(classOrObject: KtClassOrObject) {
        // we do not handle inner declarations of class currently. No need to go deeper.
        // super.visitClassOrObject(classOrObject)
        with(analysisSession) {
            classOrObject.process {
                buildSirClassFromPsi(classOrObject)
            }
        }
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        with(analysisSession) {
            function.process {
                buildSirFunctionFromPsi(function)
            }
        }
    }

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)
        with(analysisSession) {
            property.process {
                buildSirVariableFromPsi(property)
            }
        }
    }

    private inline fun <T : KtDeclaration> T.process(converter: T.() -> SirDeclaration?) {
        this.takeIf { it.isPublic }
            ?.let(converter)
            ?.let { res.add(it) }
    }
}

context(KtAnalysisSession)
internal fun buildSirClassFromPsi(classOrObject: KtClassOrObject): SirNamedDeclaration? {
    val symbol = classOrObject
        .getNamedClassOrObjectSymbol()
        ?.takeIf { it.isConsumableBySirBuilder() }
        ?: return null // todo: error handling strategy: KT-65980
    return buildClass {
        name = classOrObject.name ?: "UNKNOWN_CLASS" // todo: error handling strategy: KT-65980
        origin = KotlinSource(symbol)

        documentation = classOrObject.docComment?.text
    }
}

context(KtAnalysisSession)
internal fun buildSirFunctionFromPsi(function: KtNamedFunction): SirFunction = buildFunction {
    val symbol = function.getFunctionLikeSymbol()
    val callableId = symbol.callableIdIfNonLocal
    origin = KotlinSource(symbol)

    // this check is not ideal. We assume that there will be no further moves of the declaration,
    // especially that there will be no changes in "static" quality.
    // That is not fully true. For example, during KT-65127
    // we may end up removing the whole enum structure at top of the declaration, and that will
    // lift the need to keep it static.
    // this problem will be addressed somewhere in the future
    val isRootPackage = callableId?.packageName?.isRoot
    isStatic = if (isRootPackage == true) false else function.isTopLevel

    name = callableId?.callableName?.asString() ?: "UNKNOWN_FUNCTION_NAME"

    symbol.valueParameters.mapTo(parameters) {
        SirParameter(
            argumentName = it.name.asString(),
            type = buildSirNominalType(it.returnType)
        )
    }
    returnType = buildSirNominalType(symbol.returnType)
    documentation = function.docComment?.text
}

context(KtAnalysisSession)
internal fun buildSirVariableFromPsi(variable: KtProperty): SirVariable = buildVariable {
    val symbol = variable.getVariableSymbol()
    val callableId = symbol.callableIdIfNonLocal
    origin = KotlinSource(symbol)

    // this check is not ideal. We assume that there will be no further moves of the declaration,
    // especially that there will be no changes in "static" quality.
    // That is not fully true. For example, during KT-65127
    // we may end up removing the whole enum structure at top of the declaration, and that will
    // lift the need to keep it static.
    // this problem will be addressed somewhere in the future
    val isRootPackage = callableId?.packageName?.isRoot
    isStatic = if (isRootPackage == true) false else variable.isTopLevel

    name = callableId?.callableName?.asString() ?: "UNKNOWN_VARIABLE_NAME"

    type = buildSirNominalType(symbol.returnType)

    getter = buildGetter {}
    setter = if (variable.isVar) buildSetter {} else null

    documentation = variable.docComment?.text
}.also {
    it.getter.parent = it
    it.setter?.parent = it
}

public data class KotlinSource(
    val symbol: KtSymbol,
) : SirOrigin.Foreign.SourceCode


context(KtAnalysisSession)
private fun buildSirNominalType(it: KtType): SirNominalType = SirNominalType(
    when {
        it.isUnit -> SirSwiftModule.void

        it.isByte -> SirSwiftModule.int8
        it.isShort -> SirSwiftModule.int16
        it.isInt -> SirSwiftModule.int32
        it.isLong -> SirSwiftModule.int64

        it.isUByte -> SirSwiftModule.uint8
        it.isUShort -> SirSwiftModule.uint16
        it.isUInt -> SirSwiftModule.uint32
        it.isULong -> SirSwiftModule.uint64

        it.isBoolean -> SirSwiftModule.bool

        it.isDouble -> SirSwiftModule.double
        it.isFloat -> SirSwiftModule.float
        else ->
            throw IllegalArgumentException("Swift Export does not support argument type: ${it.asStringForDebugging()}")
    }
)

context(KtAnalysisSession)
private fun KtNamedClassOrObjectSymbol.isConsumableBySirBuilder(): Boolean =
    classKind == KtClassKind.CLASS
            && (superTypes.count() == 1 && superTypes.first().isAny) // Every class has Any as a superclass
            && classIdIfNonLocal?.packageFqName?.isRoot != false
            && !isData
            && !isInline
            && modality == Modality.FINAL
