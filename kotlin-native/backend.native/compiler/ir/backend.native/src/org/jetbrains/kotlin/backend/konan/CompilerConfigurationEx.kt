/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.config.native.*
import org.jetbrains.kotlin.konan.target.Family
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

fun CompilerConfiguration.report(priority: CompilerMessageSeverity, message: String) =
    getNotNull(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY).report(priority, message)

fun <T> CompilerConfiguration.getting(key: CompilerConfigurationKey<T>): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, T>> = PropertyDelegateProvider { _, _ ->
        ReadOnlyProperty { _, _ ->
            getNotNull(key)
        }
    }

val CompilerConfiguration.defaultGC
    get() = GC.PARALLEL_MARK_CONCURRENT_SWEEP

val CompilerConfiguration.defaultRuntimeAssertsMode
    get() = RuntimeAssertsMode.IGNORE

val CompilerConfiguration.defaultCheckStateAtExternalCalls
    get() = false

val CompilerConfiguration.defaultDisableMmap
    get() = getNotNull(NativeConfigurationKeys.TARGET).family == Family.MINGW

val CompilerConfiguration.defaultRuntimeLogs
    get() = LoggingTag.entries.associateWith { LoggingLevel.None }

val CompilerConfiguration.defaultGCSchedulerType
    get() = when (getNotNull(BinaryOptions.gc)) {
        GC.NOOP -> GCSchedulerType.MANUAL
        else -> GCSchedulerType.ADAPTIVE
    }

val CompilerConfiguration.defaultGcMarkSingleThreaded
    get() = getNotNull(NativeConfigurationKeys.TARGET).family == Family.MINGW && getNotNull(BinaryOptions.gc) == GC.PARALLEL_MARK_CONCURRENT_SWEEP

val CompilerConfiguration.defaultAllocationMode
    get() = if (get(BinaryOptions.sanitizer) == null) {
        AllocationMode.CUSTOM
    } else {
        AllocationMode.STD
    }