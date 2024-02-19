/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir

import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeAliasSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId

abstract class ActualDeclarationsCollector {
    abstract fun collect(): ClassActualizationInfo
}

data class ClassActualizationInfo(
    // mapping from classId of actual class/typealias to itself/typealias expansion
    val actualClasses: Map<ClassId, IrClassSymbol>,
    // mapping from classId to actual typealias
    val actualTypeAliases: Map<ClassId, IrTypeAliasSymbol>,
    val actualTopLevels: Map<CallableId, List<IrSymbol>>,
    val actualSymbolsToFile: Map<IrSymbol, IrFile?>,
) {
    fun getActualWithoutExpansion(classId: ClassId): IrSymbol? {
        return actualTypeAliases[classId] ?: actualClasses[classId]
    }
}