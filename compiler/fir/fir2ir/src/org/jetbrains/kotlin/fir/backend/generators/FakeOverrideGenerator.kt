/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend.generators

import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.backend.*
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.*
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.scopes.*
import org.jetbrains.kotlin.fir.scopes.impl.FirFakeOverrideGenerator
import org.jetbrains.kotlin.fir.symbols.ConeClassLikeLookupTag
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbolInternals
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.name.Name

class FakeOverrideGenerator(
    private val components: Fir2IrComponents,
    private val conversionScope: Fir2IrConversionScope
) : Fir2IrComponents by components {
    private val baseFunctionSymbols: MutableMap<IrFunction, List<FirNamedFunctionSymbol>> = mutableMapOf()
    private val basePropertySymbols: MutableMap<IrProperty, List<FirPropertySymbol>> = mutableMapOf()
    private val baseStaticFieldSymbols: MutableMap<IrField, List<FirFieldSymbol>> = mutableMapOf()

    private inline fun IrSimpleFunction.withFunction(f: IrSimpleFunction.() -> Unit) {
        conversionScope.withFunction(this, f)
    }

    private inline fun IrProperty.withProperty(f: IrProperty.() -> Unit) {
        conversionScope.withProperty(this, firProperty = null, f)
    }

    private fun IrType.containsErrorType(): Boolean {
        return when (this) {
            is IrErrorType -> true
            is IrSimpleType -> arguments.any { it is IrTypeProjection && it.type.containsErrorType() }
            else -> false
        }
    }

    fun IrClass.computeFakeOverrides(klass: FirClass, realDeclarations: Collection<FirDeclaration>) {
        val result = mutableListOf<IrDeclaration>()
        val useSiteMemberScope = klass.unsubstitutedScope()

        val superTypesCallableNames = useSiteMemberScope.getCallableNames()
        val realDeclarationSymbols = realDeclarations.mapTo(mutableSetOf(), FirDeclaration::symbol)

        for (name in superTypesCallableNames) {
            generateFakeOverridesForName(this, useSiteMemberScope, name, klass, result, realDeclarationSymbols)
        }
    }

    fun generateFakeOverridesForName(
        irClass: IrClass,
        name: Name,
        firClass: FirClass
    ): List<IrDeclaration> = buildList {
        val useSiteMemberScope = firClass.unsubstitutedScope()

        generateFakeOverridesForName(
            irClass, useSiteMemberScope, name, firClass, this,
            // This parameter is only needed for data-class methods that is irrelevant for lazy library classes
            realDeclarationSymbols = emptySet()
        )
        val staticScope = firClass.scopeProvider.getStaticMemberScopeForCallables(firClass, session, scopeSession)
        if (staticScope != null) {
            generateFakeOverridesForName(
                irClass, staticScope, name, firClass, this,
                // This parameter is only needed for data-class methods that is irrelevant for lazy library classes
                realDeclarationSymbols = emptySet()
            )
        }
    }

    private fun generateFakeOverridesForName(
        irClass: IrClass,
        useSiteOrStaticScope: FirScope,
        name: Name,
        firClass: FirClass,
        result: MutableList<IrDeclaration>?,
        realDeclarationSymbols: Set<FirBasedSymbol<*>>
    ) {
        val isLocal = firClass !is FirRegularClass || firClass.isLocal
        useSiteOrStaticScope.processFunctionsByName(name) { functionSymbol ->
            @OptIn(LeakedDeclarationCaches::class)
            createFakeOverriddenIfNeeded(
                firClass, irClass, isLocal, functionSymbol,
                declarationStorage::getCachedIrFunction,
                declarationStorage::createAndCacheIrFunction,
                createFakeOverrideSymbol = { firFunction, callableSymbol ->
                    val symbol = FirFakeOverrideGenerator.createSymbolForSubstitutionOverride(callableSymbol, firClass.symbol.classId)
                    FirFakeOverrideGenerator.createSubstitutionOverrideFunction(
                        session, symbol, firFunction,
                        derivedClassLookupTag = firClass.symbol.toLookupTag(),
                        newDispatchReceiverType = firClass.defaultType(),
                        origin = FirDeclarationOrigin.SubstitutionOverride.DeclarationSite,
                        isExpect = (firClass as? FirRegularClass)?.isExpect == true || firFunction.isExpect
                    )
                },
                baseFunctionSymbols,
                result,
                containsErrorTypes = { irFunction ->
                    irFunction.returnType.containsErrorType() || irFunction.valueParameters.any { it.type.containsErrorType() }
                },
                realDeclarationSymbols,
                FirTypeScope::getDirectOverriddenFunctions,
                useSiteOrStaticScope,
            )
        }

        useSiteOrStaticScope.processPropertiesByName(name) { propertyOrFieldSymbol ->
            when (propertyOrFieldSymbol) {
                is FirPropertySymbol -> {
                    @OptIn(LeakedDeclarationCaches::class)
                    createFakeOverriddenIfNeeded(
                        firClass, irClass, isLocal, propertyOrFieldSymbol,
                        declarationStorage::getCachedIrProperty,
                        declarationStorage::createAndCacheIrProperty,
                        createFakeOverrideSymbol = { firProperty, callableSymbol ->
                            val symbolForOverride =
                                FirFakeOverrideGenerator.createSymbolForSubstitutionOverride(callableSymbol, firClass.symbol.classId)
                            FirFakeOverrideGenerator.createSubstitutionOverrideProperty(
                                session, symbolForOverride, firProperty,
                                derivedClassLookupTag = firClass.symbol.toLookupTag(),
                                newDispatchReceiverType = firClass.defaultType(),
                                origin = FirDeclarationOrigin.SubstitutionOverride.DeclarationSite,
                                isExpect = (firClass as? FirRegularClass)?.isExpect == true || firProperty.isExpect
                            )
                        },
                        basePropertySymbols,
                        result,
                        containsErrorTypes = { irProperty ->
                            irProperty.backingField?.type?.containsErrorType() == true ||
                                    irProperty.getter?.returnType?.containsErrorType() == true
                        },
                        realDeclarationSymbols,
                        FirTypeScope::getDirectOverriddenProperties,
                        useSiteOrStaticScope,
                    )
                }

                is FirFieldSymbol -> {
                    if (!propertyOrFieldSymbol.isStatic) return@processPropertiesByName
                    createFakeOverriddenIfNeeded(
                        firClass, irClass, isLocal, propertyOrFieldSymbol,
                        { field, _, _ -> declarationStorage.getCachedIrFieldStaticFakeOverrideByDeclaration(field) },
                        { field, irParent, _, _ ->
                            declarationStorage.getOrCreateIrField(field, irParent)
                        },
                        createFakeOverrideSymbol = { firField, _ ->
                            FirFakeOverrideGenerator.createSubstitutionOverrideField(
                                session, firField,
                                derivedClassLookupTag = firClass.symbol.toLookupTag(),
                                newReturnType = firField.returnTypeRef.coneType,
                                origin = FirDeclarationOrigin.SubstitutionOverride.DeclarationSite,
                            )
                        },
                        baseStaticFieldSymbols,
                        result,
                        containsErrorTypes = { irField -> irField.type.containsErrorType() },
                        realDeclarationSymbols,
                        computeDirectOverridden = { emptyList() },
                        useSiteOrStaticScope,
                    )
                }

                else -> {
                }
            }
        }
    }

    internal fun getBaseSymbolsForFakeOverride(fakeOverride: IrDeclaration): List<FirCallableSymbol<*>> {
        return when (fakeOverride) {
            is IrFunction -> baseFunctionSymbols[fakeOverride]
            is IrProperty -> basePropertySymbols[fakeOverride]
            is IrField -> baseStaticFieldSymbols[fakeOverride]
            else -> null
        }.orEmpty()
    }

    internal fun calcBaseSymbolsForFakeOverrideFunction(
        klass: FirClass,
        fakeOverride: IrSimpleFunction,
        originalSymbol: FirNamedFunctionSymbol,
    ) {
        val scope = klass.unsubstitutedScope()
        val classLookupTag = klass.symbol.toLookupTag()
        val baseFirSymbolsForFakeOverride =
            if (originalSymbol.shouldHaveComputedBaseSymbolsForClass(classLookupTag)) {
                computeBaseSymbols(originalSymbol, FirTypeScope::getDirectOverriddenFunctions, scope, classLookupTag)
            } else {
                listOf(originalSymbol)
            }
        baseFunctionSymbols[fakeOverride] = baseFirSymbolsForFakeOverride
    }

    internal fun calcBaseSymbolsForFakeOverrideProperty(
        klass: FirClass,
        fakeOverride: IrProperty,
        originalSymbol: FirPropertySymbol,
    ) {
        val scope = klass.unsubstitutedScope()
        val classLookupTag = klass.symbol.toLookupTag()
        val baseFirSymbolsForFakeOverride =
            if (originalSymbol.shouldHaveComputedBaseSymbolsForClass(classLookupTag)) {
                computeBaseSymbols(originalSymbol, FirTypeScope::getDirectOverriddenProperties, scope, classLookupTag)
            } else {
                listOf(originalSymbol)
            }
        basePropertySymbols[fakeOverride] = baseFirSymbolsForFakeOverride
    }

    private fun FirCallableSymbol<*>.shouldHaveComputedBaseSymbolsForClass(classLookupTag: ConeClassLikeLookupTag): Boolean =
        fir.origin.fromSupertypes && dispatchReceiverClassLookupTagOrNull() == classLookupTag

    private inline fun <reified D : FirCallableDeclaration, reified S : FirCallableSymbol<D>, reified I : IrDeclaration> createFakeOverriddenIfNeeded(
        klass: FirClass,
        irClass: IrClass,
        isLocal: Boolean,
        originalSymbol: FirCallableSymbol<*>,
        cachedIrDeclaration: (firDeclaration: D, dispatchReceiverLookupTag: ConeClassLikeLookupTag?, () -> IdSignature?) -> I?,
        createIrDeclaration: (firDeclaration: D, irParent: IrClass, origin: IrDeclarationOrigin, isLocal: Boolean) -> I,
        createFakeOverrideSymbol: (firDeclaration: D, baseSymbol: S) -> S,
        baseSymbols: MutableMap<I, List<S>>,
        result: MutableList<in I>?,
        containsErrorTypes: (I) -> Boolean,
        realDeclarationSymbols: Set<FirBasedSymbol<*>>,
        computeDirectOverridden: FirTypeScope.(S) -> List<S>,
        scope: FirScope,
    ) {
        if (originalSymbol !is S) return
        val classLookupTag = klass.symbol.toLookupTag()
        val originalDeclaration = originalSymbol.fir

        if (originalSymbol.containingClassLookupTag() == classLookupTag && !originalDeclaration.origin.fromSupertypes) return
        // Data classes' methods from Any (toString/equals/hashCode) are not handled by the line above because they have Any-typed dispatch receiver
        // (there are no special FIR method for them, it's just fake overrides)
        // But they are treated differently in IR (real declarations have already been declared before) and such methods are present among realDeclarationSymbols
        if (originalSymbol in realDeclarationSymbols) return

        val baseSymbol = originalSymbol.unwrapSubstitutionAndIntersectionOverrides() as S

        if (!session.visibilityChecker.isVisibleForOverriding(klass.moduleData, klass.symbol, baseSymbol.fir)) return

        val (fakeOverrideFirDeclaration, baseFirSymbolsForFakeOverride) = when {
            originalSymbol.shouldHaveComputedBaseSymbolsForClass(classLookupTag) -> {
                // Substitution or intersection case
                // We have already a FIR declaration for such fake override
                originalDeclaration to computeBaseSymbols(originalSymbol, computeDirectOverridden, scope, classLookupTag)
            }
            originalDeclaration.allowsToHaveFakeOverride -> {
                // Trivial fake override case
                // We've got no relevant declaration in FIR world for such a fake override in current class, thus we're creating it here
                val fakeOverrideSymbol = createFakeOverrideSymbol(originalDeclaration, baseSymbol)
                declarationStorage.saveFakeOverrideInClass(irClass, originalDeclaration, fakeOverrideSymbol.fir)
                fakeOverrideSymbol.fir to listOf(originalSymbol)
            }
            else -> {
                return
            }
        }
        val irDeclaration = cachedIrDeclaration(fakeOverrideFirDeclaration, null) {
            // Sometimes we can have clashing here when FIR substitution/intersection override
            // have the same signature.
            // Now we avoid this problem by signature caching,
            // so both FIR overrides correspond to one IR fake override
            signatureComposer.composeSignature(fakeOverrideFirDeclaration)
        }?.takeIf { it.parent == irClass }
            ?: createIrDeclaration(
                fakeOverrideFirDeclaration,
                irClass,
                IrDeclarationOrigin.FAKE_OVERRIDE,
                isLocal
            )
        if (containsErrorTypes(irDeclaration)) {
            return
        }
        baseSymbols[irDeclaration] = baseFirSymbolsForFakeOverride

        result?.add(irDeclaration)
    }

    private inline fun <D : FirCallableDeclaration, reified S : FirCallableSymbol<D>> createFirFakeOverride(
        klass: FirClass,
        irClass: IrClass,
        originalSymbol: S,
        createFakeOverrideSymbol: (firDeclaration: D, baseSymbol: S) -> S,
        computeDirectOverridden: FirTypeScope.(S) -> List<S>,
        scope: FirTypeScope,
    ): Pair<D, List<S>>? {
        val classLookupTag = klass.symbol.toLookupTag()
        val originalDeclaration = originalSymbol.fir
        val baseSymbol = originalSymbol.unwrapSubstitutionAndIntersectionOverrides() as S
        return when {
            originalSymbol.shouldHaveComputedBaseSymbolsForClass(classLookupTag) -> {
                // Substitution or intersection case
                // We have already a FIR declaration for such fake override
                originalDeclaration to computeBaseSymbols(
                    originalSymbol,
                    computeDirectOverridden,
                    scope, classLookupTag
                )
            }
            originalDeclaration.allowsToHaveFakeOverride -> {
                // Trivial fake override case
                // We've got no relevant declaration in FIR world for such a fake override in current class, thus we're creating it here
                val fakeOverrideSymbol = createFakeOverrideSymbol(originalDeclaration, baseSymbol)
                declarationStorage.saveFakeOverrideInClass(irClass, originalDeclaration, fakeOverrideSymbol.fir)
                classifierStorage.preCacheTypeParameters(originalDeclaration, irClass.symbol)
                fakeOverrideSymbol.fir to listOf(originalSymbol)
            }
            else -> {
                null
            }
        }
    }

    internal fun createFirFunctionFakeOverride(
        klass: FirClass,
        irClass: IrClass,
        originalSymbol: FirNamedFunctionSymbol,
        scope: FirTypeScope
    ) = createFirFakeOverride(
        klass, irClass, originalSymbol,
        createFakeOverrideSymbol = { firFunction, callableSymbol ->
            FirFakeOverrideGenerator.createSubstitutionOverrideFunction(
                session, callableSymbol, firFunction,
                derivedClassLookupTag = klass.symbol.toLookupTag(),
                newDispatchReceiverType = klass.defaultType(),
                origin = FirDeclarationOrigin.SubstitutionOverride.DeclarationSite,
                isExpect = (klass as? FirRegularClass)?.isExpect == true || firFunction.isExpect
            )
        },
        computeDirectOverridden = FirTypeScope::getDirectOverriddenFunctions,
        scope
    )

    internal fun createFirPropertyFakeOverride(
        klass: FirClass,
        irClass: IrClass,
        originalSymbol: FirPropertySymbol,
        scope: FirTypeScope
    ) = createFirFakeOverride(
        klass, irClass, originalSymbol,
        createFakeOverrideSymbol = { firProperty, callableSymbol ->
            FirFakeOverrideGenerator.createSubstitutionOverrideProperty(
                session, callableSymbol, firProperty,
                derivedClassLookupTag = klass.symbol.toLookupTag(),
                newDispatchReceiverType = klass.defaultType(),
                origin = FirDeclarationOrigin.SubstitutionOverride.DeclarationSite,
                isExpect = (klass as? FirRegularClass)?.isExpect == true || firProperty.isExpect
            )
        },
        computeDirectOverridden = FirTypeScope::getDirectOverriddenProperties,
        scope
    )

    private inline fun <reified S : FirCallableSymbol<*>> computeBaseSymbols(
        symbol: S,
        directOverridden: FirTypeScope.(S) -> List<S>,
        scope: FirScope,
        containingClass: ConeClassLikeLookupTag,
    ): List<S> {
        if (symbol.fir.origin is FirDeclarationOrigin.SubstitutionOverride) {
            return listOf(symbol.originalForSubstitutionOverride!!)
        }

        if (scope !is FirTypeScope) {
            return emptyList()
        }

        return scope.directOverridden(symbol).map {
            // Unwrapping should happen only for fake overrides members from the same class, not from supertypes
            if (it.fir.isSubstitutionOverride && it.dispatchReceiverClassLookupTagOrNull() == containingClass)
                it.originalForSubstitutionOverride!!
            else
                it
        }
    }

    internal fun getOverriddenSymbolsForFakeOverride(function: IrSimpleFunction): List<IrSimpleFunctionSymbol>? {
        val baseSymbols = baseFunctionSymbols[function] ?: return null
        return getOverriddenSymbolsInSupertypes(
            function,
            baseSymbols
        ) { declarationStorage.getIrFunctionSymbol(it) as IrSimpleFunctionSymbol }
    }

    internal fun getOverriddenSymbolsInSupertypes(
        overridden: FirNamedFunctionSymbol,
        superClasses: Set<IrClass>,
    ): List<IrSimpleFunctionSymbol> {
        return getOverriddenSymbolsInSupertypes(
            overridden,
            superClasses
        ) { declarationStorage.getIrFunctionSymbol(it) as IrSimpleFunctionSymbol }
    }

    internal fun getOverriddenSymbolsInSupertypes(
        overridden: FirPropertySymbol,
        superClasses: Set<IrClass>,
    ): List<IrPropertySymbol> {
        return getOverriddenSymbolsInSupertypes(
            overridden, superClasses
        ) { declarationStorage.getIrPropertySymbol(it) as IrPropertySymbol }
    }

    internal fun getOverriddenSymbolsForFakeOverride(property: IrProperty): List<IrPropertySymbol>? {
        val baseSymbols = basePropertySymbols[property] ?: return null
        return getOverriddenSymbolsInSupertypes(
            property,
            baseSymbols
        ) { declarationStorage.getIrPropertySymbol(it) as IrPropertySymbol }
    }

    @OptIn(IrSymbolInternals::class)
    private fun <I : IrDeclaration, S : IrSymbol, F : FirCallableSymbol<*>> getOverriddenSymbolsInSupertypes(
        declaration: I,
        baseSymbols: List<F>,
        irProducer: (F) -> S,
    ): List<S> {
        val irClass = declaration.parentAsClass
        val superClasses = irClass.superTypes.mapNotNull { it.classifierOrNull?.owner as? IrClass }.toSet()

        return baseSymbols.flatMap { overridden ->
            getOverriddenSymbolsInSupertypes(overridden, superClasses, irProducer)
        }.distinct()
    }

    @OptIn(IrSymbolInternals::class)
    private fun <F : FirCallableSymbol<*>, S : IrSymbol> getOverriddenSymbolsInSupertypes(
        overridden: F,
        superClasses: Set<IrClass>,
        irProducer: (F) -> S
    ): List<S> {
        val overriddenContainingClass =
            overridden.containingClassLookupTag()?.toSymbol(session)?.fir as? FirClass ?: return emptyList()

        val overriddenContainingIrClass =
            declarationStorage.classifierStorage.getOrCreateIrClass(overriddenContainingClass.symbol).symbol.owner as? IrClass
                ?: return emptyList()

        return superClasses.mapNotNull { superClass ->
            if (superClass == overriddenContainingIrClass ||
                // Note: Kotlin static scopes cannot find base symbol in this case, so we have to fallback to the very base declaration
                overridden.isStatic && superClass.origin != IrDeclarationOrigin.IR_EXTERNAL_JAVA_DECLARATION_STUB
            ) {
                // `overridden` was a FIR declaration in some supertypes
                irProducer(overridden)
            } else {
                // There were no FIR declaration in supertypes, but we know that we have fake overrides in some of them
                declarationStorage.getFakeOverrideInClass(superClass, overridden.fir)?.let { fakeOverrideInClass ->
                    @Suppress("UNCHECKED_CAST")
                    irProducer(fakeOverrideInClass.symbol as F)
                }
            }
        }
    }

    fun bindOverriddenSymbols(declarations: List<IrDeclaration>) {
        for (declaration in declarations) {
            if (declaration.origin != IrDeclarationOrigin.FAKE_OVERRIDE) continue
            when (declaration) {
                is IrSimpleFunction -> {
                    val baseSymbols = getOverriddenSymbolsForFakeOverride(declaration)!!
                    declaration.withFunction {
                        overriddenSymbols = baseSymbols
                    }
                }
                is IrProperty -> {
                    val baseSymbols = basePropertySymbols[declaration]!!
                    declaration.withProperty {
                        discardAccessorsAccordingToBaseVisibility(baseSymbols)
                        setOverriddenSymbolsForProperty(declarationStorage, declaration.isVar, baseSymbols)
                    }
                }
            }
        }
    }

    private fun IrProperty.discardAccessorsAccordingToBaseVisibility(baseSymbols: List<FirPropertySymbol>) {
        for (baseSymbol in baseSymbols) {
            val unwrappedSymbol = baseSymbol.unwrapFakeOverrides()
            val unwrappedProperty = unwrappedSymbol.fir
            // Do not create fake overrides for accessors if not allowed to do so, e.g., private lateinit var.
            if (!(unwrappedProperty.getter?.allowsToHaveFakeOverride ?: unwrappedProperty.allowsToHaveFakeOverride)) {
                getter = null
            }
            // or private setter
            if (!(unwrappedProperty.setter?.allowsToHaveFakeOverride ?: unwrappedProperty.allowsToHaveFakeOverride)) {
                setter = null
            }
        }
    }

    @OptIn(IrSymbolInternals::class)
    private fun IrProperty.setOverriddenSymbolsForProperty(
        declarationStorage: Fir2IrDeclarationStorage,
        isVar: Boolean,
        firOverriddenSymbols: List<FirPropertySymbol>
    ): IrProperty {
        val overriddenIrSymbols = getOverriddenSymbolsInSupertypes(this, firOverriddenSymbols) {
            declarationStorage.getIrPropertySymbol(it) as IrPropertySymbol
        }
        val overriddenIrProperties = overriddenIrSymbols.map { it.owner }

        getter?.apply {
            overriddenSymbols = overriddenIrProperties.mapNotNull { it.getter?.symbol }
        }
        if (isVar) {
            setter?.apply {
                overriddenSymbols = overriddenIrProperties.mapNotNull { it.setter?.symbol }
            }
        }
        overriddenSymbols = overriddenIrSymbols
        return this
    }
}

