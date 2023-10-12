/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// This file was generated automatically. See compiler/ir/ir.tree/tree-generator/ReadMe.md.
// DO NOT MODIFY IT MANUALLY.

package org.jetbrains.kotlin.bir.declarations

import org.jetbrains.kotlin.bir.BirElementVisitor
import org.jetbrains.kotlin.bir.accept
import org.jetbrains.kotlin.bir.symbols.BirFileSymbol
import org.jetbrains.kotlin.ir.IrFileEntry

/**
 * A non-leaf IR tree element.
 *
 * Generated from: [org.jetbrains.kotlin.bir.generator.BirTree.file]
 */
abstract class BirFile : BirPackageFragment(), BirAnnotationContainerElement,
        BirMetadataSourceOwner {
    abstract override val symbol: BirFileSymbol

    abstract var module: BirModuleFragment

    abstract var fileEntry: IrFileEntry

    override fun <D> acceptChildren(visitor: BirElementVisitor<D>, data: D) {
        declarations.forEach { it.accept(data, visitor) }
    }
}
