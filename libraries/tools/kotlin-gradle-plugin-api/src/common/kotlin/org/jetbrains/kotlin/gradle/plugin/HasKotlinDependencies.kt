/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin

import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.logging.Logger

/**
 * Represents a Kotlin DSL entity having configurable Kotlin dependencies.
 */
interface HasKotlinDependencies {

    /**
     * Configures dependencies for this entity.
     */
    fun dependencies(configure: KotlinDependencyHandler.() -> Unit)

    /**
     * Configures dependencies for this entity.
     */
    fun dependencies(configure: Action<KotlinDependencyHandler>)

    /**
     * The name of the Gradle [Configuration]
     * containing [API](https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_separation) dependencies.
     *
     * The Gradle API configuration should be used to declare dependencies which are exported by the project API.
     *
     * This Gradle configuration is not meant to be resolved.
     */
    val apiConfigurationName: String

    /**
     * The name of the Gradle [Configuration]
     * containing [implementation](https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_separation)
     * dependencies.
     *
     * The Gradle implementation configuration should be used to declare dependencies which are internal to the component (internal APIs).
     *
     * This Gradle configuration is not meant to be resolved.
     */
    val implementationConfigurationName: String

    /**
     * The name of the Gradle [Configuration]
     * containing [compile-only](https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_configurations_graph)
     * dependencies.
     *
     * The Gradle compile-only configuration should be used to declare dependencies which are participating in compilation,
     * but should be added explicitly by consumers in the runtime.
     *
     * This Gradle configuration is not meant to be resolved.
     */
    val compileOnlyConfigurationName: String

    /**
     * The name of the Gradle [Configuration]
     * containing [compile-only](https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_configurations_graph)
     * dependencies.
     *
     * The Gradle runtime-only configuration should be used to declare dependencies which are not participating in the compilation,
     * but added in the runtime.
     *
     * This Gradle configuration is not meant to be resolved.
     */
    val runtimeOnlyConfigurationName: String
}

/**
 * @suppress
 */
@Deprecated(
    message = "Do not use in your build script",
    level = DeprecationLevel.ERROR
)
// Kept in this file to not break API binary compatibility
fun warnNpmGenerateExternals(logger: Logger) {
    logger.warn(
        """
        |
        |==========
        |Please note, Dukat integration in Gradle plugin does not work now.
        |It is in redesigning process.
        |==========
        |
        """.trimMargin()
    )
}
