/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.standalone.base.providers

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.providers.KotlinDeclarationProviderFactory
import org.jetbrains.kotlin.analysis.providers.KotlinDirectInheritorsProvider
import org.jetbrains.kotlin.analysis.providers.impl.KotlinStaticDeclarationProviderFactory
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.contains
import kotlin.collections.filter

class KotlinStandaloneDirectInheritorsProvider(private val project: Project) : KotlinDirectInheritorsProvider {
    private val staticDeclarationProviderFactory by lazy {
        KotlinDeclarationProviderFactory.getInstance(project) as? KotlinStaticDeclarationProviderFactory
            ?: error(
                "`${KotlinStandaloneDirectInheritorsProvider::class.simpleName}` expects the following declaration provider factory to be" +
                        " registered: `${KotlinStaticDeclarationProviderFactory::class.simpleName}`"
            )
    }

    override fun getDirectKotlinInheritors(
        ktClass: KtClass,
        scope: GlobalSearchScope,
        includeLocalInheritors: Boolean,
    ): Iterable<KtClassOrObject> {
        val className = ktClass.nameAsSafeName
        val possibleInheritors = staticDeclarationProviderFactory.getDirectInheritorCandidates(className)

        if (possibleInheritors.isEmpty()) {
            return emptyList()
        }

        analyze(ktClass) {
            val baseClassSymbol = ktClass.getClassOrObjectSymbol() ?: error("Expected class or object.")
            return possibleInheritors.filter { isValidInheritor(it, baseClassSymbol, scope, includeLocalInheritors) }
        }
    }

    private fun KtAnalysisSession.isValidInheritor(
        candidate: KtClassOrObject,
        baseClassSymbol: KtClassOrObjectSymbol,
        scope: GlobalSearchScope,
        includeLocalInheritors: Boolean,
    ): Boolean {
        if (!includeLocalInheritors && candidate.isLocal) {
            return false
        }

        if (!scope.contains(candidate)) {
            return false
        }

        val candidateSymbol = candidate.getClassOrObjectSymbol() ?: return false

        // `isSubClassOf` lazy-resolves symbols to the `SUPER_TYPES` phase. `KotlinDirectInheritorsProvider`'s interface guarantees that
        // `getDirectKotlinInheritors` is only called from lazy resolution to `SEALED_CLASS_INHERITORS` or later, so this is legal.
        return candidateSymbol.isSubClassOf(baseClassSymbol)
    }
}
