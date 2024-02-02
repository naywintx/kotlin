/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan

import com.google.common.base.StandardSystemProperty
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.backend.common.linkage.issues.UserVisibleIrModulesSupport
import org.jetbrains.kotlin.backend.konan.serialization.KonanUserVisibleIrModulesSupport
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.config.kotlinSourceRoots
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.config.native.*
import org.jetbrains.kotlin.ir.linkage.partial.partialLinkageConfig
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.konan.library.KonanLibrary
import org.jetbrains.kotlin.konan.properties.loadProperties
import org.jetbrains.kotlin.konan.target.*
import org.jetbrains.kotlin.konan.util.visibleName
import org.jetbrains.kotlin.library.metadata.resolver.TopologicalLibraryOrder
import org.jetbrains.kotlin.util.removeSuffixIfPresent
import java.nio.file.Files
import java.nio.file.Paths

class KonanConfig(val project: Project, val configuration: CompilerConfiguration) {
    internal val distribution = configuration.getNotNull(NativeConfigurationKeys.DISTRIBUTION)
    internal val platformManager = configuration.getNotNull(NativeConfigurationKeys.PLATFORM_MANAGER)
    internal val target = configuration.getNotNull(NativeConfigurationKeys.TARGET)

    internal val produce by configuration.getting(NativeConfigurationKeys.PRODUCE)
    internal val produceStaticFramework by configuration.getting(NativeConfigurationKeys.STATIC_FRAMEWORK)

    // TODO: debug info generation mode and debug/release variant selection probably requires some refactoring.
    val debug by configuration.getting(NativeConfigurationKeys.DEBUG)
    val lightDebug = configuration.getBoolean(NativeConfigurationKeys.LIGHT_DEBUG)
    val generateDebugTrampoline = configuration.getBoolean(NativeConfigurationKeys.GENERATE_DEBUG_TRAMPOLINE)
    val optimizationsEnabled = configuration.getBoolean(NativeConfigurationKeys.OPTIMIZATION)

    val packFields = configuration.getNotNull(BinaryOptions.packFields)

    internal val unitSuspendFunctionObjCExport by configuration.getting(BinaryOptions.unitSuspendFunctionObjCExport)

    internal val stripDebugInfoFromNativeLibs by configuration.getting(BinaryOptions.stripDebugInfoFromNativeLibs)

    internal val threadsCount = configuration.get(CommonConfigurationKeys.PARALLEL_BACKEND_THREADS) ?: 1

    internal val omitFrameworkBinary = configuration.getting(NativeConfigurationKeys.OMIT_FRAMEWORK_BINARY)

    internal val cInterfaceGenerationMode: CInterfaceGenerationMode by lazy {
        val explicitMode = configuration.get(BinaryOptions.cInterfaceMode)
        when {
            explicitMode != null -> explicitMode
            produce == CompilerOutputKind.DYNAMIC || produce == CompilerOutputKind.STATIC -> CInterfaceGenerationMode.V1
            else -> CInterfaceGenerationMode.NONE
        }
    }

    init {
        if (!platformManager.isEnabled(target)) {
            error("Target ${target.visibleName} is not available on the ${HostManager.hostName} host")
        }
    }

    val platform = platformManager.platform(target).apply {
        if (configuration.getBoolean(NativeConfigurationKeys.CHECK_DEPENDENCIES)) {
            downloadDependencies()
        }
    }

    internal val clang = platform.clang
    val indirectBranchesAreAllowed = target != KonanTarget.WASM32
    val threadsAreAllowed = (target != KonanTarget.WASM32) && (target !is KonanTarget.ZEPHYR)


    internal val metadataKlib get() = configuration.getBoolean(CommonConfigurationKeys.METADATA_KLIB)

    internal val headerKlibPath get() = configuration.get(NativeConfigurationKeys.HEADER_KLIB)

    internal val purgeUserLibs: Boolean
        get() = configuration.getBoolean(NativeConfigurationKeys.PURGE_USER_LIBS)

    internal val resolve = KonanLibrariesResolveSupport(
            configuration, target, distribution, resolveManifestDependenciesLenient = metadataKlib
    )

    val resolvedLibraries get() = resolve.resolvedLibraries

    internal val externalDependenciesFile = configuration.get(NativeConfigurationKeys.EXTERNAL_DEPENDENCIES)?.let(::File)

