/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.factory

import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.plugin.HasCompilerOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilationOutput
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.*
import org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.KotlinCompilationModuleManager.CompilationModule

internal class KotlinCompilationImplFactory(
    private val compilerOptionsFactory: CompilerOptionsFactory,

    private val compilationModuleFactory: CompilationModuleFactory =
        DefaultCompilationModuleFactory,

    private val compilationSourceSetsContainerFactory: KotlinCompilationSourceSetsContainerFactory =
        DefaultKotlinCompilationSourceSetsContainerFactory(),

    private val compilationDependencyConfigurationsFactory: KotlinCompilationDependencyConfigurationsFactory =
        DefaultKotlinCompilationDependencyConfigurationsFactory.WithRuntime,

    private val compilationAssociator: KotlinCompilationAssociator =
        DefaultKotlinCompilationAssociator,

    private val compilationFriendPathsResolver: KotlinCompilationFriendPathsResolver =
        DefaultKotlinCompilationFriendPathsResolver(),

    private val compilationSourceSetInclusion: KotlinCompilationSourceSetInclusion =
        DefaultKotlinCompilationSourceSetInclusion(),

    private val compilationOutputFactory: KotlinCompilationOutputFactory =
        DefaultKotlinCompilationOutputFactory,

    private val compilationTaskNamesContainerFactory: KotlinCompilationTaskNamesContainerFactory =
        DefaultKotlinCompilationTaskNamesContainerFactory,

    private val processResourcesTaskNameFactory: ProcessResourcesTaskNameFactory =
        DefaultProcessResourcesTaskNameFactory
) {
    fun interface CompilationModuleFactory {
        fun create(target: KotlinTarget, compilationName: String): CompilationModule
    }

    fun interface KotlinCompilationSourceSetsContainerFactory {
        fun create(target: KotlinTarget, compilationName: String): KotlinCompilationSourceSetsContainer
    }

    fun interface KotlinCompilationTaskNamesContainerFactory {
        fun create(target: KotlinTarget, compilationName: String): KotlinCompilationTaskNamesContainer
    }

    fun interface ProcessResourcesTaskNameFactory {
        fun create(target: KotlinTarget, compilationName: String): String?
    }

    fun interface KotlinCompilationDependencyConfigurationsFactory {
        fun create(target: KotlinTarget, compilationName: String): KotlinCompilationDependencyConfigurationsContainer
    }

    fun interface KotlinCompilationOutputFactory {
        fun create(target: KotlinTarget, compilationName: String): KotlinCompilationOutput
    }

    fun interface CompilerOptionsFactory {
        data class Options(val compilerOptions: HasCompilerOptions<*>, val kotlinOptions: KotlinCommonOptions)

        fun create(target: KotlinTarget, compilationName: String): Options
    }

    fun create(target: KotlinTarget, compilationName: String): KotlinCompilationImpl {
        val options = compilerOptionsFactory.create(target, compilationName)
        return KotlinCompilationImpl(
            KotlinCompilationImpl.Params(
                target = target,
                compilationModule = compilationModuleFactory.create(target, compilationName),
                sourceSets = compilationSourceSetsContainerFactory.create(target, compilationName),
                dependencyConfigurations = compilationDependencyConfigurationsFactory.create(target, compilationName),
                compilationTaskNames = compilationTaskNamesContainerFactory.create(target, compilationName),
                processResourcesTaskName = processResourcesTaskNameFactory.create(target, compilationName),
                output = compilationOutputFactory.create(target, compilationName),
                compilerOptions = options.compilerOptions,
                kotlinOptions = options.kotlinOptions,
                compilationAssociator = compilationAssociator,
                compilationFriendPathsResolver = compilationFriendPathsResolver,
                compilationSourceSetInclusion = compilationSourceSetInclusion
            )
        )
    }
}
