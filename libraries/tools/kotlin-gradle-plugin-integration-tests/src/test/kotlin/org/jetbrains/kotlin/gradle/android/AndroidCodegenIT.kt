/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.android

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.testbase.*
import org.jetbrains.kotlin.gradle.utils.GradleVersionTest
import org.junit.jupiter.api.DisplayName

@DisplayName("codegen tests on android")
@AndroidTestVersions(minVersion = TestVersions.AGP.AGP_83, maxVersion = TestVersions.AGP.AGP_83)
@GradleTestVersions(minVersion = TestVersions.Gradle.G_8_4, maxVersion = TestVersions.Gradle.G_8_4)
@AndroidCodegenTests
class AndroidCodegenIT : KGPBaseTest() {
    @GradleAndroidTest
    fun testAndroidCodegen(
        gradleVersion: GradleVersion,
        agpVersion: String,
        jdkVersion: JdkVersions.ProvidedJdk,
    ) {
        project(
            "CodegenTests",
            gradleVersion,
            buildOptions = defaultBuildOptions.copy(
                androidVersion = agpVersion,
                freeArgs = listOf(
                    "-Pandroid.useAndroidX=true",
//                    "-Dorg.gradle.workers.max=1",
//                    "-Pandroid.experimental.testOptions.managedDevices.maxConcurrentDevices=1",
//                    "-Pandroid.experimental.testOptions.managedDevices.setupTimeoutMinutes=0",
//                    "-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect",
//                    "-Pandroid.sdk.channel=3"
                )
            ),
            buildJdk = jdkVersion.location
        ) {
            makeSnapshotTo("/Users/Iaroslav.Postovalov/IdeaProjects/kotlin/build/snapshot-codegen")
            build("pixelCheck", forceOutput = true, enableGradleDaemonMemoryLimitInMb = 6000)
        }
    }
}
