/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.gradle

import org.gradle.api.logging.LogLevel
import org.jetbrains.kotlin.gradle.util.modify
import org.jetbrains.kotlin.konan.target.HostManager
import org.junit.Test

open class MultiplatformIncorrectCompileOnlyDependenciesValidationIT : BaseGradleIT() {

    private fun setupProject(
        jsTarget: Boolean = false,
        wasmJs: Boolean = false,
        wasmWasi: Boolean = false,
        compileOnlyDependencies: Boolean = false,
        apiDependencies: Boolean = false,
        kotlinNativeIgnoreIncorrectDependencies: Boolean = false,
        kotlinKmpIgnoreIncorrectCompileOnlyDependencies: Boolean = false,
        exec: BaseGradleIT.Project.() -> Unit,
    ) {
        with(Project("kmp-compileonly-dependency", minLogLevel = LogLevel.INFO)) {
            setupWorkingDir()

            fun modifyAllBuildGradleKts(transform: (content: String) -> String) {
                projectDir.walk().filter { it.name == "build.gradle.kts" }.forEach { buildGradleKts ->
                    buildGradleKts.modify(transform)
                }
            }

            if (jsTarget) {
                modifyAllBuildGradleKts { it.replace("//jsTarget:", "") }
            }

            if (wasmWasi) {
                modifyAllBuildGradleKts { it.replace("//wasmWasi:", "") }
            }

            if (wasmJs) {
                modifyAllBuildGradleKts { it.replace("//wasmJs:", "") }
            }

            if (compileOnlyDependencies) {
                modifyAllBuildGradleKts { it.replace("//compileOnly:", "") }
            }

            if (apiDependencies) {
                modifyAllBuildGradleKts { it.replace("//api:", "") }
            }

            if (kotlinNativeIgnoreIncorrectDependencies) {
                gradleProperties += """
                    |
                    |kotlin.native.ignoreIncorrectDependencies=true
                    |
                """.trimMargin()
            }

            if (kotlinKmpIgnoreIncorrectCompileOnlyDependencies) {
                gradleProperties += """
                    |
                    |kotlin.mpp.ignoreIncorrectCompileOnlyDependencies=true
                    |
                """.trimMargin()
            }

            exec()
        }
    }

    @Test
    fun `when dependency is not defined, expect no warning`() {
        setupProject(
            jsTarget = true
        ) {
            assertOutputDoesNotContainCompileOnlyWarning()
        }
    }

    @Test
    fun `when dependency is defined as compileOnly but not api, expect warning`() {
        setupProject(
            jsTarget = true,
            wasmJs = true,
            wasmWasi = true,
            compileOnlyDependencies = true,
            apiDependencies = false,
        ) {
            assertOutputContainsCompileOnlyWarning(target = "Kotlin/JS", "js")
            assertOutputContainsCompileOnlyWarning(target = "Kotlin/Native", detectNativeEnabledCompilation())
            assertOutputContainsCompileOnlyWarning(target = "Kotlin/Wasm", "wasmWasi")
            assertOutputContainsCompileOnlyWarning(target = "Kotlin/Wasm", "wasmJs")
        }
    }

    @Test
    fun `in Native project, when dependency is defined as compileOnly and api, expect no warning`() {
        setupProject(
            compileOnlyDependencies = true,
            apiDependencies = true,
        ) {
            assertOutputDoesNotContainCompileOnlyWarning()
        }
    }

    @Test
    fun `in Native and JS project, when dependency is defined as compileOnly but not api, and kotlin-mpp warning is disabled, expect no warning`() {
        setupProject(
            jsTarget = true,
            compileOnlyDependencies = true,
            apiDependencies = false,
            kotlinKmpIgnoreIncorrectCompileOnlyDependencies = true
        ) {
            assertOutputDoesNotContainCompileOnlyWarning()
        }
    }

    @Test
    fun `in Native project, when dependency is defined as compileOnly but not api, and kotlin-native warning is disabled, expect no warning`() {
        setupProject(
            jsTarget = false,
            compileOnlyDependencies = true,
            kotlinNativeIgnoreIncorrectDependencies = true
        ) {
            assertOutputDoesNotContainCompileOnlyWarning()
        }
    }

    companion object {

        private fun BaseGradleIT.Project.assertOutputContainsCompileOnlyWarning(
            target: String,
            compilation: String,
        ): Unit = with(testCase) {
            build("help") {
                assertSuccessful()
                assertContains(Regex("A compileOnly dependency is used in the $target target '$compilation':"))
            }
        }

        private fun BaseGradleIT.Project.assertOutputDoesNotContainCompileOnlyWarning(): Unit = with(testCase) {
            build("help") {
                assertSuccessful()
                assertNotContains(Regex("A compileOnly dependency is used in the [^ ]* target '[^']*':"))
            }
        }

        private fun detectNativeEnabledCompilation(): String = when {
            HostManager.hostIsLinux -> "linuxX64"
            HostManager.hostIsMingw -> "mingwX64"
            HostManager.hostIsMac -> "macosX64"
            else -> throw AssertionError("Host ${HostManager.host} is not supported for this test")
        }
    }
}
