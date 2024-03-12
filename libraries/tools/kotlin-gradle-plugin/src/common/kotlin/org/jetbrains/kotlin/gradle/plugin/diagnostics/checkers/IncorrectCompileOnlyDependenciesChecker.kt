/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.gradle.plugin.diagnostics.checkers

import org.gradle.api.artifacts.Dependency
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.diagnostics.*
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinMetadataCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget

internal object IncorrectCompileOnlyDependenciesChecker : KotlinGradleProjectChecker {

    override suspend fun KotlinGradleProjectCheckerContext.runChecks(collector: KotlinToolingDiagnosticsCollector) {
        KotlinPluginLifecycle.Stage.ReadyForExecution.await()

        val multiplatform = multiplatformExtension ?: return

        multiplatform.targets.forEach { target ->
            when (target) {
                is KotlinJsIrTarget -> checkIncorrectDependencies(target)
                is KotlinNativeTarget -> {
                    if (PropertiesProvider(project).ignoreIncorrectNativeDependencies != true) {
                        checkIncorrectDependencies(target)
                    }
                }
            }
        }
    }

    private fun KotlinGradleProjectCheckerContext.checkIncorrectDependencies(target: KotlinTarget) {
        val apiElementsDependencies = project.configurations.getByName(target.apiElementsConfigurationName).allDependencies
        fun Dependency.isInApiElements(): Boolean =
            apiElementsDependencies.any { it.contentEquals(this) }

        val compileOnlyDependencies = target.compilations
            .filter { it.isPublished() }
            .mapNotNull { compilation ->

                val compileOnlyDependencies = project.configurations.getByName(compilation.compileOnlyConfigurationName).allDependencies
                val nonApiCompileOnlyDependencies = compileOnlyDependencies.filter { !it.isInApiElements() }

                if (nonApiCompileOnlyDependencies.isNotEmpty()) {
                    compilation to nonApiCompileOnlyDependencies
                } else {
                    null
                }
            }

        compileOnlyDependencies.forEach { (compilation, dependencies) ->
            project.reportDiagnostic(
                KotlinToolingDiagnostics.IncorrectCompileOnlyDependencyWarning(
                    targetPlatform = target.platformType,
                    targetName = target.name,
                    compilationName = compilation.name,
                    defaultSourceSetName = compilation.defaultSourceSet.name,
                    dependencies = dependencies.map { it.stringCoordinates() },
                )
            )
        }
    }

    /**
     * Estimate whether a [KotlinCompilation] is 'publishable' (i.e. it is a main, non-test compilation).
     */
    private fun KotlinCompilation<*>.isPublished(): Boolean {
        return when (this) {
            is KotlinMetadataCompilation<*> -> true
            else -> name == KotlinCompilation.MAIN_COMPILATION_NAME
        }
    }

    private fun Dependency.stringCoordinates(): String = buildString {
        group?.let { append(it).append(':') }
        append(name)
        version?.let { append(':').append(it) }
    }
}
