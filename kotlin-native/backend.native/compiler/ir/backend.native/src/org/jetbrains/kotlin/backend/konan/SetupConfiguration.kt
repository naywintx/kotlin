/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan

import org.jetbrains.kotlin.cli.common.arguments.K2NativeCompilerArguments
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoot
import org.jetbrains.kotlin.cli.common.config.kotlinSourceRoots
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.*
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.config.native.*
import org.jetbrains.kotlin.ir.linkage.partial.setupPartialLinkageConfig
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.konan.target.*
import org.jetbrains.kotlin.konan.util.KonanHomeProvider
import org.jetbrains.kotlin.konan.util.visibleName

fun CompilerConfiguration.setupFromArguments(arguments: K2NativeCompilerArguments) = with(NativeConfigurationKeys) {
    val commonSources = arguments.commonSources?.toSet().orEmpty().map { it.absoluteNormalizedFile() }
    val hmppModuleStructure = get(CommonConfigurationKeys.HMPP_MODULE_STRUCTURE)
    arguments.freeArgs.forEach {
        addKotlinSourceRoot(it, isCommon = it.absoluteNormalizedFile() in commonSources, hmppModuleStructure?.getModuleNameForSource(it))
    }

    // Can be overwritten by explicit arguments below.
    parseBinaryOptions(arguments, this@setupFromArguments).forEach { optionWithValue ->
        put(optionWithValue)
    }

    val distribution = Distribution(
            arguments.kotlinHome ?: KonanHomeProvider.determineKonanHome(),
            false,
            arguments.runtimeFile,
            buildMap {
                parseOverrideKonanProperties(arguments, this@setupFromArguments)?.let(this::putAll)
                parseLlvmVariant(arguments, this@setupFromArguments)?.getKonanPropertiesEntry()?.let { (key, value) ->
                    put(key, value)
                }
            },
            arguments.konanDataDir,
    ).also {
        put(DISTRIBUTION, it)
    }
    val platformManager = PlatformManager(distribution).also {
        put(PLATFORM_MANAGER, it)
    }
    val target = platformManager.targetManager(arguments.target).target.also {
        put(TARGET, it)
    }

    put(NODEFAULTLIBS, arguments.nodefaultlibs || !arguments.libraryToAddToCache.isNullOrEmpty())
    put(NOENDORSEDLIBS, arguments.noendorsedlibs || !arguments.libraryToAddToCache.isNullOrEmpty())
    put(NOSTDLIB, arguments.nostdlib || !arguments.libraryToAddToCache.isNullOrEmpty())
    put(NOPACK, arguments.nopack)
    put(NOMAIN, arguments.nomain)
    put(LIBRARY_FILES, arguments.libraries.toNonNullList())
    put(LINKER_ARGS, arguments.linkerArguments.toNonNullList() +
            arguments.singleLinkerArguments.toNonNullList())
    arguments.moduleName?.let { put(MODULE_NAME, it) }

    // TODO: allow overriding the prefix directly.
    arguments.moduleName?.let { put(FULL_EXPORTED_NAME_PREFIX, it) }

    put(INCLUDED_BINARY_FILES, arguments.includeBinaries.toNonNullList())
    put(NATIVE_LIBRARY_FILES, arguments.nativeLibraries.toNonNullList())

    if (arguments.repositories != null) {
        // Show the warning also if `-repo` was really specified.
        report(
                WARNING,
                "'-repo' ('-r') compiler option is deprecated and will be removed in one of the future releases. " +
                        "Please use library paths instead of library names in all compiler options such as '-library' ('-l')."
        )
    }
    put(REPOSITORIES, arguments.repositories.toNonNullList())

    // TODO: Collect all the explicit file names into an object
    // and teach the compiler to work with temporaries and -save-temps.

    arguments.outputName?.let { put(OUTPUT, it) }
    val outputKind = CompilerOutputKind.valueOf(
            (arguments.produce ?: "program").uppercase())
    put(PRODUCE, outputKind)
    putIfNotNull(HEADER_KLIB, arguments.headerKlibPath)

    arguments.libraryVersion?.let { put(LIBRARY_VERSION, it) }

    arguments.mainPackage?.let { put(ENTRY, it) }
    arguments.manifestFile?.let { put(MANIFEST_FILE, it) }
    arguments.temporaryFilesDir?.let { put(TEMPORARY_FILES_DIR, it) }
    put(SAVE_LLVM_IR, arguments.saveLlvmIrAfter.toList())

    put(LIST_TARGETS, arguments.listTargets)
    val optimization = arguments.optimization.also {
        put(OPTIMIZATION, it)
    }
    val debug = arguments.debug.also {
        put(DEBUG, it)
    }
    // TODO: remove after 1.4 release.
    if (arguments.lightDebugDeprecated) {
        report(WARNING,
                "-Xg0 is now deprecated and skipped by compiler. Light debug information is enabled by default for Darwin platforms." +
                        " For other targets, please, use `-Xadd-light-debug=enable` instead.")
    }
    put(LIGHT_DEBUG, when (val it = arguments.lightDebugString) {
        "enable" -> true
        "disable" -> false
        null -> target.family.isAppleFamily // Default is true for Apple targets.
        else -> {
            report(ERROR, "Unsupported -Xadd-light-debug= value: $it. Possible values are 'enable'/'disable'")
            null
        }
    })
    put(GENERATE_DEBUG_TRAMPOLINE, debug && when (val it = arguments.generateDebugTrampolineString) {
        "enable" -> true
        "disable" -> false
        null -> false
        else -> {
            report(ERROR, "Unsupported -Xg-generate-debug-tramboline= value: $it. Possible values are 'enable'/'disable'")
            false
        }
    })
    val produceStaticFramework = selectFrameworkType(this@setupFromArguments, arguments, outputKind).also {
        put(STATIC_FRAMEWORK, it)
    }
    put(OVERRIDE_CLANG_OPTIONS, arguments.clangOptions.toNonNullList())

    put(EXPORT_KDOC, arguments.exportKDoc)

    put(PRINT_IR, arguments.printIr)
    put(PRINT_BITCODE, arguments.printBitCode)
    put(PRINT_FILES, arguments.printFiles)

    put(PURGE_USER_LIBS, arguments.purgeUserLibs)

    put(VERIFY_COMPILER, arguments.verifyCompiler?.let { it == "true" }
            ?: (optimization || !KotlinCompilerVersion.VERSION.isRelease()))
    if (arguments.verifyCompiler != null)
        put(VERIFY_COMPILER, arguments.verifyCompiler == "true")
    put(VERIFY_IR, when (arguments.verifyIr) {
        null -> IrVerificationMode.NONE
        "none" -> IrVerificationMode.NONE
        "warning" -> IrVerificationMode.WARNING
        "error" -> IrVerificationMode.ERROR
        else -> {
            report(ERROR, "Unsupported IR verification mode ${arguments.verifyIr}")
            IrVerificationMode.NONE
        }
    })
    put(VERIFY_BITCODE, arguments.verifyBitCode)

    put(ENABLE_ASSERTIONS, arguments.enableAssertions)

    val memoryModelFromArgument = when (arguments.memoryModel) {
        "relaxed" -> MemoryModel.RELAXED
        "strict" -> MemoryModel.STRICT
        "experimental" -> MemoryModel.EXPERIMENTAL
        null -> null
        else -> {
            report(ERROR, "Unsupported memory model ${arguments.memoryModel}")
            null
        }
    }

    // TODO: revise priority and/or report conflicting values.
    if (get(BinaryOptions.memoryModel) == null) {
        putIfNotNull(BinaryOptions.memoryModel, memoryModelFromArgument)
    }

    when {
        arguments.generateWorkerTestRunner -> put(GENERATE_TEST_RUNNER, TestRunnerKind.WORKER)
        arguments.generateTestRunner -> put(GENERATE_TEST_RUNNER, TestRunnerKind.MAIN_THREAD)
        arguments.generateNoExitTestRunner -> put(GENERATE_TEST_RUNNER, TestRunnerKind.MAIN_THREAD_NO_EXIT)
        else -> put(GENERATE_TEST_RUNNER, TestRunnerKind.NONE)
    }
    // We need to download dependencies only if we use them ( = there are files to compile).
    put(CHECK_DEPENDENCIES,
            kotlinSourceRoots.isNotEmpty()
                    || !arguments.includes.isNullOrEmpty()
                    || !arguments.exportedLibraries.isNullOrEmpty()
                    || outputKind.isCache
                    || arguments.checkDependencies
    )
    if (arguments.friendModules != null)
        put(FRIEND_MODULES, arguments.friendModules!!.split(File.pathSeparator).filterNot(String::isEmpty))

    if (arguments.refinesPaths != null)
        put(REFINES_MODULES, arguments.refinesPaths!!.filterNot(String::isEmpty))

    put(EXPORTED_LIBRARIES, selectExportedLibraries(this@setupFromArguments, arguments, outputKind))
    put(INCLUDED_LIBRARIES, selectIncludes(this@setupFromArguments, arguments, outputKind))
    put(FRAMEWORK_IMPORT_HEADERS, arguments.frameworkImportHeaders.toNonNullList())
    arguments.emitLazyObjCHeader?.let { put(EMIT_LAZY_OBJC_HEADER_FILE, it) }

    put(BITCODE_EMBEDDING_MODE, selectBitcodeEmbeddingMode(this@setupFromArguments, arguments))
    put(DEBUG_INFO_VERSION, arguments.debugInfoFormatVersion.toInt())
    put(OBJC_GENERICS, !arguments.noObjcGenerics)
    put(DEBUG_PREFIX_MAP, parseDebugPrefixMap(arguments, this@setupFromArguments))

    val libraryToAddToCache = parseLibraryToAddToCache(arguments, this@setupFromArguments, outputKind)
    if (libraryToAddToCache != null && !arguments.outputName.isNullOrEmpty())
        report(ERROR, "${K2NativeCompilerArguments.ADD_CACHE} already implicitly sets output file name")
    libraryToAddToCache?.let { put(LIBRARY_TO_ADD_TO_CACHE, it) }
    put(CACHED_LIBRARIES, parseCachedLibraries(arguments, this@setupFromArguments))
    put(CACHE_DIRECTORIES, arguments.cacheDirectories.toNonNullList())
    put(AUTO_CACHEABLE_FROM, arguments.autoCacheableFrom.toNonNullList())
    arguments.autoCacheDir?.let { put(AUTO_CACHE_DIR, it) }
    val incrementalCacheDir = arguments.incrementalCacheDir
    if ((incrementalCacheDir != null) xor (arguments.incrementalCompilation == true))
        report(ERROR, "For incremental compilation both flags should be supplied: " +
                "-Xenable-incremental-compilation and ${K2NativeCompilerArguments.INCREMENTAL_CACHE_DIR}")
    incrementalCacheDir?.let { put(INCREMENTAL_CACHE_DIR, it) }
    arguments.filesToCache?.let { put(FILES_TO_CACHE, it.toList()) }
    put(MAKE_PER_FILE_CACHE, arguments.makePerFileCache)
    val nThreadsRaw = parseBackendThreads(arguments.backendThreads)
    val availableProcessors = Runtime.getRuntime().availableProcessors()
    val nThreads = if (nThreadsRaw == 0) availableProcessors else nThreadsRaw
    if (nThreads > 1) {
        report(LOGGING, "Running backend in parallel with $nThreads threads")
    }
    if (nThreads > availableProcessors) {
        report(WARNING, "The number of threads $nThreads is more than the number of processors $availableProcessors")
    }
    put(CommonConfigurationKeys.PARALLEL_BACKEND_THREADS, nThreads)

    parseShortModuleName(arguments, this@setupFromArguments, outputKind)?.let {
        put(SHORT_MODULE_NAME, it)
    }
    put(FAKE_OVERRIDE_VALIDATOR, arguments.fakeOverrideValidator)
    putIfNotNull(PRE_LINK_CACHES, parsePreLinkCachesValue(this@setupFromArguments, arguments.preLinkCaches))
    put(DESTROY_RUNTIME_MODE, when (arguments.destroyRuntimeMode) {
        null -> DestroyRuntimeMode.ON_SHUTDOWN
        "legacy" -> {
            report(ERROR, "New MM is incompatible with 'legacy' destroy runtime mode.")
            DestroyRuntimeMode.ON_SHUTDOWN
        }
        "on-shutdown" -> {
            report(STRONG_WARNING, "-Xdestroy-runtime-mode switch is deprecated and will be removed in a future release.")
            DestroyRuntimeMode.ON_SHUTDOWN
        }
        else -> {
            report(ERROR, "Unsupported destroy runtime mode ${arguments.destroyRuntimeMode}")
            DestroyRuntimeMode.ON_SHUTDOWN
        }
    })

    val gcFromArgument = when (arguments.gc) {
        null -> null
        "noop" -> GC.NOOP
        "stms" -> GC.STOP_THE_WORLD_MARK_AND_SWEEP
        "cms" -> GC.PARALLEL_MARK_CONCURRENT_SWEEP
        else -> {
            val validValues = enumValues<GC>().map {
                val fullName = "$it".lowercase()
                it.shortcut?.let { short ->
                    "$fullName (or: $short)"
                } ?: fullName
            }.joinToString("|")
            report(ERROR, "Unsupported argument -Xgc=${arguments.gc}. Use -Xbinary=gc= with values ${validValues}")
            null
        }
    }
    if (gcFromArgument != null) {
        val newValue = gcFromArgument.shortcut ?: "$gcFromArgument".lowercase()
        report(WARNING, "-Xgc=${arguments.gc} compiler argument is deprecated. Use -Xbinary=gc=${newValue} instead")
    }
    // TODO: revise priority and/or report conflicting values.
    if (get(BinaryOptions.gc) == null) {
        putIfNotNull(BinaryOptions.gc, gcFromArgument)
    }

    if (arguments.checkExternalCalls != null) {
        report(WARNING, "-Xcheck-state-at-external-calls compiler argument is deprecated. Use -Xbinary=checkStateAtExternalCalls=true instead")
    }
    // TODO: revise priority and/or report conflicting values.
    if (get(BinaryOptions.checkStateAtExternalCalls) == null) {
        putIfNotNull(BinaryOptions.checkStateAtExternalCalls, arguments.checkExternalCalls)
    }

    putIfNotNull(PROPERTY_LAZY_INITIALIZATION, when (arguments.propertyLazyInitialization) {
        null -> null
        "enable" -> true
        "disable" -> false
        else -> {
            report(ERROR, "Expected 'enable' or 'disable' for lazy property initialization")
            false
        }
    })
    put(WORKER_EXCEPTION_HANDLING, when (arguments.workerExceptionHandling) {
        null -> WorkerExceptionHandling.USE_HOOK
        "legacy" -> {
            report(ERROR, "Legacy exception handling in workers is deprecated")
            WorkerExceptionHandling.USE_HOOK
        }
        "use-hook" -> {
            report(STRONG_WARNING, "-Xworker-exception-handling is deprecated")
            WorkerExceptionHandling.USE_HOOK
        }
        else -> {
            report(ERROR, "Unsupported worker exception handling mode ${arguments.workerExceptionHandling}")
            WorkerExceptionHandling.USE_HOOK
        }
    })
    put(LAZY_IR_FOR_CACHES, when (arguments.lazyIrForCaches) {
        null -> false
        "enable" -> true
        "disable" -> false
        else -> {
            report(ERROR, "Expected 'enable' or 'disable' for lazy IR usage for cached libraries")
            false
        }
    })

    arguments.externalDependencies?.let { put(EXTERNAL_DEPENDENCIES, it) }
    put(RUNTIME_LOGS, parseRuntimeLogs(arguments, this@setupFromArguments))
    putIfNotNull(BUNDLE_ID, parseBundleId(arguments, outputKind, this@setupFromArguments))
    arguments.testDumpOutputPath?.let { put(TEST_DUMP_OUTPUT_PATH, it) }

    setupPartialLinkageConfig(
            mode = arguments.partialLinkageMode,
            logLevel = arguments.partialLinkageLogLevel,
            compilerModeAllowsUsingPartialLinkage = outputKind != CompilerOutputKind.LIBRARY, // Don't run PL when producing KLIB.
            onWarning = { report(WARNING, it) },
            onError = { report(ERROR, it) }
    )

    put(OMIT_FRAMEWORK_BINARY, arguments.omitFrameworkBinary.also {
        if (it && outputKind != CompilerOutputKind.FRAMEWORK) {
            report(STRONG_WARNING,
                    "Trying to disable framework binary compilation when producing ${outputKind.name.lowercase()} is meaningless.")
        }
    })
    putIfNotNull(COMPILE_FROM_BITCODE, parseCompileFromBitcode(arguments, this@setupFromArguments, outputKind))
    putIfNotNull(SERIALIZED_DEPENDENCIES, parseSerializedDependencies(arguments, this@setupFromArguments))
    putIfNotNull(SAVE_DEPENDENCIES_PATH, arguments.saveDependenciesPath)
    putIfNotNull(SAVE_LLVM_IR_DIRECTORY, arguments.saveLlvmIrDirectory)

    val sanitizer = update(BinaryOptions.sanitizer) {
        when {
            it == null -> null
            it != SanitizerKind.THREAD -> {
                report(STRONG_WARNING, "${it.name} sanitizer is not supported yet")
                null
            }
            outputKind == CompilerOutputKind.STATIC -> {
                report(STRONG_WARNING, "${it.name} sanitizer is not supported for static library")
                null
            }
            outputKind == CompilerOutputKind.FRAMEWORK && produceStaticFramework -> {
                report(STRONG_WARNING, "${it.name} sanitizer is not supported for static framework")
                null
            }
            it !in target.supportedSanitizers() -> {
                report(STRONG_WARNING, "${it.name} sanitizer is not supported on ${target.name}")
                null
            }
            else -> it
        }
    }
    put(ALLOCATION_MODE, when (arguments.allocator) {
        null -> defaultAllocationMode
        "std" -> AllocationMode.STD
        "mimalloc" -> {
            if (sanitizer != null) {
                report(STRONG_WARNING, "Sanitizers are useful only with the std allocator")
            }
            if (target.supportsMimallocAllocator()) {
                AllocationMode.MIMALLOC
            } else {
                report(STRONG_WARNING, "Mimalloc allocator isn't supported on target ${target.name}. Used standard mode.")
                AllocationMode.STD
            }
        }
        "custom" -> {
            if (sanitizer != null) {
                report(STRONG_WARNING, "Sanitizers are useful only with the std allocator")
            }
            AllocationMode.CUSTOM
        }
        else -> {
            report(ERROR, "Expected 'std', 'mimalloc', or 'custom' for allocator")
            AllocationMode.STD
        }
    })
    update(BinaryOptions.memoryModel) {
        when (it) {
            null -> {}
            MemoryModel.EXPERIMENTAL -> {
                report(STRONG_WARNING, "-memory-model and memoryModel switches are deprecated and will be removed in a future release.")
            }
            else -> {
                report(ERROR, "Legacy MM is deprecated and no longer works")
            }
        }
        MemoryModel.EXPERIMENTAL
    }
    update(BinaryOptions.gc) {
        it ?: defaultGC
    }
    update(BinaryOptions.runtimeAssertionsMode) {
        it ?: defaultRuntimeAssertsMode
    }
    update(BinaryOptions.checkStateAtExternalCalls) {
        it ?: defaultCheckStateAtExternalCalls
    }
    update(BinaryOptions.disableMmap) {
        when (it) {
            null -> defaultDisableMmap
            true -> true
            false -> {
                if (target.family == Family.MINGW) {
                    report(STRONG_WARNING, "MinGW target does not support mmap/munmap")
                    true
                } else {
                    false
                }
            }
        }
    }
    update(BinaryOptions.disableAllocatorOverheadEstimate) {
        it ?: false
    }
    update(BinaryOptions.packFields) {
        it ?: true
    }
    update(BinaryOptions.objcExportSuspendFunctionLaunchThreadRestriction) {
        it ?: ObjCExportSuspendFunctionLaunchThreadRestriction.MAIN
    }
    update(BinaryOptions.freezing) {
        when (it) {
            null -> {}
            Freezing.Disabled -> {
                report(STRONG_WARNING, "freezing switch is deprecated and will be removed in a future release.")
            }
            else -> {
                report(ERROR, "`freezing` is not supported with the new MM. Freezing API is deprecated since 1.7.20. See https://kotlinlang.org/docs/native-migration-guide.html for details")
            }
        }
        Freezing.Disabled
    }
    update(BinaryOptions.sourceInfoType) {
        it
                ?: SourceInfoType.CORESYMBOLICATION.takeIf { debug && target.supportsCoreSymbolication() }
                ?: SourceInfoType.NOOP
    }
    update(BinaryOptions.gcSchedulerType) {
        val arg = it ?: defaultGCSchedulerType
        arg.deprecatedWithReplacement?.let { replacement ->
            report(WARNING, "Binary option gcSchedulerType=$arg is deprecated. Use gcSchedulerType=$replacement instead")
            replacement
        } ?: arg
    }
    update(BinaryOptions.gcMarkSingleThreaded) {
        it ?: defaultGcMarkSingleThreaded
    }
    update(BinaryOptions.concurrentWeakSweep) {
        it ?: true
    }
    update(BinaryOptions.gcMutatorsCooperate) {
        if (getNotNull(BinaryOptions.gcMarkSingleThreaded)) {
            if (it == true) {
                report(STRONG_WARNING, "Mutators cooperation is not supported during single threaded mark")
            }
            false
        } else if (getNotNull(BinaryOptions.gc) == GC.CONCURRENT_MARK_AND_SWEEP) {
            if (it == true) {
                report(STRONG_WARNING, "Mutators cooperation is not yet supported in CMS GC")
            }
            false
        } else {
            it ?: true
        }
    }
    update(BinaryOptions.auxGCThreads) {
        if (getNotNull(BinaryOptions.gcMarkSingleThreaded)) {
            if (it != null && it != 0U) {
                report(STRONG_WARNING, "Auxiliary GC workers are not supported during single threaded mark")
            }
            0U
        } else {
            it ?: 0U
        }
    }
    update(BinaryOptions.appStateTracking) {
        it ?: AppStateTracking.DISABLED
    }
    update(BinaryOptions.mimallocUseDefaultOptions) {
        it ?: false
    }
    update(BinaryOptions.mimallocUseCompaction) {
        // Turned off by default, because it slows down allocation.
        it ?: false
    }
    update(BinaryOptions.objcDisposeOnMain) {
        it ?: true
    }
    update(BinaryOptions.objcDisposeWithRunLoop) {
        it ?: true
    }
    update(BinaryOptions.enableSafepointSignposts) {
        it?.also {
            if (it && !target.supportsSignposts) {
                report(STRONG_WARNING, "Signposts are not available on $target. The setting will have no effect.")
            }
        } ?: target.supportsSignposts
    }
    update(BinaryOptions.globalDataLazyInit) {
        it ?: true
    }
    update(BinaryOptions.unitSuspendFunctionObjCExport) {
        it ?: UnitSuspendFunctionObjCExport.DEFAULT
    }
    update(BinaryOptions.stripDebugInfoFromNativeLibs) {
        it ?: true
    }
}

