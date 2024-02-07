/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.providers

interface FirSourcesBasedProvider {
    /**
     * Transforms sources to remove unwanted requirements on transitive dependencies.
     * For example, it can mean expanding all typealiases in declaration signatures.
     */
    fun prepareSourcesForUseAsDependencies()
}