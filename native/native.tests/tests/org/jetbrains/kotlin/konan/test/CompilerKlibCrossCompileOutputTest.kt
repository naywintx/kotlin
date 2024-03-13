/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.test

import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.konan.test.blackbox.AbstractNativeSimpleTest
import org.jetbrains.kotlin.konan.test.blackbox.CompilerOutputTestBase
import org.jetbrains.kotlin.konan.test.blackbox.compileLibrary
import org.jetbrains.kotlin.konan.test.blackbox.support.ClassLevelProperty
import org.jetbrains.kotlin.konan.test.blackbox.support.EnforcedProperty
import org.jetbrains.kotlin.konan.test.blackbox.support.compilation.TestCompilationResult.Companion.assertSuccess
import org.jetbrains.kotlin.konan.test.blackbox.support.group.FirPipeline
import org.jetbrains.kotlin.konan.test.blackbox.toOutput
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File

/**
 * This test asserts that K/N compiler "agrees" to try to compile klib for any target
 * on any host (i.e. it doesn't emit an error, doesn't crash, etc.)
 *
 * It doesn't check whether the resulting klib is same across all hosts or even valid
 * (i.e. compiler can emit empty klib and the test will still pass)
 */
abstract class CompilerKlibCrossCompileOutputTest : AbstractNativeSimpleTest() {
    @Test
    fun testKlibCrossCompilation() {
        val rootDir = File("native/native.tests/testData/compilerOutput/klibCrossCompilation")
        val compilationResult = compileLibrary(testRunSettings, rootDir.resolve("hello.kt"))
        val expectedOutput = rootDir.resolve("output.txt")

        KotlinTestUtils.assertEqualsToFile(expectedOutput, compilationResult.toOutput())
    }
}

@TestDataPath("\$PROJECT_ROOT")
@EnforcedProperty(ClassLevelProperty.COMPILER_OUTPUT_INTERCEPTOR, "NONE")
@EnforcedProperty(ClassLevelProperty.TEST_TARGET, "ios_arm64")
class ClassicCompilerOutputTest : CompilerKlibCrossCompileOutputTest()

@FirPipeline
@Tag("frontend-fir")
@TestDataPath("\$PROJECT_ROOT")
@EnforcedProperty(ClassLevelProperty.COMPILER_OUTPUT_INTERCEPTOR, "NONE")
@EnforcedProperty(ClassLevelProperty.TEST_TARGET, "ios_arm64")
class FirCompilerOutputTest : CompilerKlibCrossCompileOutputTest() {

    @Test
    fun testSignatureClashDiagnostics() {
        // TODO: use the Compiler Core test infrastructure for testing these diagnostics (KT-64393)
        val rootDir = File("native/native.tests/testData/compilerOutput/SignatureClashDiagnostics")
        val settings = testRunSettings
        val lib = compileLibrary(settings, rootDir.resolve("lib.kt")).assertSuccess().resultingArtifact
        val compilationResult = compileLibrary(settings, rootDir.resolve("main.kt"), dependencies = listOf(lib))
        val goldenData = rootDir.resolve("output.txt")

        KotlinTestUtils.assertEqualsToFile(goldenData, compilationResult.toOutput())
    }
}