private inline fun <T> CompilerConfiguration.update(key: CompilerConfigurationKey<T>, block: (T?) -> T?): T? {
    val result = get(key).run(block)
    putIfNotNull(key, result)
    return result
}

private fun String.absoluteNormalizedFile() = java.io.File(this).absoluteFile.normalize()

private fun String.isRelease(): Boolean {
    // major.minor.patch-meta-build where patch, meta and build are optional.
    val versionPattern = "(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-(\\p{Alpha}*\\p{Alnum}|[\\p{Alpha}-]*))?(?:-(\\d+))?".toRegex()
    val (_, _, _, metaString, build) = versionPattern.matchEntire(this)?.destructured
            ?: throw IllegalStateException("Cannot parse Kotlin/Native version: $this")

    return metaString.isEmpty() && build.isEmpty()
}

internal fun CompilerConfiguration.setupCommonOptionsForCaches(konanConfig: KonanConfig) = with(NativeConfigurationKeys) {
    put(TARGET, konanConfig.target)
    put(DEBUG, konanConfig.debug)
    setupPartialLinkageConfig(konanConfig.partialLinkageConfig)
    putIfNotNull(EXTERNAL_DEPENDENCIES, konanConfig.externalDependenciesFile?.absolutePath)
    put(PROPERTY_LAZY_INITIALIZATION, konanConfig.propertyLazyInitialization)
    put(BinaryOptions.stripDebugInfoFromNativeLibs, konanConfig.stripDebugInfoFromNativeLibs)
    put(ALLOCATION_MODE, konanConfig.configuration.getNotNull(ALLOCATION_MODE))
    put(BinaryOptions.gc, konanConfig.configuration.getNotNull(BinaryOptions.gc))
    put(BinaryOptions.gcSchedulerType, konanConfig.configuration.getNotNull(BinaryOptions.gcSchedulerType))
    put(BinaryOptions.runtimeAssertionsMode, konanConfig.configuration.getNotNull(BinaryOptions.runtimeAssertionsMode))
    put(LAZY_IR_FOR_CACHES, konanConfig.lazyIrForCaches)
    put(CommonConfigurationKeys.PARALLEL_BACKEND_THREADS, konanConfig.threadsCount)
    put(DISTRIBUTION, konanConfig.distribution)
}

