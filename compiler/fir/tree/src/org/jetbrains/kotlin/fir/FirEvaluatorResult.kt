/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir

sealed class FirEvaluatorResult
class Evaluated(val result: FirElement) : FirEvaluatorResult()
object NotEvaluated : FirEvaluatorResult()
object DuringEvaluation : FirEvaluatorResult()
abstract class CompileTimeException : FirEvaluatorResult()
object DivisionByZero : CompileTimeException()
object StackOverflow : CompileTimeException()
