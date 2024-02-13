/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm.resolver

import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier
import org.gradle.api.logging.Logger
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.isMain
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrCompilation
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.js.nodejs.TasksRequirements
import org.jetbrains.kotlin.gradle.targets.js.npm.*
import org.jetbrains.kotlin.gradle.targets.js.npm.resolved.KotlinRootNpmResolution
import org.jetbrains.kotlin.gradle.targets.js.npm.resolved.PreparedKotlinCompilationNpmResolution
import org.jetbrains.kotlin.gradle.utils.CompositeProjectComponentArtifactMetadata
import org.jetbrains.kotlin.gradle.utils.getFile
import org.jetbrains.kotlin.gradle.utils.`is`
import org.jetbrains.kotlin.gradle.utils.topRealPath
import java.io.File
import java.io.Serializable

class KotlinCompilationNpmResolution(
//    var internalDependencies: Collection<InternalDependency>,
//    var internalCompositeDependencies: Collection<CompositeDependency>,
//    var externalGradleDependencies: Collection<FileExternalGradleDependency>,
//    var externalNpmDependencies: Collection<NpmDependencyDeclaration>,
//    var fileCollectionDependencies: Collection<FileCollectionExternalGradleDependency>,
    val projectPath: String,
    val compilationDisambiguatedName: String,
    val npmProjectName: String,
    val npmProjectVersion: String,
    val tasksRequirements: TasksRequirements,
) : Serializable {

//    val inputs: PackageJsonProducerInputs
//        get() = PackageJsonProducerInputs(
//            internalDependencies.map { it.projectName },
//            externalGradleDependencies.map { it.file },
//            externalNpmDependencies.map { it.uniqueRepresentation() },
//            fileCollectionDependencies.flatMap { it.files }
//        )

    private var closed = false
    internal var resolution: PreparedKotlinCompilationNpmResolution? = null

    @Synchronized
    fun prepareWithDependencies(
        npmResolutionManager: KotlinNpmResolutionManager,
        logger: Logger,
        resolvedConfiguration: Pair<ResolvedComponentResult, Map<ComponentIdentifier, File>>,
        npmDeps: Set<NpmDependencyDeclaration>,
        fileDeps: Set<FileCollectionExternalGradleDependency>
    ): PreparedKotlinCompilationNpmResolution {
        check(resolution == null) { "$this already resolved" }

        return createPreparedResolution(
            npmResolutionManager,
            logger,
            resolvedConfiguration,
            npmDeps,
            fileDeps,
        ).also {
            resolution = it
        }
    }

    @Synchronized
    fun getResolutionOrPrepare(
        npmResolutionManager: KotlinNpmResolutionManager,
        logger: Logger,
        resolvedConfiguration: Pair<ResolvedComponentResult, Map<ComponentIdentifier, File>>? = null,
    ): PreparedKotlinCompilationNpmResolution {

        return resolution ?: prepareWithDependencies(
            npmResolutionManager,
            logger,
            resolvedConfiguration!!,
            null!!,
            null!!
        )
    }

    @Synchronized
    fun close(
        npmResolutionManager: KotlinNpmResolutionManager,
        logger: Logger,
    ): PreparedKotlinCompilationNpmResolution {
        check(!closed) { "$this already closed" }
        closed = true
        return getResolutionOrPrepare(npmResolutionManager, logger)
    }

    fun createPreparedResolution(
        npmResolutionManager: KotlinNpmResolutionManager,
        logger: Logger,
        resolvedConfiguration: Pair<ResolvedComponentResult, Map<ComponentIdentifier, File>>,
        npmDeps: Set<NpmDependencyDeclaration>,
        fileDeps: Set<FileCollectionExternalGradleDependency>,
    ): PreparedKotlinCompilationNpmResolution {
        val rootResolver = npmResolutionManager.parameters.resolution.get()

        val visitor = ConfigurationVisitor(rootResolver)
        visitor.visit(resolvedConfiguration.first to resolvedConfiguration.second)

        val internalNpmDependencies = visitor.internalDependencies
            .map {
                val compilationNpmResolution: KotlinCompilationNpmResolution = rootResolver[it.projectPath][it.compilationName]
                compilationNpmResolution.getResolutionOrPrepare(
                    npmResolutionManager,
                    logger
                )
            }
            .flatMap { it.externalNpmDependencies }
        val importedExternalGradleDependencies = visitor.externalGradleDependencies.mapNotNull {
            npmResolutionManager.parameters.gradleNodeModulesProvider.get().get(it.component.module, it.component.version, it.artifact)
        } + fileDeps.flatMap { dependency ->
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
        val allNpmDependencies = disambiguateDependencies(npmDeps, otherNpmDependencies, logger)

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
        val internalCompositeDependencies = mutableSetOf<CompositeDependency>()
        val externalGradleDependencies = mutableSetOf<ExternalGradleDependency>()
        val externalNpmDependencies = mutableSetOf<NpmDependencyDeclaration>()
        val fileCollectionDependencies = mutableSetOf<FileCollectionExternalGradleDependency>()

        private val visitedDependencies = mutableSetOf<ComponentIdentifier>()

        fun visit(configuration: Pair<ResolvedComponentResult, Map<ComponentIdentifier, File>>) {
            configuration.first.dependencies.forEach { result ->
                if (result is ResolvedDependencyResult) {
                    val owner = result.resolvedVariant.externalVariant.orElse(result.resolvedVariant).owner
                    visitDependency(owner, configuration.second.getValue(owner))
                } else {
                    println("WTF ${result}")
                }
            }
//            configuration.resolvedConfiguration.firstLevelModuleDependencies.forEach {
//                visitDependency(it)
//            }
//
//            configuration.allDependencies.forEach { dependency ->
//                when (dependency) {
//                    is NpmDependency -> externalNpmDependencies.add(dependency)
//                    is FileCollectionDependency -> fileCollectionDependencies.add(
//                        FileCollectionExternalGradleDependency(
//                            dependency.files.files,
//                            dependency.version
//                        )
//                    )
//                }
//            }

//        TODO: rewrite when we get general way to have inter compilation dependencies
//        if (compilation.name == KotlinCompilation.TEST_COMPILATION_NAME) {
//            val main = compilation.target.compilations.findByName(KotlinCompilation.MAIN_COMPILATION_NAME) as KotlinJsCompilation
//            internalDependencies.add(
//                InternalDependency(
//                    projectResolver.project.path,
//                    main.disambiguatedName,
//                    projectResolver[main].npmProject.name
//                )
//            )
//        }

            val hasPublicNpmDependencies = externalNpmDependencies.isNotEmpty()

//        if (compilation.isMain() && hasPublicNpmDependencies) {
//            project.tasks
//                .withType(Zip::class.java)
//                .named(npmProject.target.artifactsTaskName)
//                .configure { task ->
//                    task.from(publicPackageJsonTaskHolder)
//                }
//        }
        }

        private fun visitDependency(dependency: ComponentIdentifier, second: File) {
            if (dependency in visitedDependencies) return
            visitedDependencies.add(dependency)
            visitArtifact(dependency, second)
//            visitArtifacts(dependency, dependency.)

//            dependency.children.forEach {
//                visitDependency(it)
//            }
        }

//        private fun visitArtifacts(
//            dependency: ResolvedDependency,
//            artifacts: MutableSet<ResolvedArtifact>
//        ) {
//            artifacts.forEach { visitArtifact(dependency, it) }
//        }

        private fun visitArtifact(
            dependency: ComponentIdentifier,
            artifact: File
        ) {
//            val artifactId = artifact.id
//        val componentIdentifier = dependency.id
//
//            if (artifactId `is` CompositeProjectComponentArtifactMetadata) {
//                visitCompositeProjectDependency(dependency, componentIdentifier as ProjectComponentIdentifier)
//                return
//            }

            if (dependency is ProjectComponentIdentifier) {
                visitProjectDependency(dependency)
                return
            }

            if (dependency is ModuleComponentIdentifier) {
                externalGradleDependencies.add(ExternalGradleDependency(dependency, artifact))
            }
        }

//    private fun visitCompositeProjectDependency(
//        dependency: ResolvedDependency,
//        componentIdentifier: ProjectComponentIdentifier
//    ) {
//        check(target is KotlinJsIrTarget) {
//            """
//                Composite builds for Kotlin/JS are supported only for IR compiler.
//                Use kotlin.js.compiler=ir in gradle.properties or
//                js(IR) {
//                ...
//                }
//                """.trimIndent()
//        }
//
//        (componentIdentifier as DefaultProjectComponentIdentifier).let { identifier ->
//            val includedBuild = project.gradle.includedBuild(identifier.identityPath.topRealPath().name!!)
//            internalCompositeDependencies.add(
//                CompositeDependency(dependency.moduleName, dependency.moduleVersion, includedBuild.projectDir, includedBuild)
//            )
//        }
//    }

        private fun visitProjectDependency(
            componentIdentifier: ProjectComponentIdentifier
        ) {
            val dependentProject = rootResolution[componentIdentifier.projectPath]

            val dependentCompilation = dependentProject.npmProjects.single { it.compilationDisambiguatedName.contains("main", ignoreCase = true) }

            internalDependencies.add(
                InternalDependency(
                    dependentCompilation.projectPath,
                    dependentCompilation.compilationDisambiguatedName,
                    dependentCompilation.npmProjectName
                )
            )
        }

//    fun toPackageJsonProducer() = PackageJsonProducer(
//        internalDependencies,
//        internalCompositeDependencies,
//        externalGradleDependencies.map {
//            it.component to it.artifact
//        },
//        externalNpmDependencies.map { it.toDeclaration() },
//        fileCollectionDependencies,
//        projectPath
//    )




        // ================
//        private val internalDependencies = mutableSetOf<InternalDependency>()
//        private val internalCompositeDependencies = mutableSetOf<CompositeDependency>()
//        private val externalGradleDependencies = mutableSetOf<ExternalGradleDependency>()
//        private val externalNpmDependencies = mutableSetOf<NpmDependencyDeclaration>()
//        private val fileCollectionDependencies = mutableSetOf<FileCollectionExternalGradleDependency>()
//
//        private val visitedDependencies = mutableSetOf<ResolvedDependency>()

//        fun visit(configuration: Configuration) {
//            configuration.resolvedConfiguration.firstLevelModuleDependencies.forEach {
//                visitDependency(it)
//            }
//
//            configuration.allDependencies.forEach { dependency ->
//                when (dependency) {
//                    is NpmDependency -> externalNpmDependencies.add(dependency.toDeclaration())
//                    is FileCollectionDependency -> fileCollectionDependencies.add(
//                        FileCollectionExternalGradleDependency(
//                            dependency.files.files,
//                            dependency.version
//                        )
//                    )
//                }
//            }
//
//            //TODO: rewrite when we get general way to have inter compilation dependencies
//            if (compilation.name == KotlinCompilation.TEST_COMPILATION_NAME) {
//                val main = compilation.target.compilations.findByName(KotlinCompilation.MAIN_COMPILATION_NAME) as KotlinJsIrCompilation
//                internalDependencies.add(
//                    InternalDependency(
//                        projectResolver.projectPath,
//                        main.disambiguatedName,
//                        projectResolver[main].npmProject.name
//                    )
//                )
//            }
//
//            val hasPublicNpmDependencies = externalNpmDependencies.isNotEmpty()
//
//            if (compilation.isMain() && hasPublicNpmDependencies) {
//                project.tasks
//                    .withType(Zip::class.java)
//                    .named(npmProject.target.artifactsTaskName)
//                    .configure { task ->
//                        task.from(publicPackageJsonTaskHolder)
//                    }
//            }
//        }

//        private fun visitDependency(dependency: ResolvedDependency) {
//            if (dependency in visitedDependencies) return
//            visitedDependencies.add(dependency)
//            visitArtifacts(dependency, dependency.moduleArtifacts)
//
//            dependency.children.forEach {
//                visitDependency(it)
//            }
//        }
//
//        private fun visitArtifacts(
//            dependency: ResolvedDependency,
//            artifacts: MutableSet<ResolvedArtifact>,
//        ) {
//            artifacts.forEach { visitArtifact(dependency, it) }
//        }
//
//        private fun visitArtifact(
//            dependency: ResolvedDependency,
//            artifact: ResolvedArtifact,
//        ) {
//            val artifactId = artifact.id
//            val componentIdentifier = artifactId.componentIdentifier
//
//            if (artifactId `is` CompositeProjectComponentArtifactMetadata) {
//                visitCompositeProjectDependency(dependency, componentIdentifier as ProjectComponentIdentifier)
//            }
//
//            if (componentIdentifier is ProjectComponentIdentifier && !(artifactId `is` CompositeProjectComponentArtifactMetadata)) {
//                visitProjectDependency(componentIdentifier)
//                return
//            }
//
//            externalGradleDependencies.add(ExternalGradleDependency(dependency, artifact))
//        }
//
//        private fun visitCompositeProjectDependency(
//            dependency: ResolvedDependency,
//            componentIdentifier: ProjectComponentIdentifier,
//        ) {
//            check(target is KotlinJsIrTarget) {
//                """
//                Composite builds for Kotlin/JS are supported only for IR compiler.
//                Use kotlin.js.compiler=ir in gradle.properties or
//                js(IR) {
//                ...
//                }
//                """.trimIndent()
//            }
//
//            (componentIdentifier as DefaultProjectComponentIdentifier).let { identifier ->
//                val includedBuild = project.gradle.includedBuild(identifier.identityPath.topRealPath().name!!)
//                internalCompositeDependencies.add(
//                    CompositeDependency(dependency.moduleName, dependency.moduleVersion, includedBuild.projectDir, includedBuild)
//                )
//            }
//        }
//
//        private fun visitProjectDependency(
//            componentIdentifier: ProjectComponentIdentifier,
//        ) {
//            val dependentProject = project.findProject(componentIdentifier.projectPath)
//                ?: error("Cannot find project ${componentIdentifier.projectPath}")
//
//            rootResolver.findDependentResolver(project, dependentProject)
//                ?.forEach { dependentResolver ->
//                    internalDependencies.add(
//                        InternalDependency(
//                            dependentResolver.projectPath,
//                            dependentResolver.compilationDisambiguatedName,
//                            dependentResolver.npmProject.name
//                        )
//                    )
//                }
//        }
//
//        fun toPackageJsonProducer() = KotlinCompilationNpmResolution(
//            internalDependencies,
//            internalCompositeDependencies,
//            externalGradleDependencies.map {
//                FileExternalGradleDependency(
//                    it.dependency.moduleName,
//                    it.dependency.moduleVersion,
//                    it.artifact.file
//                )
//            },
//            externalNpmDependencies,
//            fileCollectionDependencies,
//            projectPath,
//            compilationDisambiguatedName,
//            npmProject.name,
//            npmVersion,
//            rootResolver.tasksRequirements
//        )
    }
}