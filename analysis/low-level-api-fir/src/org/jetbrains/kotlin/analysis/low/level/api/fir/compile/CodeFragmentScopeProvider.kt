/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.compile

import com.intellij.psi.impl.compiled.ClsTypeElementImpl
import com.intellij.psi.impl.compiled.SignatureParsing
import com.intellij.psi.impl.compiled.StubBuildingVisitor
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.analysis.providers.ForeignValueProviderService
import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fakeElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.FirSessionComponent
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.builder.buildProperty
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.java.JavaTypeParameterStack
import org.jetbrains.kotlin.fir.java.resolveIfJavaType
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.fir.scopes.impl.FirLocalScope
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.jvm.buildJavaTypeRef
import org.jetbrains.kotlin.load.java.structure.impl.JavaTypeImpl
import org.jetbrains.kotlin.load.java.structure.impl.source.JavaElementSourceFactory
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.org.objectweb.asm.Type
import java.text.StringCharacterIterator

val FirSession.codeFragmentScopeProvider: CodeFragmentScopeProvider by FirSession.sessionComponentAccessor()

private object ForeignValueMarkerDataKey : FirDeclarationDataKey()

var FirProperty.foreignValueMarker: Boolean? by FirDeclarationDataRegistry.data(ForeignValueMarkerDataKey)

class CodeFragmentScopeProvider(private val session: FirSession) : FirSessionComponent {
    private val foreignValueProvider = ForeignValueProviderService.getInstance()

    private val typeCache = session.firCachesFactory.createCache<String, FirTypeRef, FirCodeFragment> { typeDescriptor, codeFragment ->
        getPrimitiveType(typeDescriptor, session)?.let { return@createCache it }

        val project = codeFragment.psi?.project!!
        val javaElementSourceFactory = JavaElementSourceFactory.getInstance(project)

        val signatureIterator = StringCharacterIterator(typeDescriptor)
        val typeString = SignatureParsing.parseTypeString(signatureIterator, StubBuildingVisitor.GUESSING_MAPPER)
        val psiType = ClsTypeElementImpl(codeFragment.psi!!, typeString, '\u0000').type
        val javaType = JavaTypeImpl.create(psiType, javaElementSourceFactory.createTypeSource(psiType))
        val source = codeFragment.source?.fakeElement(KtFakeSourceElementKind.Enhancement)

        val javaTypeRef = buildJavaTypeRef {
            annotationBuilder = { emptyList() }
            type = javaType
            this.source = source
        }

        javaTypeRef.resolveIfJavaType(session, JavaTypeParameterStack.EMPTY, source)
    }

    fun getExtraScopes(codeFragment: FirCodeFragment, ktCodeFragment: KtCodeFragment): List<FirLocalScope> {
        val foreignValues = foreignValueProvider?.getForeignValues(ktCodeFragment)?.takeUnless { it.isEmpty() } ?: return emptyList()
        return listOf(getForeignValuesScope(codeFragment, foreignValues))
    }

    private fun getForeignValuesScope(codeFragment: FirCodeFragment, foreignValues: Map<String, String>): FirLocalScope {
        var result = FirLocalScope(session)

        for ((variableNameString, typeDescriptor) in foreignValues) {
            val variableName = Name.identifier(variableNameString)

            val variable = buildProperty {
                resolvePhase = FirResolvePhase.BODY_RESOLVE
                moduleData = session.moduleData
                origin = FirDeclarationOrigin.Source
                status = FirResolvedDeclarationStatusImpl(Visibilities.Local, Modality.FINAL, EffectiveVisibility.Local)
                returnTypeRef = typeCache.getValue(typeDescriptor, codeFragment)
                deprecationsProvider = EmptyDeprecationsProvider
                name = variableName
                isVar = false
                symbol = FirPropertySymbol(variableName)
                isLocal = true
            }

            variable.foreignValueMarker = true

            result = result.storeVariable(variable, session)
        }

        return result
    }
}

private fun getPrimitiveType(typeDescriptor: String, session: FirSession): FirTypeRef? {
    val asmType = Type.getType(typeDescriptor)
    return when (asmType.sort) {
        Type.VOID -> session.builtinTypes.unitType
        Type.BOOLEAN -> session.builtinTypes.booleanType
        Type.CHAR -> session.builtinTypes.charType
        Type.BYTE -> session.builtinTypes.byteType
        Type.SHORT -> session.builtinTypes.shortType
        Type.INT -> session.builtinTypes.intType
        Type.FLOAT -> session.builtinTypes.floatType
        Type.LONG -> session.builtinTypes.longType
        Type.DOUBLE -> session.builtinTypes.doubleType
        else -> null
    }
}