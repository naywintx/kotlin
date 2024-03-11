/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.components

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtKlibSourceFileProvider
import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.descriptors.SourceFile

internal class KtFe10KlibSourceFileProvider(
    override val analysisSession: KtAnalysisSession,
) : KtKlibSourceFileProvider() {
    override fun getKlibSourceFile(declaration: KtDeclarationSymbol): SourceFile? {
        throw NotImplementedError("Method is not implemented for FE 1.0")
    }
}
