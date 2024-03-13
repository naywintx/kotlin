/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.gradle.unitTests

import org.gradle.api.internal.project.ProjectInternal
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.configurationResult
import org.jetbrains.kotlin.gradle.plugin.diagnostics.KotlinToolingDiagnostics.IncorrectCompileOnlyDependencyWarning
import org.jetbrains.kotlin.gradle.plugin.diagnostics.kotlinToolingDiagnosticsCollector
import org.jetbrains.kotlin.gradle.util.*
import kotlin.test.Test


@Suppress("FunctionName") // TODO why are `tests with spaces in names` triggering a warning?
class MultiplatformIncorrectCompileOnlyDependenciesValidationTest {

    private fun setupProject(
        commonMainCompileOnlyDependencies: Boolean = false,
        commonMainApiDependencies: Boolean = false,

        commonTestCompileOnlyDependencies: Boolean = false,

        targetMainApiDependencies: Boolean = false,

        kotlinNativeIgnoreIncorrectDependencies: Boolean? = null,
        kotlinKmpIgnoreIncorrectCompileOnlyDependencies: Boolean? = null,
        configure: KotlinMultiplatformExtension.() -> Unit = {},
    ): ProjectInternal {
        val project = buildProjectWithMPP {
//            kotlin {}
            multiplatformExtension.apply {
                jvm()

                linuxX64()
                mingwX64()
                macosX64()

                js { browser() }

                wasmJs { browser() }
                wasmWasi { nodejs() }

                configure()

                sourceSets.apply {

                    commonMain {
                        dependencies {
                            if (commonMainCompileOnlyDependencies) {
                                compileOnly("org.jetbrains.kotlinx:atomicfu:latest.release")
                            }
                            if (commonMainApiDependencies) {
                                api("org.jetbrains.kotlinx:atomicfu:latest.release")
                            }
                        }
                    }

                    commonTest {
                        dependencies {
                            if (commonTestCompileOnlyDependencies) {
                                compileOnly("org.jetbrains.kotlinx:atomicfu:latest.release")
                                // api() dependency is not defined here, to verify that
                                // compileOnly() + api() is not necessary for test compilations,
                                // because they are not published
                            }
                        }
                    }


                    jvmMain {
                        dependencies {
                            // JVM does not require exposing `compileOnly()` dependencies
                        }
                    }
                    jsMain {
                        dependencies {
                            if (targetMainApiDependencies) {
                                api("org.jetbrains.kotlinx:atomicfu:latest.release")
                            }
                        }
                    }
                    nativeMain {
                        dependencies {
                            if (targetMainApiDependencies) {
                                api("org.jetbrains.kotlinx:atomicfu:latest.release")
                            }
                        }
                    }
                    wasmJsMain {
                        dependencies {
                            if (targetMainApiDependencies) {
                                api("org.jetbrains.kotlinx:atomicfu:latest.release")
                            }
                        }
                    }
                    wasmWasiMain {
                        dependencies {
                            if (targetMainApiDependencies) {
                                api("org.jetbrains.kotlinx:atomicfu:latest.release")
                            }
                        }
                    }
                }
            }

            if (kotlinNativeIgnoreIncorrectDependencies != null) {
                project.propertiesExtension.set(
                    "kotlin.native.ignoreIncorrectDependencies",
                    kotlinNativeIgnoreIncorrectDependencies.toString(),
                )
            }

            if (kotlinKmpIgnoreIncorrectCompileOnlyDependencies != null) {
                project.propertiesExtension.set("kotlin.suppressGradlePluginWarnings", "IncorrectCompileOnlyDependencyWarning")
            }
        }

        project.evaluate()

        return project

//        project.configurationResult.await()
    }

