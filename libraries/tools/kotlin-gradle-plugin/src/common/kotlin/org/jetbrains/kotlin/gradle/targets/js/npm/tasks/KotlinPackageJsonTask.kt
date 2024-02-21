/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm.tasks

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrCompilation
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin.Companion.kotlinNodeJsExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin.Companion.kotlinNpmResolutionManager
import org.jetbrains.kotlin.gradle.targets.js.npm.*
import org.jetbrains.kotlin.gradle.targets.js.npm.resolver.KotlinCompilationNpmResolver
import org.jetbrains.kotlin.gradle.targets.js.npm.resolver.KotlinRootNpmResolver
import org.jetbrains.kotlin.gradle.tasks.registerTask
import org.jetbrains.kotlin.gradle.utils.mapToFile
import java.io.File

@DisableCachingByDefault
abstract class KotlinPackageJsonTask :
    DefaultTask(),
    UsesKotlinNpmResolutionManager,
    UsesGradleNodeModulesCache {
    // Only in configuration phase
    // Not part of configuration caching

    private val nodeJs: NodeJsRootExtension
        get() = project.rootProject.kotlinNodeJsExtension

    private val rootResolver: KotlinRootNpmResolver
        get() = nodeJs.resolver

    private val compilationResolver: KotlinCompilationNpmResolver
        get() = rootResolver[projectPath][compilationDisambiguatedName.get()]

//    private fun findDependentTasks(): Collection<Any> =
//        compilationResolver.compilationNpmResolution.internalDependencies.map { dependency ->
//            nodeJs.resolver[dependency.projectPath][dependency.compilationName].npmProject.packageJsonTaskPath
//        } + compilationResolver.compilationNpmResolution.internalCompositeDependencies.map { dependency ->
//            dependency.includedBuild?.task(":$PACKAGE_JSON_UMBRELLA_TASK_NAME") ?: error("includedBuild instance is not available")
//            dependency.includedBuild.task(":${RootPackageJsonTask.NAME}")
//        }

    // -----

    private val projectPath = project.path

    @get:Internal
    abstract val compilationDisambiguatedName: Property<String>

    @get:Internal
    abstract val packageJsonHandlers: ListProperty<Action<PackageJson>>

    @get:Input
    abstract val packageJsonMain: Property<String>

    @get:Input
    internal val packageJsonInputHandlers: Provider<PackageJson> by lazy {
        packageJsonHandlers.map { packageJsonHandlersList ->
            PackageJson(fakePackageJsonValue, fakePackageJsonValue)
                .apply {
                    packageJsonHandlersList.forEach { it.execute(this) }
                }
        }
    }

    @get:Input
    internal val toolsNpmDependencies: List<String> by lazy {
        nodeJs.taskRequirements
            .getCompilationNpmRequirements(projectPath, compilationDisambiguatedName.get())
            .map { it.toString() }
            .sorted()
    }

    @get:Internal
    internal val components by lazy {
        rootResolver.allConfigurations
    }

    @get:InputFiles
    val one: ConfigurableFileCollection = project.objects
        .fileCollection()

//    @get:IgnoreEmptyDirectories
//    @get:NormalizeLineEndings
//    @get:InputFiles
//    @get:PathSensitive(PathSensitivity.RELATIVE)
//    internal abstract val compositeFiles: SetProperty<File>

//    @get:Input
//    internal abstract val components: Property<ResolvedComponentResult>

//    @get:Input
//    internal abstract val map: MapProperty<ComponentArtifactIdentifier, File>

//    @get:Nested
//    internal abstract val npmDeps: SetProperty<NpmDependencyDeclaration>

//    @get:Nested
//    internal abstract val fileDeps: SetProperty<FileCollectionExternalGradleDependency>

    // nested inputs are processed in configuration phase
    // so npmResolutionManager must not be used
//    @get:Nested
//    internal val producerInputs: PackageJsonProducerInputs by lazy {
//        compilationResolver.compilationNpmResolution.inputs
//    }

    @get:OutputFile
    abstract val packageJson: Property<File>

    @TaskAction
    fun resolve() {
//        val resolvedConfiguration = components.get() to map.get().map { (key, value) -> key.componentIdentifier to value }.toMap()

        val resolution = npmResolutionManager.get().resolution.get()[projectPath][compilationDisambiguatedName.get()]
        val preparedResolution = resolution
            .prepareWithDependencies(
                npmResolutionManager = npmResolutionManager.get(),
                logger = logger,
                resolvedConfiguration = components
            )

        resolution.createPackageJson(preparedResolution, packageJsonMain, packageJsonHandlers)
    }

    companion object {
        fun create(
            compilation: KotlinJsIrCompilation,
            conf: Configuration,
            anotherConfName: String
//            resolvedConfiguration: Pair<Provider<ResolvedComponentResult>, Provider<Map<ComponentArtifactIdentifier, File>>>
        ): TaskProvider<KotlinPackageJsonTask> {
            val target = compilation.target
            val project = target.project
            val npmProject = compilation.npmProject
            val nodeJsTaskProviders = project.rootProject.kotlinNodeJsExtension

            val npmCachesSetupTask = nodeJsTaskProviders.npmCachesSetupTaskProvider
            val packageJsonTaskName = npmProject.packageJsonTaskName
            val packageJsonUmbrella = nodeJsTaskProviders.packageJsonUmbrellaTaskProvider

//            fun createAggregatedConfiguration(): Configuration {
//                val all = project.configurations.create(compilation.disambiguateName("npm"))
//
//                all.usesPlatformOf(target)
//                all.attributes.attribute(Usage.USAGE_ATTRIBUTE, KotlinUsages.consumerRuntimeUsage(target))
//                all.attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.categoryByName(Category.LIBRARY))
//                all.isVisible = false
//                all.isCanBeConsumed = false
//                all.isCanBeResolved = true
//                all.description = "NPM configuration for $compilation."
//
//                KotlinDependencyScope.values().forEach { scope ->
//                    val compilationConfiguration = project.compilationDependencyConfigurationByScope(
//                        compilation,
//                        scope
//                    )
//                    all.extendsFrom(compilationConfiguration)
//                    compilation.allKotlinSourceSets.forEach { sourceSet ->
//                        val sourceSetConfiguration = project.configurations.sourceSetDependencyConfigurationByScope(sourceSet, scope)
//                        all.extendsFrom(sourceSetConfiguration)
//                    }
//                }
//
//                // We don't have `kotlin-js-test-runner` in NPM yet
//                all.dependencies.add(nodeJsTaskProviders.versions.kotlinJsTestRunner.createDependency(project))
//
//                return all
//            }

            val npmResolutionManager = project.kotlinNpmResolutionManager
            val gradleNodeModules = GradleNodeModulesCache.registerIfAbsent(project, null, null)
            val packageJsonTask = project.registerTask<KotlinPackageJsonTask>(packageJsonTaskName) { task ->
                task.compilationDisambiguatedName.set(anotherConfName)
                task.packageJsonHandlers.set(compilation.packageJsonHandlers)
                task.description = "Create package.json file for $compilation"
                task.group = NodeJsRootPlugin.TASKS_GROUP_NAME

                task.one.from(conf)

//                val all = createAggregatedConfiguration()
//                val createAggregatedConfiguration = all.incoming.resolutionResult.rootComponent to all.incoming.artifacts.resolvedArtifacts.map {
//                    it.map { it.id to it.file }.toMap()
//                }
//
//                val externalNpmDependencies = mutableSetOf<NpmDependencyDeclaration>()
//                val fileCollectionDependencies = mutableSetOf<FileCollectionExternalGradleDependency>()
//
//                all.allDependencies.forEach { dependency ->
//                    when (dependency) {
//                        is NpmDependency -> externalNpmDependencies.add(dependency.toDeclaration())
//                        is FileCollectionDependency -> fileCollectionDependencies.add(
//                            FileCollectionExternalGradleDependency(
//                                dependency.files.files,
//                                dependency.version
//                            )
//                        )
//                    }
//                }
//                task.components.set(resolvedConfiguration.first)
//                task.map.set(resolvedConfiguration.second)
//                task.resolvedConfiguration = createAggregatedConfiguration

//                task.npmDeps.set(externalNpmDependencies)
//                task.fileDeps.set(fileCollectionDependencies)

//                task.compositeFiles.set(
//                    all
//                        .incoming
//                        .artifactView { artifactView ->
//                            artifactView.componentFilter { componentIdentifier ->
//                                componentIdentifier is ProjectComponentIdentifier
//                            }
//                        }
//                        .artifacts
//                        .filter {
//                            it.id `is` CompositeProjectComponentArtifactMetadata
//                        }
//                        .map { it.file }
//                        .toSet()
//                )

                task.npmResolutionManager.value(npmResolutionManager)
                    .disallowChanges()

                task.gradleNodeModules.value(gradleNodeModules)
                    .disallowChanges()

                task.packageJsonMain.set(compilation.npmProject.main)

                task.packageJson.set(compilation.npmProject.packageJsonFile.mapToFile())

                task.onlyIf {
                    it as KotlinPackageJsonTask
                    it.npmResolutionManager.get().isConfiguringState()
                }

//                task.dependsOn(target.project.provider { task.findDependentTasks() })
                task.dependsOn(npmCachesSetupTask)
            }

            packageJsonUmbrella.configure { task ->
                task.inputs.file(packageJsonTask.map { it.packageJson })
            }

            nodeJsTaskProviders.rootPackageJsonTaskProvider.configure { it.mustRunAfter(packageJsonTask) }

            return packageJsonTask
        }
    }
}