/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.bir.backend

import org.jetbrains.kotlin.bir.*
import org.jetbrains.kotlin.bir.declarations.*
import org.jetbrains.kotlin.bir.expressions.BirGetValue

context(BirBackendContext)
abstract class BirLoweringPhase : BirPhase {
    abstract fun lower(module: BirModuleFragment)

    /**
     * Gets all elements matching a specified index key, either from the compiled module and/or
     * other modules, as specified in [registerIndexKey].
     *
     * @see BirDatabase.getElementsWithIndex
     */
    protected fun <E : BirElement> getAllElementsOfClass(elementType: BirElementType<E>, includeExternalModules: Boolean): Sequence<E> {
        var elements: Sequence<E> = compiledBir.getElementsWithIndex(elementType)
        if (includeExternalModules) {
            elements += externalModulesBir.getElementsWithIndex(elementType)
        }
        return elements
    }


    protected fun <E : BirElement, T> acquireTemporaryProperty(elementType: BirElementType<E>): BirDynamicPropertyAccessToken<E, T> {
        return TemporaryBirDynamicProperty<E, T>(elementType, dynamicPropertyManager, this)
    }


    protected fun <T> lz(initializer: () -> T) = lazy(LazyThreadSafetyMode.NONE, initializer)
}