private fun Array<String>?.toNonNullList() = this?.asList().orEmpty()

private fun selectFrameworkType(
        configuration: CompilerConfiguration,
        arguments: K2NativeCompilerArguments,
        outputKind: CompilerOutputKind
): Boolean {
    return if (outputKind != CompilerOutputKind.FRAMEWORK && arguments.staticFramework) {
        configuration.report(
                STRONG_WARNING,
                "'${K2NativeCompilerArguments.STATIC_FRAMEWORK_FLAG}' is only supported when producing frameworks, " +
                        "but the compiler is producing ${outputKind.name.lowercase()}"
        )
        false
    } else {
        arguments.staticFramework
    }
}

private fun parsePreLinkCachesValue(
        configuration: CompilerConfiguration,
        value: String?
): Boolean? = when (value) {
    "enable" -> true
    "disable" -> false
    null -> null
    else -> {
        configuration.report(ERROR, "Unsupported `-Xpre-link-caches` value: $value. Possible values are 'enable'/'disable'")
        null
    }
}

private fun selectBitcodeEmbeddingMode(
        configuration: CompilerConfiguration,
        arguments: K2NativeCompilerArguments
): BitcodeEmbedding.Mode = when {
    arguments.embedBitcodeMarker -> {
        if (arguments.embedBitcode) {
            configuration.report(
                    STRONG_WARNING,
                    "'${K2NativeCompilerArguments.EMBED_BITCODE_FLAG}' is ignored because '${K2NativeCompilerArguments.EMBED_BITCODE_MARKER_FLAG}' is specified"
            )
        }
        BitcodeEmbedding.Mode.MARKER
    }
    arguments.embedBitcode -> {
        BitcodeEmbedding.Mode.FULL
    }
    else -> BitcodeEmbedding.Mode.NONE
}

