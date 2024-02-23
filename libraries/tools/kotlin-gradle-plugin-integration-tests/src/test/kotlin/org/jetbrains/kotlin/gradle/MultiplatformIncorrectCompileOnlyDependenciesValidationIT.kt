/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.gradle

import org.gradle.util.GradleVersion
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.gradle.testbase.*
import kotlin.test.assertContentEquals

open class MultiplatformIncorrectCompileOnlyDependenciesValidationIT : KGPBaseTest() {

    private fun setupProject(
        gradleVersion: GradleVersion,
        commonMainCompileOnlyDependencies: Boolean = false,
        commonMainApiDependencies: Boolean = false,

        commonTestCompileOnlyDependencies: Boolean = false,

        targetMainApiDependencies: Boolean = false,

        kotlinNativeIgnoreIncorrectDependencies: Boolean = false,
        kotlinKmpIgnoreIncorrectCompileOnlyDependencies: Boolean = false,
        exec: TestProject.() -> Unit,
    ) {
        project("kmp-compileonly-dependency", gradleVersion) {
            if (commonMainCompileOnlyDependencies) {
                buildGradleKts.modify {
                    it.replace("//commonMain-compileOnly:", "")
                }
            }

            if (commonTestCompileOnlyDependencies) {
                buildGradleKts.modify {
                    it.replace("//commonTest-compileOnly:", "")
                }
            }

            if (commonMainApiDependencies) {
                buildGradleKts.modify {
                    it.replace("//commonMain-api:", "")
                }
            }

            if (targetMainApiDependencies) {
                buildGradleKts.modify {
                    it.replace("//targetMain-api:", "")
                }
            }

            if (kotlinNativeIgnoreIncorrectDependencies) {
                gradleProperties.modify {
                    @Language("properties")
                    val prop = """
                        |
                        |kotlin.native.ignoreIncorrectDependencies=true
                        |
                    """.trimMargin()
                    it + prop
                }
            }

            if (kotlinKmpIgnoreIncorrectCompileOnlyDependencies) {
                gradleProperties.modify {
                    @Language("properties")
                    val prop = """
                        |
                        |kotlin.suppressGradlePluginWarnings=IncorrectCompileOnlyDependencyWarning
                        |
                    """.trimMargin()
                    it + prop
                }
            }

            exec()
        }
    }

    @GradleTest
    @MppGradlePluginTests
    fun `when compileOnly dependency is not defined anywhere, expect no warning`(
        gradleVersion: GradleVersion,
    ) {
        setupProject(gradleVersion) {
            build("help") {
                assertTasksExecuted(":help")
                assertOutputDoesNotContain(compileOnlyDependencyWarningRegex)
            }
        }
    }

    /**
     * The `compileOnly()` warning is only relevant for 'published' compilations.
     *
     * Verify `compileOnly()` dependencies in test sources do not trigger the warning.
     */
    @GradleTest
    @MppGradlePluginTests
    fun `when compileOnly dependency is defined in commonTest, expect no warning`(
        gradleVersion: GradleVersion,
    ) {
        setupProject(
            gradleVersion = gradleVersion,
            commonTestCompileOnlyDependencies = true,
        ) {
            build("help") {
                assertTasksExecuted(":help")
                assertOutputDoesNotContain(compileOnlyDependencyWarningRegex)
            }
        }
    }

    @GradleTest
    @MppGradlePluginTests
    fun `when dependency is defined as compileOnly but not api, expect warnings`(
        gradleVersion: GradleVersion,
    ) {
        setupProject(
            gradleVersion = gradleVersion,
            commonMainCompileOnlyDependencies = true,
        ) {
            build("help") {
                val warnings = compileOnlyDependencyWarningRegex.findAll(output)
                    .map {
                        val (platformName) = it.destructured
                        platformName
                    }
                    .distinct()
                    .toList()
                    .sorted()

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

    @GradleTest
    @MppGradlePluginTests
    fun `when commonMain dependency is defined as compileOnly and api, expect no warning`(
        gradleVersion: GradleVersion,
    ) {
        setupProject(
            gradleVersion = gradleVersion,
            commonMainCompileOnlyDependencies = true,
            commonMainApiDependencies = true,
        ) {
            build("help") {
                assertTasksExecuted(":help")
                assertOutputDoesNotContain(compileOnlyDependencyWarningRegex)
            }
        }
    }

    @GradleTest
    @MppGradlePluginTests
    fun `when dependency is defined as compileOnly in commonMain, and api in target main sources, expect no warning`(
        gradleVersion: GradleVersion,
    ) {
        setupProject(
            gradleVersion = gradleVersion,
            commonMainCompileOnlyDependencies = true,
            targetMainApiDependencies = true,
        ) {
            build("help") {
                assertTasksExecuted(":help")
                assertOutputDoesNotContain(compileOnlyDependencyWarningRegex)
            }
        }
    }

    @GradleTest
    @MppGradlePluginTests
    fun `when dependency is defined as compileOnly but not api, and kotlin-mpp warning is disabled, expect no warning`(
        gradleVersion: GradleVersion,
    ) {
        setupProject(
            gradleVersion = gradleVersion,
            commonMainCompileOnlyDependencies = true,
            kotlinKmpIgnoreIncorrectCompileOnlyDependencies = true,
        ) {
            build("help") {
                assertTasksExecuted(":help")
                assertOutputDoesNotContain(compileOnlyDependencyWarningRegex)
            }
        }
    }

    @GradleTest
    @MppGradlePluginTests
    fun `when dependency is defined as compileOnly but not api, and kotlin-native warning is disabled, expect no warning for native compilations`(
        gradleVersion: GradleVersion,
    ) {
        setupProject(
            gradleVersion = gradleVersion,
            commonMainCompileOnlyDependencies = true,
            kotlinNativeIgnoreIncorrectDependencies = true,
        ) {
            build("help") {
                assertOutputDoesNotContain("A compileOnly dependency is used in the Kotlin/Native target")
            }
        }
    }

    companion object {
        private val compileOnlyDependencyWarningRegex = Regex(
            "A compileOnly dependency is used in the (?<platformName>[^ ]*) target '[^']*':"
        )
    }
}
