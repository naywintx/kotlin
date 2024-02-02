/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.config.native

import org.jetbrains.kotlin.config.CompilerConfiguration

object BitcodeEmbedding {

    enum class Mode {
        NONE, MARKER, FULL
    }

    fun getLinkerOptions(config: CompilerConfiguration): List<String> = when (config.bitcodeEmbeddingMode) {
        Mode.NONE -> emptyList()
        Mode.MARKER -> listOf("-bitcode_bundle", "-bitcode_process_mode", "marker")
        Mode.FULL -> listOf("-bitcode_bundle")
    }

    fun getClangOptions(config: CompilerConfiguration): List<String> = when (config.bitcodeEmbeddingMode) {
        Mode.NONE -> listOf("-fembed-bitcode=off")
        Mode.MARKER -> listOf("-fembed-bitcode=marker")
        Mode.FULL -> listOf("-fembed-bitcode=all")
    }

    private val CompilerConfiguration.bitcodeEmbeddingMode get() = get(NativeConfigurationKeys.BITCODE_EMBEDDING_MODE)!!
}