private fun selectExportedLibraries(
        configuration: CompilerConfiguration,
        arguments: K2NativeCompilerArguments,
        outputKind: CompilerOutputKind
): List<String> {
    val exportedLibraries = arguments.exportedLibraries?.toList().orEmpty()

    return if (exportedLibraries.isNotEmpty() && outputKind != CompilerOutputKind.FRAMEWORK &&
            outputKind != CompilerOutputKind.STATIC && outputKind != CompilerOutputKind.DYNAMIC) {
        configuration.report(STRONG_WARNING,
                "-Xexport-library is only supported when producing frameworks or native libraries, " +
                        "but the compiler is producing ${outputKind.name.lowercase()}")

        emptyList()
    } else {
        exportedLibraries
    }
}

private fun selectIncludes(
        configuration: CompilerConfiguration,
        arguments: K2NativeCompilerArguments,
        outputKind: CompilerOutputKind
): List<String> {
    val includes = arguments.includes?.toList().orEmpty()

    return if (includes.isNotEmpty() && outputKind == CompilerOutputKind.LIBRARY) {
        configuration.report(
                ERROR,
                "The ${K2NativeCompilerArguments.INCLUDE_ARG} flag is not supported when producing ${outputKind.name.lowercase()}"
        )
        emptyList()
    } else {
        includes
    }
}

