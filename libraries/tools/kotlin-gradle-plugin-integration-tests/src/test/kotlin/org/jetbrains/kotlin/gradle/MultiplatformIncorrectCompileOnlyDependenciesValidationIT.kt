/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.gradle

import org.gradle.api.logging.LogLevel
import org.jetbrains.kotlin.gradle.util.modify
import org.junit.Test
import kotlin.test.assertContentEquals

open class MultiplatformIncorrectCompileOnlyDependenciesValidationIT : BaseGradleIT() {

    private fun setupProject(
        commonMainCompileOnlyDependencies: Boolean = false,
        commonMainApiDependencies: Boolean = false,

        commonTestCompileOnlyDependencies: Boolean = false,

        targetMainApiDependencies: Boolean = false,

        kotlinNativeIgnoreIncorrectDependencies: Boolean = false,
        kotlinKmpIgnoreIncorrectCompileOnlyDependencies: Boolean = false,
        exec: BaseGradleIT.Project.() -> Unit,
    ) {
        with(Project("kmp-compileonly-dependency", minLogLevel = LogLevel.INFO)) {
            setupWorkingDir()

            if (commonMainCompileOnlyDependencies) {
                gradleBuildScript().modify {
                    it.replace("//commonMain-compileOnly:", "")
                }
            }

            if (commonTestCompileOnlyDependencies) {
                gradleBuildScript().modify {
                    it.replace("//commonTest-compileOnly:", "")
                }
            }

            if (commonMainApiDependencies) {
                gradleBuildScript().modify {
                    it.replace("//commonMain-api:", "")
                }
            }

            if (targetMainApiDependencies) {
                gradleBuildScript().modify {
                    it.replace("//targetMain-api:", "")
                }
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
    fun `when compileOnly dependency is not defined anywhere, expect no warning`() {
        setupProject {
            assertOutputDoesNotContainCompileOnlyWarning()
        }
    }

    /**
     * The `compileOnly()` warning is only relevant for 'published' compilations.
     *
     * Verify `compileOnly()` dependencies in test sources do not trigger the warning.
     */
    @Test
    fun `when compileOnly dependency is defined in commonTest, expect no warning`() {
        setupProject(
            commonTestCompileOnlyDependencies = true,
        ) {
            assertOutputDoesNotContainCompileOnlyWarning()
        }
    }

    @Test
    fun `when dependency is defined as compileOnly but not api, expect warnings`() {
        setupProject(
            commonMainCompileOnlyDependencies = true,
        ) {
            build("help") {
                val warnings = extractCompileOnlyWarningPlatformNames()
                assertContentEquals(
                    listOf(
                        "Kotlin/JS",
                        "Kotlin/Native",
                        "Kotlin/Wasm",
                    ),
                    warnings,
                    message = "expect warnings for compileOnly-incompatible platforms"
                )
            }
        }
    }

    @Test
    fun `when commonMain dependency is defined as compileOnly and api, expect no warning`() {
        setupProject(
            commonMainCompileOnlyDependencies = true,
            commonMainApiDependencies = true,
        ) {
            assertOutputDoesNotContainCompileOnlyWarning()
        }
    }

    @Test
    fun `when dependency is defined as compileOnly in commonMain, and api in target main sources, expect no warning`() {
        setupProject(
            commonMainCompileOnlyDependencies = true,
            targetMainApiDependencies = true
        ) {
            assertOutputDoesNotContainCompileOnlyWarning()
        }
    }

    @Test
    fun `when dependency is defined as compileOnly but not api, and kotlin-mpp warning is disabled, expect no warning`() {
        setupProject(
            commonMainCompileOnlyDependencies = true,
            kotlinKmpIgnoreIncorrectCompileOnlyDependencies = true
        ) {
            assertOutputDoesNotContainCompileOnlyWarning()
        }
    }

    @Test
    fun `when dependency is defined as compileOnly but not api, and kotlin-native warning is disabled, expect no warning for native compilations`() {
        setupProject(
            commonMainCompileOnlyDependencies = true,
            kotlinNativeIgnoreIncorrectDependencies = true
        ) {
            build("help") {
                assertNotContains("A compileOnly dependency is used in the Kotlin/Native target")
            }
        }
    }

    companion object {

        private val warningRegex = Regex(
            "A compileOnly dependency is used in the (?<platformName>[^ ]*) target '[^']*':"
        )

        private fun CompiledProject.extractCompileOnlyWarningPlatformNames(): List<String> =
            warningRegex.findAll(output)
                .map {
                    val (platformName) = it.destructured
                    platformName
                }
                .distinct()
                .toList()
                .sorted()

        private fun BaseGradleIT.Project.assertOutputDoesNotContainCompileOnlyWarning(): Unit = with(testCase) {
            build("help") {
                assertSuccessful()
                assertNotContains(warningRegex)
            }
        }
    }
}
