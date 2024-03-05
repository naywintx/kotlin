/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.fir.components

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtKlibSourceFileProvider
import org.jetbrains.kotlin.analysis.api.fir.symbols.KtFirSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.descriptors.SourceFile
import org.jetbrains.kotlin.fir.declarations.utils.klibSourceFile

internal class KtFirKlibSourceFileProvider(
    override val analysisSession: KtAnalysisSession
) : KtKlibSourceFileProvider() {
    override fun getKlibSourceFile(declaration: KtDeclarationSymbol): SourceFile? {
        require(declaration is KtFirSymbol<*>)
        return declaration.firSymbol.klibSourceFile
    }
}