    internal val userVisibleIrModulesSupport = KonanUserVisibleIrModulesSupport(
            externalDependenciesLoader = UserVisibleIrModulesSupport.ExternalDependenciesLoader.from(
                    externalDependenciesFile = externalDependenciesFile,
                    onMalformedExternalDependencies = { warningMessage ->
                        configuration.report(CompilerMessageSeverity.STRONG_WARNING, warningMessage)
                    }),
            konanKlibDir = File(distribution.klib)
    )

    val fullExportedNamePrefix: String
        get() = configuration.get(NativeConfigurationKeys.FULL_EXPORTED_NAME_PREFIX) ?: implicitModuleName

    val moduleId: String
        get() = configuration.get(NativeConfigurationKeys.MODULE_NAME) ?: implicitModuleName

    val shortModuleName: String?
        get() = configuration.get(NativeConfigurationKeys.SHORT_MODULE_NAME)

    fun librariesWithDependencies(): List<KonanLibrary> {
        return resolvedLibraries.filterRoots { (!it.isDefault && !this.purgeUserLibs) || it.isNeededForLink }.getFullList(TopologicalLibraryOrder).map { it as KonanLibrary }
    }

    internal val runtimeNativeLibraries: List<String> = mutableListOf<String>().apply {
        if (debug) add("debug.bc")
        add("runtime.bc")
        add("mm.bc")
        add("common_alloc.bc")
        add("common_gc.bc")
        add("common_gcScheduler.bc")
        val gcSchedulerType = configuration.getNotNull(BinaryOptions.gcSchedulerType)
        when (gcSchedulerType) {
            GCSchedulerType.MANUAL -> {
                add("manual_gcScheduler.bc")
            }
            GCSchedulerType.ADAPTIVE -> {
                add("adaptive_gcScheduler.bc")
            }
            GCSchedulerType.AGGRESSIVE -> {
                add("aggressive_gcScheduler.bc")
            }
            GCSchedulerType.DISABLED, GCSchedulerType.WITH_TIMER, GCSchedulerType.ON_SAFE_POINTS -> {
                throw IllegalStateException("Deprecated options must have already been handled")
            }
        }
        val gc = configuration.getNotNull(BinaryOptions.gc)
        val allocationMode = configuration.getNotNull(NativeConfigurationKeys.ALLOCATION_MODE)
        if (allocationMode == AllocationMode.CUSTOM) {
            when (gc) {
                GC.STOP_THE_WORLD_MARK_AND_SWEEP -> add("same_thread_ms_gc_custom.bc")
                GC.NOOP -> add("noop_gc_custom.bc")
                GC.PARALLEL_MARK_CONCURRENT_SWEEP -> add("pmcs_gc_custom.bc")
                GC.CONCURRENT_MARK_AND_SWEEP -> add("concurrent_ms_gc_custom.bc")
            }
        } else {
            when (gc) {
                GC.STOP_THE_WORLD_MARK_AND_SWEEP -> add("same_thread_ms_gc.bc")
                GC.NOOP -> add("noop_gc.bc")
                GC.PARALLEL_MARK_CONCURRENT_SWEEP -> add("pmcs_gc.bc")
                GC.CONCURRENT_MARK_AND_SWEEP -> add("concurrent_ms_gc.bc")
            }
        }
        if (target.supportsCoreSymbolication()) {
            add("source_info_core_symbolication.bc")
        }
        if (target.supportsLibBacktrace()) {
            add("source_info_libbacktrace.bc")
            add("libbacktrace.bc")
        }
        when (allocationMode) {
            AllocationMode.MIMALLOC -> {
                add("legacy_alloc.bc")
                add("mimalloc_alloc.bc")
                add("mimalloc.bc")
            }
            AllocationMode.STD -> {
                add("legacy_alloc.bc")
                add("std_alloc.bc")
            }
            AllocationMode.CUSTOM -> {
                add("custom_alloc.bc")
            }
        }
        when (configuration.getNotNull(BinaryOptions.checkStateAtExternalCalls)) {
            true -> add("impl_externalCallsChecker.bc")
            false -> add("noop_externalCallsChecker.bc")
        }
    }.map {
        File(distribution.defaultNatives(target)).child(it).absolutePath
    }

    internal val launcherNativeLibraries: List<String> = distribution.launcherFiles.map {
        File(distribution.defaultNatives(target)).child(it).absolutePath
    }

    internal val objCNativeLibrary: String =
            File(distribution.defaultNatives(target)).child("objc.bc").absolutePath

    internal val xcTestLauncherNativeLibrary: String =
            File(distribution.defaultNatives(target)).child("xctest_launcher.bc").absolutePath

