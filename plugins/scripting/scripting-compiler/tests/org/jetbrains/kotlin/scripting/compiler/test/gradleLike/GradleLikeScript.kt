/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.scripting.compiler.test.gradleLike

import org.jetbrains.kotlin.scripting.compiler.test.gradleLike.dsl.CompiledKotlinBuildScript
import org.jetbrains.kotlin.scripting.compiler.test.gradleLike.dsl.Project
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm

object GradleLikeScriptCompilationConfiguration : ScriptCompilationConfiguration(
    {
        defaultImports("org.jetbrains.kotlin.scripting.compiler.test.gradleLike.dsl.*")
        jvm {
            dependenciesFromCurrentContext(wholeClasspath = true)
        }
        baseClass(CompiledKotlinBuildScript::class)
        implicitReceivers(Project::class)
    }
)

object GradleLikeScriptEvaluationConfiguration : ScriptEvaluationConfiguration(
    {
        implicitReceivers(Project())
    }
)