context(Fir2IrComponents)
@OptIn(IrSymbolInternals::class)
internal fun FirProperty.generateOverriddenAccessorSymbols(containingClass: FirClass, isGetter: Boolean): List<IrSimpleFunctionSymbol> {
    val scope = containingClass.unsubstitutedScope()
    scope.processPropertiesByName(name) {}
    val overriddenSet = mutableSetOf<IrSimpleFunctionSymbol>()
    val superClasses = containingClass.getSuperTypesAsIrClasses()

    scope.processOverriddenPropertiesFromSuperClasses(symbol, containingClass) { overriddenSymbol ->
        if (!session.visibilityChecker.isVisibleForOverriding(
                candidateInDerivedClass = symbol.fir, candidateInBaseClass = overriddenSymbol.fir
            )
        ) {
            return@processOverriddenPropertiesFromSuperClasses ProcessorAction.NEXT
        }

        for (overriddenIrPropertySymbol in fakeOverrideGenerator.getOverriddenSymbolsInSupertypes(overriddenSymbol, superClasses)) {
            val overriddenIrAccessorSymbol =
                if (isGetter) overriddenIrPropertySymbol.owner.getter?.symbol
                else overriddenIrPropertySymbol.owner.setter?.symbol
            if (overriddenIrAccessorSymbol != null) {
                assert(overriddenIrAccessorSymbol != symbol) { "Cannot add property $overriddenIrAccessorSymbol to its own overriddenSymbols" }
                overriddenSet += overriddenIrAccessorSymbol
            }
        }
        ProcessorAction.NEXT
    }
    return overriddenSet.toList()
}

