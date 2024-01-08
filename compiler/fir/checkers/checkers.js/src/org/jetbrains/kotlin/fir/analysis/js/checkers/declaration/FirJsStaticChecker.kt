/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.js.checkers.declaration

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.descriptors.isInterface
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.classKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.fullyExpandedClassId
import org.jetbrains.kotlin.fir.analysis.diagnostics.js.FirJsErrors
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.*
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.JsStandardClassIds
import org.jetbrains.kotlin.name.SpecialNames

object FirJsStaticChecker : FirBasicDeclarationChecker(MppCheckerKind.Common) {
    override fun check(declaration: FirDeclaration, context: CheckerContext, reporter: DiagnosticReporter) {
        if (declaration is FirConstructor) {
            // WRONG_DECLARATION_TARGET
            return
        }

        if (declaration is FirPropertyAccessor) {
            return
        }

        val declarationAnnotation = declaration.findAnnotation(JsStandardClassIds.Annotations.JsStatic, context.session)

        if (declarationAnnotation != null) {
            checkAnnotated(declaration, context, reporter, declaration.source)
        }

        fun checkIfAnnotated(it: FirDeclaration) {
            if (!it.hasAnnotation(JsStandardClassIds.Annotations.JsStatic, context.session)) {
                return
            }
            val targetSource = it.source ?: declaration.source
            checkAnnotated(it, context, reporter, targetSource, declaration as? FirProperty)
        }

        if (declaration is FirProperty) {
            declaration.getter?.let { checkIfAnnotated(it) }
            declaration.setter?.let { checkIfAnnotated(it) }
        }
    }

    private fun checkAnnotated(
        declaration: FirDeclaration,
        context: CheckerContext,
        reporter: DiagnosticReporter,
        targetSource: KtSourceElement?,
        outerProperty: FirProperty? = null,
    ) {
        if (declaration !is FirMemberDeclaration) {
            return
        }

        val container = context.getContainerAt(0) ?: return
        val containerIsAnonymous = container.classId.shortClassName == SpecialNames.ANONYMOUS

        if (
            container.classKind != ClassKind.OBJECT ||
            !container.isCompanion() && containerIsAnonymous
        ) {
            reporter.reportOn(targetSource, FirJsErrors.JS_STATIC_IN_NOT_CLASS_COMPANION, context)
        } else if (
            container.isCompanion() &&
            context.containerIsInterface(1)
        ) {
            reporter.reportOn(targetSource, FirJsErrors.JS_STATIC_IN_NOT_CLASS_COMPANION, context)
        }

        checkOverrideCannotBeStatic(declaration, context, reporter, targetSource, outerProperty)
        checkStaticOnConst(declaration, context, reporter, targetSource)
    }

    private fun checkForInterface(
        declaration: FirDeclaration,
        context: CheckerContext,
        reporter: DiagnosticReporter,
        targetSource: KtSourceElement?,
    ) {
        if (declaration !is FirCallableDeclaration) {
            return
        }

        val visibility = if (declaration is FirProperty) {
            declaration.getMinimumVisibility()
        } else {
            declaration.visibility
        }

        if (visibility != Visibilities.Public) {
            reporter.reportOn(targetSource, FirJsErrors.JS_STATIC_ON_NON_PUBLIC_MEMBER, context)
        }
    }

    private fun FirProperty.getMinimumVisibility(): Visibility {
        var minVisibility = visibility

        getter?.let {
            minVisibility = chooseMostSpecific(minVisibility, it.visibility)
        }

        setter?.let {
            minVisibility = chooseMostSpecific(minVisibility, it.visibility)
        }

        return minVisibility
    }

    private fun chooseMostSpecific(a: Visibility, b: Visibility): Visibility {
        val difference = a.compareTo(b) ?: return a
        return if (difference > 0) {
            b
        } else {
            a
        }
    }

    private fun checkOverrideCannotBeStatic(
        declaration: FirMemberDeclaration,
        context: CheckerContext,
        reporter: DiagnosticReporter,
        targetSource: KtSourceElement?,
        outerProperty: FirProperty? = null,
    ) {
        val isOverride = outerProperty?.isOverride ?: declaration.isOverride

        if (!isOverride || !context.containerIsNonCompanionObject(0)) {
            return
        }

        reporter.reportOn(targetSource, FirJsErrors.OVERRIDE_CANNOT_BE_JS_STATIC, context)
    }

    private fun checkStaticOnConst(
        declaration: FirDeclaration,
        context: CheckerContext,
        reporter: DiagnosticReporter,
        targetSource: KtSourceElement?,
    ) {
        if (declaration !is FirProperty) return
        if (declaration.isConst) reporter.reportOn(targetSource, FirJsErrors.JS_STATIC_ON_CONST, context)
    }

    private fun CheckerContext.containerIsInterface(outerLevel: Int): Boolean {
        return this.getContainerAt(outerLevel)?.classKind?.isInterface == true
    }

    private fun CheckerContext.containerIsNonCompanionObject(outerLevel: Int): Boolean {
        val containingClassSymbol = this.getContainerAt(outerLevel) ?: return false

        @OptIn(SymbolInternals::class)
        val containingClass = (containingClassSymbol.fir as? FirRegularClass) ?: return false

        return containingClass.classKind == ClassKind.OBJECT && !containingClass.isCompanion
    }

    private fun CheckerContext.getContainerAt(outerLevel: Int): FirClassLikeSymbol<*>? {
        val correction = if (this.containingDeclarations.lastOrNull() is FirProperty) {
            1
        } else {
            0
        }
        val last = this.containingDeclarations.asReversed().getOrNull(outerLevel + correction)
        return if (last is FirClassLikeDeclaration) {
            last.symbol
        } else {
            null
        }
    }

    private fun FirClassLikeSymbol<*>.isCompanion() = (this as? FirRegularClassSymbol)?.isCompanion == true

    private fun FirDeclaration.findAnnotation(classId: ClassId, session: FirSession): FirAnnotation? {
        return annotations.firstOrNull {
            it.annotationTypeRef.coneType.fullyExpandedClassId(session) == classId
        }
    }
}