private fun parseCachedLibraries(
        arguments: K2NativeCompilerArguments,
        configuration: CompilerConfiguration
): Map<String, String> = arguments.cachedLibraries?.asList().orEmpty().mapNotNull {
    val libraryAndCache = it.split(",")
    if (libraryAndCache.size != 2) {
        configuration.report(
                ERROR,
                "incorrect ${K2NativeCompilerArguments.CACHED_LIBRARY} format: expected '<library>,<cache>', got '$it'"
        )
        null
    } else {
        libraryAndCache[0] to libraryAndCache[1]
    }
}.toMap()

private fun parseLibraryToAddToCache(
        arguments: K2NativeCompilerArguments,
        configuration: CompilerConfiguration,
        outputKind: CompilerOutputKind
): String? {
    val input = arguments.libraryToAddToCache

    return if (input != null && !outputKind.isCache) {
        configuration.report(ERROR, "${K2NativeCompilerArguments.ADD_CACHE} can't be used when not producing cache")
        null
    } else {
        input
    }
}

private fun parseBackendThreads(stringValue: String): Int {
    val value = stringValue.toIntOrNull()
            ?: throw KonanCompilationException("Cannot parse -Xbackend-threads value: \"$stringValue\". Please use an integer number")
    if (value < 0)
        throw KonanCompilationException("-Xbackend-threads value cannot be negative")
    return value
}

