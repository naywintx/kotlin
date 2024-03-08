/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm.resolver

import org.gradle.api.Action
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.logging.Logger
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.targets.js.nodejs.TasksRequirements
import org.jetbrains.kotlin.gradle.targets.js.npm.*
import org.jetbrains.kotlin.gradle.targets.js.npm.resolved.KotlinRootNpmResolution
import org.jetbrains.kotlin.gradle.targets.js.npm.resolved.PreparedKotlinCompilationNpmResolution
import org.jetbrains.kotlin.gradle.utils.buildOrNull
import org.jetbrains.kotlin.gradle.utils.buildPathCompat
import org.jetbrains.kotlin.gradle.utils.getFile
import java.io.File
import java.io.Serializable

class KotlinCompilationNpmResolution(
    val buildPath: String,
    val npmDependencies: Set<NpmDependencyDeclaration>,
    val fileDependencies: Set<FileCollectionExternalGradleDependency>,
    val projectPath: String,
    val compilationDisambiguatedName: String,
    val publicPackageJsonConf: String,
    val npmProjectName: String,
    val npmProjectVersion: String,
    val tasksRequirements: TasksRequirements,
) : Serializable {

    private var closed = false
    internal var resolution: PreparedKotlinCompilationNpmResolution? = null

    @Synchronized
    fun prepareWithDependencies(
        npmResolutionManager: KotlinNpmResolutionManager,
        logger: Logger,
        resolvedConfigurations: Map<String, Map<String, Pair<Provider<ResolvedComponentResult>, Provider<Map<ComponentArtifactIdentifier, File>>>>>,
    ): PreparedKotlinCompilationNpmResolution {
        check(resolution == null) { "$this already resolved" }

        return createPreparedResolution(
            npmResolutionManager,
            logger,
            resolvedConfigurations
        ).also {
            resolution = it
        }
    }

    @Synchronized
    fun getResolutionOrPrepare(
        npmResolutionManager: KotlinNpmResolutionManager,
        logger: Logger,
        resolvedConfigurations: Map<String, Map<String, Pair<Provider<ResolvedComponentResult>, Provider<Map<ComponentArtifactIdentifier, File>>>>>,
    ): PreparedKotlinCompilationNpmResolution {

        return resolution ?: prepareWithDependencies(
            npmResolutionManager,
            logger,
            resolvedConfigurations,
        )
    }

    @Synchronized
    fun close(
        npmResolutionManager: KotlinNpmResolutionManager,
        logger: Logger,
        resolvedConfigurations: Map<String, Map<String, Pair<Provider<ResolvedComponentResult>, Provider<Map<ComponentArtifactIdentifier, File>>>>>,
    ): PreparedKotlinCompilationNpmResolution {
        check(!closed) { "$this already closed" }
        closed = true
        return getResolutionOrPrepare(npmResolutionManager, logger, resolvedConfigurations)
    }

    private fun createPreparedResolution(
        npmResolutionManager: KotlinNpmResolutionManager,
        logger: Logger,
        resolvedConfigurations: Map<String, Map<String, Pair<Provider<ResolvedComponentResult>, Provider<Map<ComponentArtifactIdentifier, File>>>>>,
    ): PreparedKotlinCompilationNpmResolution {
        val rootResolver = npmResolutionManager.parameters.resolution.get()

        val visitor = ConfigurationVisitor(rootResolver)
        val configuration = resolvedConfigurations.getValue(projectPath).getValue(publicPackageJsonConf)
        visitor.visit(configuration.first.get() to configuration.second.get().map { (key, value) -> key.componentIdentifier to value }
            .toMap())

        val internalNpmDependencies = visitor.internalDependencies
            .map {
                val compilationNpmResolution: KotlinCompilationNpmResolution = rootResolver[it.projectPath][it.compilationName]
                compilationNpmResolution.getResolutionOrPrepare(
                    npmResolutionManager,
                    logger,
                    resolvedConfigurations
                )
            }
            .flatMap { it.externalNpmDependencies }
        val importedExternalGradleDependencies = visitor.externalGradleDependencies.mapNotNull {
            npmResolutionManager.parameters.gradleNodeModulesProvider.get().get(it.module, it.version ?: "0.0.1-SNAPSHOT", it.artifact)
        } + fileDependencies.flatMap { dependency ->
            dependency.files
                // Gradle can hash with FileHasher only files and only existed files
                .filter { it.isFile }
                .map { file ->
                    npmResolutionManager.parameters.gradleNodeModulesProvider.get().get(
                        file.name,
                        dependency.dependencyVersion ?: "0.0.1",
                        file
                    )
                }
        }.filterNotNull()
        val transitiveNpmDependencies = (importedExternalGradleDependencies.flatMap {
            it.dependencies
        } + internalNpmDependencies).filter { it.scope != NpmDependency.Scope.DEV }

        val toolsNpmDependencies = tasksRequirements
            .getCompilationNpmRequirements(projectPath, compilationDisambiguatedName)

        val otherNpmDependencies = toolsNpmDependencies + transitiveNpmDependencies
        val allNpmDependencies = disambiguateDependencies(npmDependencies, otherNpmDependencies, logger)

        return PreparedKotlinCompilationNpmResolution(
            npmResolutionManager.packagesDir.map { it.dir(npmProjectName) },
            importedExternalGradleDependencies,
            allNpmDependencies,
        )
    }

    fun createPackageJson(
        resolution: PreparedKotlinCompilationNpmResolution,
        npmProjectMain: Provider<String>,
        packageJsonHandlers: ListProperty<Action<PackageJson>>,
    ) {
        val packageJson = packageJson(
            npmProjectName,
            npmProjectVersion,
            npmProjectMain.get(),
            resolution.externalNpmDependencies,
            packageJsonHandlers.get()
        )

        packageJsonHandlers.get().forEach {
            it.execute(packageJson)
        }

        packageJson.saveTo(resolution.npmProjectDir.getFile().resolve(NpmProject.PACKAGE_JSON))
    }

    private fun disambiguateDependencies(
        direct: Collection<NpmDependencyDeclaration>,
        others: Collection<NpmDependencyDeclaration>,
        logger: Logger,
    ): Collection<NpmDependencyDeclaration> {
        val unique = others.groupBy(NpmDependencyDeclaration::name)
            .filterKeys { k -> direct.none { it.name == k } }
            .mapNotNull { (_, dependencies) ->
                dependencies.maxByOrNull { dep ->
                    SemVer.from(dep.version, true)
                }?.also { selected ->
                    if (dependencies.size > 1) {
                        logger.warn(
                            """
                                Transitive npm dependency version clash for compilation "${compilationDisambiguatedName}"
                                    Candidates:
                                ${dependencies.joinToString("\n") { "\t\t" + it.name + "@" + it.version }}
                                    Selected:
                                        ${selected.name}@${selected.version}
                                """.trimIndent()
                        )
                    }
                }
            }
        return direct + unique
    }

    inner class ConfigurationVisitor(val rootResolution: KotlinRootNpmResolution) {
        val internalDependencies = mutableSetOf<InternalDependency>()
        val externalGradleDependencies = mutableSetOf<ExternalGradleDependency>()

        private val visitedDependencies = mutableSetOf<ResolvedVariantResult>()

        fun visit(configuration: Pair<ResolvedComponentResult, Map<ComponentIdentifier, File>>) {
            configuration.first.dependencies.forEach { result ->
                if (result is ResolvedDependencyResult) {
                    val variant = result.resolvedVariant.externalVariant.orElse(result.resolvedVariant)
                    configuration.second[variant.owner]?.let { artifactFile ->
                        visitDependency(variant, artifactFile)
                    }
                } else {
                    println("WTF ${result}")
                }
            }
        }

        private fun visitDependency(dependency: ResolvedVariantResult, second: File) {
            if (dependency in visitedDependencies) return
            visitedDependencies.add(dependency)
            visitArtifact(dependency, second)
        }

        private fun visitArtifact(
            dependency: ResolvedVariantResult,
            artifact: File,
        ) {

            val owner = dependency.owner
            if (buildPath == owner.buildOrNull?.buildPathCompat && owner is ProjectComponentIdentifier) {
                visitProjectDependency(dependency)
            }

            if (buildPath != owner.buildOrNull?.buildPathCompat && owner is ProjectComponentIdentifier) {
                dependency.capabilities.forEach { capability ->
                    externalGradleDependencies.add(ExternalGradleDependency(capability.name, capability.version, artifact))
                }
            }

            if (owner is ModuleComponentIdentifier) {
                externalGradleDependencies.add(ExternalGradleDependency(owner.module, owner.version, artifact))
            }
        }

        private fun visitProjectDependency(
            componentIdentifier: ResolvedVariantResult,
        ) {
            val owner = componentIdentifier.owner as ProjectComponentIdentifier
            val dependentCompilation = rootResolution[owner.projectPath][componentIdentifier.displayName]

            internalDependencies.add(
                InternalDependency(
                    dependentCompilation.projectPath,
                    dependentCompilation.publicPackageJsonConf,
                    dependentCompilation.npmProjectName
                )
            )
        }
    }
}