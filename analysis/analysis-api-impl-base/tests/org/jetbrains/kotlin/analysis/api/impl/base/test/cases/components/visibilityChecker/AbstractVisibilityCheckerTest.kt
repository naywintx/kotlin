/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.impl.base.test.cases.components.visibilityChecker

import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.getSymbolOfType
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.analysis.test.framework.base.AbstractAnalysisApiBasedTest
import org.jetbrains.kotlin.analysis.test.framework.services.expressionMarkerProvider
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.test.services.TestModuleStructure
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.assertions

private const val DECLARATION_SITE_MODULE_NAME = "declarationSite"
private const val USE_SITE_MODULE_NAME = "useSite"

/**
 * The visibility checker test looks for an element called "target" if the file doesn't or cannot contain a caret marker, e.g. in files from
 * binary libraries. The target name is case-insensitive, so classes called `Target` will be found as well.
 */
private const val TARGET_ELEMENT_NAME = "target"

abstract class AbstractVisibilityCheckerTest : AbstractAnalysisApiBasedTest() {
    override fun doTestByModuleStructure(moduleStructure: TestModuleStructure, testServices: TestServices) {
        val declarationSiteFile = getMainFileFrom(DECLARATION_SITE_MODULE_NAME, moduleStructure, testServices)
        val useSiteFile = getMainFileFrom(USE_SITE_MODULE_NAME, moduleStructure, testServices)

        val declaration = testServices.expressionMarkerProvider.getElementOfTypeAtCaretOrNull<KtDeclaration>(declarationSiteFile)
            ?: findFirstTargetDeclaration(declarationSiteFile)
            ?: error("Cannot find declaration to check visibility for.")

        val usage = testServices.expressionMarkerProvider.getElementOfTypeAtCaretOrNull<KtExpression>(useSiteFile)
            ?: findFirstTargetDeclaration(useSiteFile)
            ?: error("Cannot find use-site element to check visibility at.")

        val actualText = analyseForTest(usage) {
            val declarationSymbol = declaration.getSymbolOfType<KtSymbolWithVisibility>()
            val useSiteFileSymbol = useSiteFile.getFileSymbol()

            val visible = isVisible(declarationSymbol, useSiteFileSymbol, null, usage)
            """
                Declaration: ${(declarationSymbol as KtDeclarationSymbol).render()}
                At usage site: ${usage.text}
                Is visible: $visible
            """.trimIndent()
        }

        testServices.assertions.assertEqualsToTestDataFileSibling(actualText)
    }

    private fun getMainFileFrom(moduleName: String, moduleStructure: TestModuleStructure, testServices: TestServices): KtFile {
        val module = moduleStructure.modules.find { it.name == moduleName } ?: error("Cannot find module `$moduleName`.")
        return findMainFile(module, testServices) ?: error("Cannot find main file in `$module`.")
    }

    private fun findFirstTargetDeclaration(ktFile: KtFile): KtNamedDeclaration? =
        ktFile.findDescendantOfType<KtNamedDeclaration> { it.name?.lowercase() == TARGET_ELEMENT_NAME }
}