    @Test
    fun `when compileOnly dependency is not defined anywhere, expect no warning`() {
        setupProject().runLifecycleAwareTest {
            configurationResult.await()
            val diagnostics = kotlinToolingDiagnosticsCollector.getDiagnosticsForProject(this)

            diagnostics.assertNoDiagnostics(IncorrectCompileOnlyDependencyWarning)
        }
//        setupProject(gradleVersion) {
//            build("help") {
//                assertTasksExecuted(":help")
//                assertOutputDoesNotContain(org.jetbrains.kotlin.gradle.unitTests.MultiplatformIncorrectCompileOnlyDependenciesValidationIT.compileOnlyDependencyWarningRegex)
//            }
//        }
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
        ).runLifecycleAwareTest {
//            configurationResult.await()
            val diagnostics = kotlinToolingDiagnosticsCollector.getDiagnosticsForProject(this)

            diagnostics.assertNoDiagnostics(IncorrectCompileOnlyDependencyWarning)
        }
    }

    @Test
    fun `when dependency is defined as compileOnly but not api, expect warnings`() {
        val project = setupProject(
            commonMainCompileOnlyDependencies = true,
        )

        project.evaluate()

        project.runLifecycleAwareTest {
            val diagnostics = project.kotlinToolingDiagnosticsCollector
                .getDiagnosticsForProject(project)

            diagnostics.assertContainsDiagnostic(IncorrectCompileOnlyDependencyWarning)

        }

//        diagnostics.assertDiagnostics(ToolingDiagnostic("x", "x", ToolingDiagnostic.Severity.ERROR))

//            .runLifecycleAwareTest {
//
//            configurationResult.await()
//            val diagnostics = kotlinToolingDiagnosticsCollector.getDiagnosticsForProject(this)

//            diagnostics.assertNoDiagnostics("")

//            build("help") {
//                val warnings =
//                    org.jetbrains.kotlin.gradle.unitTests.MultiplatformIncorrectCompileOnlyDependenciesValidationIT.compileOnlyDependencyWarningRegex.findAll(
//                        output
//                    )
//                        .map {
//                            val (platformName) = it.destructured
//                            platformName
//                        }
//                        .distinct()
//                        .toList()
//                        .sorted()
//
//                assertContentEquals(
//                    listOf(
//                        "Kotlin/JS",
//                        "Kotlin/Native",
//                        "Kotlin/Wasm",
//                    ),
//                    warnings,
//                    message = "expect warnings for compileOnly-incompatible platforms"
//                )
//            }
//        }
    }


    @Test
    fun `when commonMain dependency is defined as compileOnly and api, expect no warning`() {
        val project = setupProject(
            commonMainCompileOnlyDependencies = true,
            commonMainApiDependencies = true,
        )

        project.evaluate()

        project.runLifecycleAwareTest {
            val diagnostics = kotlinToolingDiagnosticsCollector.getDiagnosticsForProject(this)
            diagnostics.assertNoDiagnostics(IncorrectCompileOnlyDependencyWarning)
        }

    }

    @Test
    fun `when dependency is defined as compileOnly in commonMain, and api in target main sources, expect no warning`() {
        val project = setupProject(
            commonMainCompileOnlyDependencies = true,
            targetMainApiDependencies = true,
        ) {
//            build("help") {
//                assertTasksExecuted(":help")
//                assertOutputDoesNotContain(org.jetbrains.kotlin.gradle.unitTests.MultiplatformIncorrectCompileOnlyDependenciesValidationIT.compileOnlyDependencyWarningRegex)
//            }
        }
        project.evaluate()

        project.runLifecycleAwareTest {
            val diagnostics = kotlinToolingDiagnosticsCollector.getDiagnosticsForProject(this)
            diagnostics.assertNoDiagnostics(IncorrectCompileOnlyDependencyWarning)
        }

    }

    @Test
    fun `when dependency is defined as compileOnly but not api, and kotlin-mpp warning is disabled, expect no warning`() {
        val project = setupProject(
            commonMainCompileOnlyDependencies = true,
            kotlinKmpIgnoreIncorrectCompileOnlyDependencies = true,
        ) {
//            build("help") {
//                assertTasksExecuted(":help")
//                assertOutputDoesNotContain(org.jetbrains.kotlin.gradle.unitTests.MultiplatformIncorrectCompileOnlyDependenciesValidationIT.compileOnlyDependencyWarningRegex)
//            }
        }
        project.evaluate()

        project.runLifecycleAwareTest {
            val diagnostics = kotlinToolingDiagnosticsCollector.getDiagnosticsForProject(this)
            diagnostics.assertNoDiagnostics(IncorrectCompileOnlyDependencyWarning)
        }
    }

    @Test
    fun `when dependency is defined as compileOnly but not api, and kotlin-native warning is disabled, expect no warning for native compilations`() {
        val project = setupProject(
            commonMainCompileOnlyDependencies = true,
            kotlinNativeIgnoreIncorrectDependencies = true,
        ) {
//            build("help") {
//                assertOutputDoesNotContain("A compileOnly dependency is used in the Kotlin/Native target")
//            }
        }

        project.evaluate()

        project.runLifecycleAwareTest {
            val diagnostics = kotlinToolingDiagnosticsCollector.getDiagnosticsForProject(this)
            diagnostics.assertNoDiagnostics(IncorrectCompileOnlyDependencyWarning)
        }
    }
//
//    companion object {
//        private val compileOnlyDependencyWarningRegex = Regex(
//            "A compileOnly dependency is used in the (?<platformName>[^ ]*) target '[^']*':"
//        )
//    }
}