    internal val exceptionsSupportNativeLibrary: String =
            File(distribution.defaultNatives(target)).child("exceptionsSupport.bc").absolutePath

    internal val nativeLibraries: List<String> =
            configuration.getList(NativeConfigurationKeys.NATIVE_LIBRARY_FILES)

    internal val includeBinaries: List<String> =
            configuration.getList(NativeConfigurationKeys.INCLUDED_BINARY_FILES)

    internal val languageVersionSettings =
            configuration.get(CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS)!!

    internal val friendModuleFiles: Set<File> =
            configuration.get(NativeConfigurationKeys.FRIEND_MODULES)?.map { File(it) }?.toSet() ?: emptySet()

    internal val refinesModuleFiles: Set<File> =
            configuration.get(NativeConfigurationKeys.REFINES_MODULES)?.map { File(it) }?.toSet().orEmpty()

    internal val manifestProperties = configuration.get(NativeConfigurationKeys.MANIFEST_FILE)?.let {
        File(it).loadProperties()
    }

    internal val isInteropStubs: Boolean get() = manifestProperties?.getProperty("interop") == "true"

    private val defaultPropertyLazyInitialization = true
    internal val propertyLazyInitialization: Boolean get() = configuration.get(NativeConfigurationKeys.PROPERTY_LAZY_INITIALIZATION)?.also {
        if (!it) {
            configuration.report(CompilerMessageSeverity.STRONG_WARNING, "Eager property initialization is deprecated")
        }
    } ?: defaultPropertyLazyInitialization

    internal val lazyIrForCaches: Boolean get() = configuration.get(NativeConfigurationKeys.LAZY_IR_FOR_CACHES)!!

    internal val entryPointName: String by lazy {
        if (target.family == Family.ANDROID) {
            val androidProgramType = configuration.get(BinaryOptions.androidProgramType)
                    ?: AndroidProgramType.Default
            androidProgramType.konanMainOverride?.let {
                return@lazy it
            }
        }
        "Konan_main"
    }

    internal val testDumpFile: File? = configuration[NativeConfigurationKeys.TEST_DUMP_OUTPUT_PATH]?.let(::File)

    internal val partialLinkageConfig = configuration.partialLinkageConfig

    internal val additionalCacheFlags by lazy { platformManager.loader(target).additionalCacheFlags }

    private fun StringBuilder.appendCommonCacheFlavor() {
        append(target.toString())
        if (debug) append("-g")
        append("STATIC")

        if (propertyLazyInitialization != defaultPropertyLazyInitialization)
            append("-lazy_init${if (propertyLazyInitialization) "ENABLE" else "DISABLE"}")
    }

    private val systemCacheFlavorString = buildString {
        appendCommonCacheFlavor()

        if (!stripDebugInfoFromNativeLibs)
            append("-runtime_debug")
        val allocationMode = configuration.getNotNull(NativeConfigurationKeys.ALLOCATION_MODE)
        if (allocationMode != configuration.defaultAllocationMode)
            append("-allocator${allocationMode.name}")
        val gc = configuration.getNotNull(BinaryOptions.gc)
        if (gc != configuration.defaultGC)
            append("-gc${gc.name}")
        val gcSchedulerType = configuration.getNotNull(BinaryOptions.gcSchedulerType)
        if (gcSchedulerType != configuration.defaultGCSchedulerType)
            append("-gc_scheduler${gcSchedulerType.name}")
        val runtimeAssertsMode = configuration.getNotNull(BinaryOptions.runtimeAssertionsMode)
        if (runtimeAssertsMode != configuration.defaultRuntimeAssertsMode)
            append("-runtime_asserts${runtimeAssertsMode.name}")
        val disableMmap = configuration.getNotNull(BinaryOptions.disableMmap)
        if (disableMmap != configuration.defaultDisableMmap)
            append("-disable_mmap${if (disableMmap) "TRUE" else "FALSE"}")
        val gcMarkSingleThreaded = configuration.getNotNull(BinaryOptions.gcMarkSingleThreaded)
        if (gcMarkSingleThreaded != configuration.defaultGcMarkSingleThreaded)
            append("-gc_mark_single_threaded${if (gcMarkSingleThreaded) "TRUE" else "FALSE"}")
    }

    private val userCacheFlavorString = buildString {
        appendCommonCacheFlavor()
        if (partialLinkageConfig.isEnabled) append("-pl")
    }