context(Fir2IrComponents)
internal fun FirProperty.generateOverriddenPropertySymbols(containingClass: FirClass): List<IrPropertySymbol> {
    val superClasses = containingClass.getSuperTypesAsIrClasses()
    val overriddenSet = mutableSetOf<IrPropertySymbol>()

    processOverriddenPropertySymbols(containingClass) {
        for (overridden in fakeOverrideGenerator.getOverriddenSymbolsInSupertypes(it, superClasses)) {
            assert(overridden != symbol) { "Cannot add property $overridden to its own overriddenSymbols" }
            overriddenSet += overridden
        }
    }

    return overriddenSet.toList()
}

context(Fir2IrComponents)
internal fun FirSimpleFunction.generateOverriddenFunctionSymbols(containingClass: FirClass): List<IrSimpleFunctionSymbol> {
    val superClasses = containingClass.getSuperTypesAsIrClasses()
    val overriddenSet = mutableSetOf<IrSimpleFunctionSymbol>()

    processOverriddenFunctionSymbols(containingClass) {
        for (overridden in fakeOverrideGenerator.getOverriddenSymbolsInSupertypes(it, superClasses)) {
            assert(overridden != symbol) { "Cannot add function $overridden to its own overriddenSymbols" }
            overriddenSet += overridden
        }
    }

    return overriddenSet.toList()
}

context(Fir2IrComponents)
@OptIn(IrSymbolInternals::class)
private fun FirClass.getSuperTypesAsIrClasses(): Set<IrClass> {
    val irClass = declarationStorage.classifierStorage.getOrCreateIrClass(symbol)

    return irClass.superTypes.mapNotNull { it.classifierOrNull?.owner as? IrClass }.toSet()
}