// TODO: Support short names for current module in ObjC export and lift this limitation.
private fun parseShortModuleName(
        arguments: K2NativeCompilerArguments,
        configuration: CompilerConfiguration,
        outputKind: CompilerOutputKind
): String? {
    val input = arguments.shortModuleName

    return if (input != null && outputKind != CompilerOutputKind.LIBRARY) {
        configuration.report(
                STRONG_WARNING,
                "${K2NativeCompilerArguments.SHORT_MODULE_NAME_ARG} is only supported when producing a Kotlin library, " +
                        "but the compiler is producing ${outputKind.name.lowercase()}"
        )
        null
    } else {
        input
    }
}

private fun parseDebugPrefixMap(
        arguments: K2NativeCompilerArguments,
        configuration: CompilerConfiguration
): Map<String, String> = arguments.debugPrefixMap?.asList().orEmpty().mapNotNull {
    val libraryAndCache = it.split("=")
    if (libraryAndCache.size != 2) {
        configuration.report(ERROR, "incorrect debug prefix map format: expected '<old>=<new>', got '$it'")
        null
    } else {
        libraryAndCache[0] to libraryAndCache[1]
    }
}.toMap()

class BinaryOptionWithValue<T : Any>(val option: BinaryOption<T>, val value: T)

private fun <T : Any> CompilerConfiguration.put(binaryOptionWithValue: BinaryOptionWithValue<T>) {
    this.put(binaryOptionWithValue.option.compilerConfigurationKey, binaryOptionWithValue.value)
}

