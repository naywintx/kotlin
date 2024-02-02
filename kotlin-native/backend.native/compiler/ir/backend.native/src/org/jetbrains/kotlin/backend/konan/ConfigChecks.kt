/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan

import org.jetbrains.kotlin.config.native.NativeConfigurationKeys
import org.jetbrains.kotlin.konan.target.needSmallBinary

/**
 * Convenient methods to check compilation parameters.
 */
interface ConfigChecks {

    val config: KonanConfig

    fun shouldExportKDoc() = config.configuration.getBoolean(NativeConfigurationKeys.EXPORT_KDOC)

    fun shouldVerifyBitCode() = config.configuration.getBoolean(NativeConfigurationKeys.VERIFY_BITCODE)

    fun shouldPrintBitCode() = config.configuration.getBoolean(NativeConfigurationKeys.PRINT_BITCODE)

    fun shouldPrintFiles() = config.configuration.getBoolean(NativeConfigurationKeys.PRINT_FILES)

    fun shouldContainDebugInfo() = config.debug

    fun shouldContainLocationDebugInfo() = shouldContainDebugInfo() || config.lightDebug

    fun shouldContainAnyDebugInfo() = shouldContainDebugInfo() || shouldContainLocationDebugInfo()

    fun shouldUseDebugInfoFromNativeLibs() = shouldContainAnyDebugInfo() && config.useDebugInfoInNativeLibs

    fun shouldOptimize() = config.optimizationsEnabled

    fun shouldInlineSafepoints() = !config.target.needSmallBinary()

    fun useLazyFileInitializers() = config.propertyLazyInitialization

}