/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin.Companion.kotlinNodeJsExtension
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmProject.Companion.PACKAGE_JSON
import org.jetbrains.kotlin.gradle.targets.js.npm.resolved.PreparedKotlinCompilationNpmResolution
import org.jetbrains.kotlin.gradle.targets.js.npm.resolver.KotlinRootNpmResolver
import org.jetbrains.kotlin.gradle.utils.getFile
import org.jetbrains.kotlin.gradle.utils.property
import java.io.File

@DisableCachingByDefault
abstract class PublicPackageJsonTask :
    DefaultTask(),
    UsesKotlinNpmResolutionManager {

    private val nodeJs: NodeJsRootExtension
        get() = project.rootProject.kotlinNodeJsExtension

    @get:Internal
    abstract val compilationDisambiguatedName: Property<String>

    private val projectPath = project.path

    private val rootResolver: KotlinRootNpmResolver
        get() = nodeJs.resolver

    @get:Input
    val projectVersion = project.version.toString()

    @get:Input
    abstract val jsIrCompilation: Property<Boolean>

    @get:Input
    abstract val extension: Property<String>

    @get:Input
    abstract val npmProjectName: Property<String>

    @get:Internal
    abstract val npmProjectMain: Property<String>

    @get:Internal
    abstract val packageJsonHandlers: ListProperty<Action<PackageJson>>

    @Suppress("unused")
    @get:Input
    internal val packageJsonInputHandlers: Provider<PackageJson> by lazy {
        packageJsonHandlers.map { packageJsonHandlersList ->
            PackageJson(fakePackageJsonValue, fakePackageJsonValue)
                .apply {
                    packageJsonHandlersList.forEach { it.execute(this) }
                }
        }
    }

    @get:Internal
    internal val components by lazy {
        rootResolver.allConfigurations
    }

//    @get:Input
//    internal abstract val components: Property<ResolvedComponentResult>

//    @get:Input
//    internal abstract val map: MapProperty<ComponentArtifactIdentifier, File>

    private val compilationResolution: PreparedKotlinCompilationNpmResolution
        get() = run {
//            val resolvedConfiguration = components.get() to map.get().map { (key, value) -> key.componentIdentifier to value }.toMap()
            npmResolutionManager.get().resolution.get()[projectPath][compilationDisambiguatedName.get()]
                .getResolutionOrPrepare(
                    npmResolutionManager.get(),
                    logger,
                    components
                )
        }

    @get:Input
    val externalDependencies: Collection<NpmDependencyDeclaration>
        get() = compilationResolution.externalNpmDependencies

    private val defaultPackageJsonFile by lazy {
        project.layout.buildDirectory
            .dir("tmp")
            .map { it.dir(name) }
            .map { it.file(PACKAGE_JSON) }
            .getFile()
    }

    @get:OutputFile
    var packageJsonFile: File by property { defaultPackageJsonFile }

    @TaskAction
    fun resolve() {
        packageJson(
            npmProjectName.get(),
            projectVersion,
            npmProjectMain.get(),
            externalDependencies,
            packageJsonHandlers.get()
        ).let { packageJson ->
            packageJson.main = "${npmProjectName.get()}.${extension.get()}"

            if (jsIrCompilation.get()) {
                packageJson.types = "${npmProjectName.get()}.d.ts"
            }

            packageJson.apply {
                listOf(
                    dependencies,
                    devDependencies,
                    peerDependencies,
                    optionalDependencies
                ).forEach { it.processDependencies() }
            }

            packageJson.saveTo(this@PublicPackageJsonTask.packageJsonFile)
        }
    }

    private fun MutableMap<String, String>.processDependencies() {
        filter { (_, version) ->
            version.isFileVersion()
        }.forEach { (key, _) ->
            remove(key)
        }
    }

    companion object {
        const val NAME = "publicPackageJson"
    }
}