fun parseBinaryOptions(
        arguments: K2NativeCompilerArguments,
        configuration: CompilerConfiguration
): List<BinaryOptionWithValue<*>> {
    val keyValuePairs = parseKeyValuePairs(arguments.binaryOptions, configuration) ?: return emptyList()

    return keyValuePairs.mapNotNull { (key, value) ->
        val option = BinaryOptions.getByName(key)
        if (option == null) {
            configuration.report(STRONG_WARNING, "Unknown binary option '$key'")
            null
        } else {
            parseBinaryOption(option, value, configuration)
        }
    }
}

private fun <T : Any> parseBinaryOption(
        option: BinaryOption<T>,
        valueName: String,
        configuration: CompilerConfiguration
): BinaryOptionWithValue<T>? {
    val value = option.valueParser.parse(valueName)
    return if (value == null) {
        configuration.report(STRONG_WARNING, "Unknown value '$valueName' of binary option '${option.name}'. " +
                "Possible values are: ${option.valueParser.validValuesHint}")
        null
    } else {
        BinaryOptionWithValue(option, value)
    }
}

private fun parseOverrideKonanProperties(
        arguments: K2NativeCompilerArguments,
        configuration: CompilerConfiguration
): Map<String, String>? = parseKeyValuePairs(arguments.overrideKonanProperties, configuration)

