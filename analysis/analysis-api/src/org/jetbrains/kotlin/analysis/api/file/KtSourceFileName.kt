/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.file

import org.jetbrains.kotlin.analysis.api.components.KtKlibSourceFileProviderMixIn

/**
 * This [KtSourceFileName] carries limited information about the [name] of a Kotlin Source File.
 * The name of a given source file can be encoded into binaries (such as .klib files) and might be relevant for
 * tools processing `klib`s (such as swift- and objc export).
 *
 * @see [KtKlibSourceFileProviderMixIn.getKlibSourceFileName]
 */
public class KtSourceFileName(
    public val name: String,
) : Comparable<KtSourceFileName> {

    override fun compareTo(other: KtSourceFileName): Int {
        return name.compareTo(other.name)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KtSourceFileName) return false
        if (name != other.name) return false
        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun toString(): String {
        return name
    }
}
