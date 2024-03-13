/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.toolchain

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.Internal
import org.jetbrains.kotlin.gradle.targets.native.internal.NativeDistributionCommonizerLock
import org.jetbrains.kotlin.gradle.targets.native.internal.NativeDistributionTypeProvider
import org.jetbrains.kotlin.gradle.targets.native.internal.PlatformLibrariesGenerator
import org.jetbrains.kotlin.gradle.tasks.withType
import org.jetbrains.kotlin.gradle.utils.SingleActionPerProject
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import javax.inject.Inject

private const val KONAN_DIRECTORY_NAME_TO_CHECK_EXISTENCE = "konan"

internal interface UsesKotlinNativeBundleBuildService : Task {
    @get:Internal
    val kotlinNativeBundleBuildService: Property<KotlinNativeBundleBuildService>
}

/**
 * This service provides functionality to prepare a Kotlin/Native bundle.
 */
internal abstract class KotlinNativeBundleBuildService : BuildService<BuildServiceParameters.None> {

    @get:Inject
    abstract val fso: FileSystemOperations

    private var canBeReinstalled: Boolean = true // we can reinstall a k/n bundle once during the build

    companion object {
        fun registerIfAbsent(project: Project): Provider<KotlinNativeBundleBuildService> =
            project.gradle.sharedServices.registerIfAbsent(
                "kotlinNativeBundleBuildService",
                KotlinNativeBundleBuildService::class.java
            ) {}.also { serviceProvider ->
                SingleActionPerProject.run(project, UsesKotlinNativeBundleBuildService::class.java.name) {
                    project.tasks.withType<UsesKotlinNativeBundleBuildService>().configureEach { task ->
                        task.kotlinNativeBundleBuildService.value(serviceProvider).disallowChanges()
                        task.usesService(serviceProvider)
                    }
                }
            }
    }

    internal fun prepareKotlinNativeBundle(
        project: Project,
        kotlinNativeCompilerConfiguration: ConfigurableFileCollection,
        kotlinNativeVersion: String,
        bundleDir: File,
        reinstallFlag: Boolean,
        konanTargets: Set<KonanTarget>,
    ) {

        val lock =
            NativeDistributionCommonizerLock(bundleDir) { message -> project.logger.info("Kotlin Native Bundle: $message") }

        val checkSum = if (kotlinNativeVersion.endsWith("SNAPSHOT")) {
            kotlinNativeCompilerConfiguration
                .singleOrNull()
                ?.resolve(kotlinNativeVersion)
                ?.let { getCheckSum(it) }
        } else null

        lock.withLock {
            val checkSumFile = bundleDir.resolve("checksum")
            val currentCheckSum = if (checkSumFile.exists()) checkSumFile.readText() else null

            val needToReinstall = (checkSum != null && checkSum != currentCheckSum)
            if (needToReinstall) {
                project.logger.info("Delete existed Kotlin/Native ($currentCheckSum) because snapshot version was updated to $it")
            }

            removeBundleIfNeeded(reinstallFlag || needToReinstall, bundleDir)

            if (!bundleDir.resolve(KONAN_DIRECTORY_NAME_TO_CHECK_EXISTENCE).exists()) {
                val gradleCachesKotlinNativeDir =
                    resolveKotlinNativeConfiguration(kotlinNativeVersion, kotlinNativeCompilerConfiguration)

                project.logger.info("Moving Kotlin/Native bundle from tmp directory $gradleCachesKotlinNativeDir to ${bundleDir.absolutePath}")
                fso.copy {
                    it.from(gradleCachesKotlinNativeDir)
                    it.into(bundleDir)
                }
                checkSum?.also {
                    bundleDir.resolve("checksum").writeText(it)
                }
                project.logger.info("Moved Kotlin/Native bundle from $gradleCachesKotlinNativeDir to ${bundleDir.absolutePath}")
            }
        }

        project.setupKotlinNativeDependencies(konanTargets)
    }

    private fun removeBundleIfNeeded(
        reinstallFlag: Boolean,
        bundleDir: File,
    ) {
        if (reinstallFlag && canBeReinstalled) {
            bundleDir.deleteRecursively()
            canBeReinstalled = false // we don't need to reinstall k/n if it was reinstalled once during the same build
        }
    }

    private fun resolveKotlinNativeConfiguration(
        kotlinNativeVersion: String,
        kotlinNativeCompilerConfiguration: ConfigurableFileCollection,
    ): File {
        val resolutionErrorMessage = "Kotlin Native dependency has not been properly resolved. " +
                "Please, make sure that you've declared the repository, which contains $kotlinNativeVersion."

        val gradleCachesKotlinNativeDir = kotlinNativeCompilerConfiguration
            .singleOrNull()
            ?.resolve(kotlinNativeVersion)
            ?: error(resolutionErrorMessage)

        if (!gradleCachesKotlinNativeDir.exists()) {
            throw IllegalArgumentException(resolutionErrorMessage)
        }
        return gradleCachesKotlinNativeDir
    }

    private fun Project.setupKotlinNativeDependencies(konanTargets: Set<KonanTarget>) {
        val distributionType = NativeDistributionTypeProvider(this).getDistributionType()
        if (distributionType.mustGeneratePlatformLibs) {
            konanTargets.forEach { konanTarget ->
                PlatformLibrariesGenerator(project, konanTarget).generatePlatformLibsIfNeeded()
            }
        }
    }


    private fun getCheckSum(directory: File): String? {
        if (!directory.exists()) return null

        val md5 = MessageDigest.getInstance("MD5")

        Files.walkFileTree(directory.toPath(), object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes) =
                if (dir.endsWith("cache")) {
                    FileVisitResult.SKIP_SUBTREE
                } else {
                    FileVisitResult.CONTINUE
                }

            override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                file?.toFile()?.inputStream()?.buffered()?.use { fileStream -> md5.update(fileStream.readAllBytes()) }
                return super.visitFile(file, attrs)
            }


            override fun postVisitDirectory(dir: Path?, exc: IOException?): FileVisitResult {
                val hash = md5.digest()
                md5.update(hash)
                return super.postVisitDirectory(dir, exc)
            }
        })

        return "%032x".format(BigInteger(1, md5.digest()))
    }

}
