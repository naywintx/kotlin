/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.scripting.compiler.test

import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.ScriptJvmCompilerIsolated
import org.jetbrains.kotlin.scripting.compiler.test.gradleLike.GradleLikeScriptCompilationConfiguration
import org.jetbrains.kotlin.scripting.compiler.test.gradleLike.GradleLikeScriptEvaluationConfiguration
import org.junit.Assert
import org.junit.Test
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.BasicJvmScriptEvaluator
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

class GradleLikeScriptTest : TestCase() {

    @Test
    fun test1() {
        val res = checkCompile(
            """
            val projectApi1 = projectApi1 { it }            
            """.trimIndent()
        )
        assertSuccess(res)
    }

    private fun checkCompile(script: String): ResultWithDiagnostics<CompiledScript> = checkCompile(script.toScriptSource())

    private fun checkCompile(script: SourceCode): ResultWithDiagnostics<CompiledScript> {
        val compilationConfiguration = GradleLikeScriptCompilationConfiguration
        val compiler = ScriptJvmCompilerIsolated(defaultJvmScriptingHostConfiguration)
        return compiler.compile(script, compilationConfiguration)
    }

    private fun checkEvaluate(script: String): ResultWithDiagnostics<EvaluationResult> = checkEvaluate(script.toScriptSource())

    private fun checkEvaluate(script: SourceCode): ResultWithDiagnostics<EvaluationResult> =
        checkCompile(script).onSuccess { compiled ->
            val evaluationConfiguration = GradleLikeScriptEvaluationConfiguration
            val evaluator = BasicJvmScriptEvaluator()
            val res = runBlocking {
                evaluator.invoke(compiled, evaluationConfiguration)
            }
            res
        }
}

fun assertSuccess(res: ResultWithDiagnostics<*>) {
    if (res !is ResultWithDiagnostics.Success<*>) {
        Assert.fail(
            "test failed:\n  ${res.reports.joinToString("\n  ") { it.message + if (it.exception == null) "" else ": ${it.exception}" }}"
        )
    }
}