private fun parseKeyValuePairs(
        argumentValue: Array<String>?,
        configuration: CompilerConfiguration
): Map<String, String>? = argumentValue?.mapNotNull {
    val keyValueSeparatorIndex = it.indexOf('=')
    if (keyValueSeparatorIndex > 0) {
        it.substringBefore('=') to it.substringAfter('=')
    } else {
        configuration.report(ERROR, "incorrect property format: expected '<key>=<value>', got '$it'")
        null
    }
}?.toMap()

private fun parseBundleId(
        arguments: K2NativeCompilerArguments,
        outputKind: CompilerOutputKind,
        configuration: CompilerConfiguration
): String? {
    val argumentValue = arguments.bundleId
    return if (argumentValue != null && outputKind != CompilerOutputKind.FRAMEWORK) {
        configuration.report(STRONG_WARNING, "Setting a bundle ID is only supported when producing a framework " +
                "but the compiler is producing ${outputKind.name.lowercase()}")
        null
    } else {
        argumentValue
    }
}

private fun parseSerializedDependencies(
        arguments: K2NativeCompilerArguments,
        configuration: CompilerConfiguration
): String? {
    if (!arguments.serializedDependencies.isNullOrEmpty() && arguments.compileFromBitcode.isNullOrEmpty()) {
        configuration.report(STRONG_WARNING,
                "Providing serialized dependencies only works in conjunction with a bitcode file to compile.")
    }
    return arguments.serializedDependencies
}

private fun parseCompileFromBitcode(
        arguments: K2NativeCompilerArguments,
        configuration: CompilerConfiguration,
        outputKind: CompilerOutputKind,
): String? {
    if (!arguments.compileFromBitcode.isNullOrEmpty() && !outputKind.involvesBitcodeGeneration) {
        configuration.report(ERROR,
                "Compilation from bitcode is not available when producing ${outputKind.visibleName}")
    }
    return arguments.compileFromBitcode
}

private fun parseLlvmVariant(
        arguments: K2NativeCompilerArguments,
        configuration: CompilerConfiguration,
) = when (val variant = arguments.llvmVariant) {
    "user" -> LlvmVariant.User
    "dev" -> LlvmVariant.Dev
    null -> null
    else -> {
        val file = File(variant)
        if (!file.exists) {
            configuration.report(ERROR, "`-Xllvm-variant` should be `user`, `dev` or an absolute path. Got: $variant")
            null
        } else {
            LlvmVariant.Custom(file)
        }
    }
}

private fun parseRuntimeLogs(
        arguments: K2NativeCompilerArguments,
        configuration: CompilerConfiguration,
): Map<LoggingTag, LoggingLevel> {
    val default = configuration.defaultRuntimeLogs

    val cfgString = arguments.runtimeLogs ?: return default

    fun <T> error(message: String): T? {
        configuration.report(STRONG_WARNING, "$message. No logging will be performed.")
        return null
    }

    fun parseSingleTagLevel(tagLevel: String): Pair<LoggingTag, LoggingLevel>? {
        val parts = tagLevel.split("=")
        val tagStr = parts[0]
        val tag = tagStr.let {
            LoggingTag.parse(it) ?: error("Failed to parse log tag at \"$tagStr\"")
        }
        val levelStr = parts.getOrNull(1) ?: error("Failed to parse log tag-level pair at \"$tagLevel\"")
        val level = parts.getOrNull(1)?.let {
            LoggingLevel.parse(it) ?: error("Failed to parse log level at \"$levelStr\"")
        }
        if (level == LoggingLevel.None) return error("Invalid log level: \"$levelStr\"")
        return tag?.let { t -> level?.let { l -> Pair(t, l) } }
    }

    val configured = cfgString.split(",").map { parseSingleTagLevel(it) ?: return default }
    return default + configured
}