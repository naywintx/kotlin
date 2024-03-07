/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.test.blackbox.support.util

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.konan.test.blackbox.AbstractNativeSimpleTest
import org.jetbrains.kotlin.konan.test.blackbox.support.LoggedData
import org.jetbrains.kotlin.konan.test.blackbox.support.compilation.TestCompilationArtifact
import org.jetbrains.kotlin.konan.test.blackbox.support.compilation.TestCompilationResult
import org.jetbrains.kotlin.konan.test.blackbox.support.settings.configurables
import java.io.File
import java.io.FileInputStream
import kotlin.time.measureTimedValue

internal fun AbstractNativeSimpleTest.lipoCreate(
    inputFiles: List<File>,
    outputFile: File,
): TestCompilationResult<out TestCompilationArtifact.BinaryLibrary> {
    val processBuilder = ProcessBuilder(
        "${testRunSettings.configurables.absoluteTargetToolchain}/bin/lipo",
        "-create",
        *inputFiles.map { it.canonicalPath }.toTypedArray(),
        "-output",
        outputFile.canonicalPath,
    )
    val process = processBuilder.start()
    val (exitCode, duration) = measureTimedValue {
        process.waitFor()
    }
    val stderr = process.errorStream.readBytes()
    val stdout = process.inputStream.readBytes()

    val parameters = CommandParameters(
        commandName = "LIPO",
        command = processBuilder.command()
    )

    fun loggedData(output: String = "") = LoggedData.CompilationToolCall(
        toolName = "LIPO",
        parameters = parameters,
        exitCode = if (exitCode == 0) ExitCode.OK else ExitCode.COMPILATION_ERROR,
        toolOutput = stdout.decodeToString() + stderr.decodeToString() + output,
        toolOutputHasErrors = stderr.isNotEmpty(),
        duration = duration,
        input = null,
    )

    if (exitCode != 0) {
        return TestCompilationResult.CompilationToolFailure(
            loggedData()
        )
    }
    if (!outputFile.exists()) {
        return TestCompilationResult.CompilationToolFailure(
            loggedData(
                "\nMissing output library"
            )
        )
    }

    val universalMagic = listOf(0xca, 0xfe, 0xba, 0xbe)
    val actualMagic = FileInputStream(outputFile).use { stream -> (0..<universalMagic.count()).map { stream.read() } }
    if (actualMagic != universalMagic) {
        return TestCompilationResult.CompilationToolFailure(
            loggedData(
                "\nLipo output was not a universal image"
            )
        )
    }

    return TestCompilationResult.Success(
        TestCompilationArtifact.BinaryLibrary(outputFile),
        loggedData(),
    )
}