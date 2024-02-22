/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors.SELF_CALL_IN_OBJECT_CONSTRUCTOR
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.visitors.FirVisitor

object FirObjectChecker : FirRegularClassChecker(MppCheckerKind.Common) {
    override fun check(declaration: FirRegularClass, context: CheckerContext, reporter: DiagnosticReporter) {
        if (declaration.classKind != ClassKind.OBJECT)
            return

        val companionSymbol = declaration.symbol

        companionSymbol.primaryConstructorSymbol(context.session)?.resolvedDelegatedConstructorCall
            ?.accept(objectRefVisitor, Data(companionSymbol, context, reporter))
    }

    private val objectRefVisitor: FirVisitor<Unit, Data> = object : FirVisitor<Unit, Data>() {
        override fun visitElement(element: FirElement, data: Data) {
            element.acceptChildren(this, data)
        }

        override fun visitThisReceiverExpression(thisReceiverExpression: FirThisReceiverExpression, data: Data) {
            if (thisReceiverExpression.calleeReference.boundSymbol == data.objectSymbol)
                data.reporter.reportOn(thisReceiverExpression.source, SELF_CALL_IN_OBJECT_CONSTRUCTOR, data.context)

            super.visitThisReceiverExpression(thisReceiverExpression, data)
        }

        override fun visitResolvedQualifier(resolvedQualifier: FirResolvedQualifier, data: Data) {
            if (resolvedQualifier.symbol == data.objectSymbol)
                data.reporter.reportOn(resolvedQualifier.source, SELF_CALL_IN_OBJECT_CONSTRUCTOR, data.context)

            super.visitResolvedQualifier(resolvedQualifier, data)
        }
    }

    private class Data(val objectSymbol: FirRegularClassSymbol, val context: CheckerContext, val reporter: DiagnosticReporter)
}