    private val systemCacheRootDirectory = File(distribution.konanHome).child("klib").child("cache")
    internal val systemCacheDirectory = systemCacheRootDirectory.child(systemCacheFlavorString).also { it.mkdirs() }
    private val autoCacheRootDirectory = configuration.get(NativeConfigurationKeys.AUTO_CACHE_DIR)?.let {
        File(it).apply {
            if (!isDirectory) configuration.reportCompilationError("auto cache directory $this is not found or is not a directory")
        }
    } ?: systemCacheRootDirectory
    internal val autoCacheDirectory = autoCacheRootDirectory.child(userCacheFlavorString).also { it.mkdirs() }
    private val incrementalCacheRootDirectory = configuration.get(NativeConfigurationKeys.INCREMENTAL_CACHE_DIR)?.let {
        File(it).apply {
            if (!isDirectory) configuration.reportCompilationError("incremental cache directory $this is not found or is not a directory")
        }
    }
    internal val incrementalCacheDirectory = incrementalCacheRootDirectory?.child(userCacheFlavorString)?.also { it.mkdirs() }

    internal val ignoreCacheReason = when {
        optimizationsEnabled -> "for optimized compilation"
        configuration.get(BinaryOptions.sanitizer) != null -> "with sanitizers enabled"
        configuration.getNotNull(NativeConfigurationKeys.RUNTIME_LOGS) != configuration.defaultRuntimeLogs -> "with runtime logs"
        configuration.getNotNull(BinaryOptions.checkStateAtExternalCalls) -> "with external calls state checker"
        else -> null
    }

    internal val cacheSupport = CacheSupport(
            configuration = configuration,
            resolvedLibraries = resolvedLibraries,
            ignoreCacheReason = ignoreCacheReason,
            systemCacheDirectory = systemCacheDirectory,
            autoCacheDirectory = autoCacheDirectory,
            incrementalCacheDirectory = incrementalCacheDirectory,
            target = target,
            produce = produce
    )

    internal val cachedLibraries: CachedLibraries
        get() = cacheSupport.cachedLibraries

    internal val libraryToCache: PartialCacheInfo?
        get() = cacheSupport.libraryToCache

    internal val producePerFileCache
        get() = configuration.get(NativeConfigurationKeys.MAKE_PER_FILE_CACHE) == true

    val outputPath get() = configuration.get(NativeConfigurationKeys.OUTPUT)?.removeSuffixIfPresent(produce.suffix(target)) ?: produce.visibleName

    private val implicitModuleName: String
        get() = cacheSupport.libraryToCache?.let {
            if (producePerFileCache)
                CachedLibraries.getPerFileCachedLibraryName(it.klib)
            else
                CachedLibraries.getCachedLibraryName(it.klib)
        }
                ?: File(outputPath).name

    /**
     * Continue from bitcode. Skips the frontend and codegen phase of the compiler
     * and instead reads the provided bitcode file.
     * This option can be used for continuing the compilation from a previous invocation.
     */
    internal val compileFromBitcode: String? by lazy {
        configuration.get(NativeConfigurationKeys.COMPILE_FROM_BITCODE)
    }

    /**
     * Path to serialized dependencies to use for bitcode compilation.
     */
    internal val readSerializedDependencies: String? by lazy {
        configuration.get(NativeConfigurationKeys.SERIALIZED_DEPENDENCIES)
    }

    /**
     * Path to store backend dependency information.
     */
    internal val writeSerializedDependencies: String? by lazy {
        configuration.get(NativeConfigurationKeys.SAVE_DEPENDENCIES_PATH)
    }

    val infoArgsOnly = (configuration.kotlinSourceRoots.isEmpty()
            && configuration[NativeConfigurationKeys.INCLUDED_LIBRARIES].isNullOrEmpty()
            && configuration[NativeConfigurationKeys.EXPORTED_LIBRARIES].isNullOrEmpty()
            && libraryToCache == null && compileFromBitcode.isNullOrEmpty())


    /**
     * Directory to store LLVM IR from -Xsave-llvm-ir-after.
     */
    internal val saveLlvmIrDirectory: java.io.File by lazy {
        val path = configuration.get(NativeConfigurationKeys.SAVE_LLVM_IR_DIRECTORY)
        if (path == null) {
            val tempDir = Files.createTempDirectory(Paths.get(StandardSystemProperty.JAVA_IO_TMPDIR.value()!!), /* prefix= */ null).toFile()
            configuration.report(CompilerMessageSeverity.WARNING,
                    "Temporary directory for LLVM IR is ${tempDir.canonicalPath}")
            tempDir
        } else {
            java.io.File(path)
        }
    }

}