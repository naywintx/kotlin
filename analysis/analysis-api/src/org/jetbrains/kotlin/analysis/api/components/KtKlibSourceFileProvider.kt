/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.components

import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.descriptors.SourceFile
import org.jetbrains.kotlin.psi.KtDeclaration

public abstract class KtKlibSourceFileProvider : KtAnalysisSessionComponent() {
    public abstract fun getKlibSourceFile(declaration: KtDeclarationSymbol): SourceFile?
}

public interface KtKlibSourceFileProviderMixIn : KtAnalysisSessionMixIn {
    /**
     * If [KtDeclaration] is a deserialized, klib based symbol, then information about the original
     * [SourceFile] might be retained.
     */
    public fun KtDeclarationSymbol.getKlibSourceFile(): SourceFile? =
        withValidityAssertion { analysisSession.klibSourceFileProvider.getKlibSourceFile(this) }
}