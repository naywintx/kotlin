/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.impl.base.test.cases.components.inheritorsProvider

import org.jetbrains.kotlin.analysis.api.impl.base.test.SymbolByFqName
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.test.framework.base.AbstractAnalysisApiBasedTest
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.assertions

abstract class AbstractSealedInheritorsTest : AbstractAnalysisApiBasedTest() {
    override fun doTestByMainFile(mainFile: KtFile, mainModule: TestModule, testServices: TestServices) {
        analyseForTest(mainFile) {
            val actualText = with(SymbolByFqName.getSymbolDataFromFile(testDataPath)) {
                val classSymbol = toSymbols(mainFile).singleOrNull() as? KtNamedClassOrObjectSymbol
                    ?: error("Expected a single named class to be specified.")

                classSymbol.getSealedClassInheritors().joinToString("\n") { it.classIdIfNonLocal!!.toString() }
            }

            testServices.assertions.assertEqualsToTestDataFileSibling(actualText)
        }
    }
}
