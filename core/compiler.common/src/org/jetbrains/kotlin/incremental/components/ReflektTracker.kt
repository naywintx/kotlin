/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.incremental.components

import org.jetbrains.kotlin.container.DefaultImplementation
import java.io.File

@DefaultImplementation(ReflektTracker.DoNothing::class)
interface ReflektTracker {
    fun report(fileSearchedByReflect: File, reflektUsageFile: File)

    object DoNothing : ReflektTracker {
        override fun report(fileSearchedByReflect: File, reflektUsageFile: File) {
        }
    }
}