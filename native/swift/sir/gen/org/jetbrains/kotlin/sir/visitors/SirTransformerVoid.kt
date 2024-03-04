/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// This file was generated automatically. See native/swift/sir/tree-generator/Readme.md.
// DO NOT MODIFY IT MANUALLY.

package org.jetbrains.kotlin.sir.visitors

import org.jetbrains.kotlin.sir.*

/**
 * Auto-generated by [org.jetbrains.kotlin.sir.tree.generator.printer.TransformerVoidPrinter]
 */
abstract class SirTransformerVoid : SirTransformer<Nothing?>() {

    abstract fun <E : SirElement> transformElement(element: E): E

    final override fun <E : SirElement> transformElement(element: E, data: Nothing?): E =
        transformElement(element)

    open fun transformModule(module: SirModule): SirModule =
        transformElement(module)

    final override fun transformModule(module: SirModule, data: Nothing?): SirModule =
        transformModule(module)

    open fun transformDeclarationContainer(declarationContainer: SirDeclarationContainer): SirDeclarationContainer =
        transformElement(declarationContainer)

    final override fun transformDeclarationContainer(declarationContainer: SirDeclarationContainer, data: Nothing?): SirDeclarationContainer =
        transformDeclarationContainer(declarationContainer)

    open fun transformDeclaration(declaration: SirDeclaration): SirDeclaration =
        transformElement(declaration)

    final override fun transformDeclaration(declaration: SirDeclaration, data: Nothing?): SirDeclaration =
        transformDeclaration(declaration)

    open fun transformNamedDeclaration(declaration: SirNamedDeclaration): SirDeclaration =
        transformDeclaration(declaration)

    final override fun transformNamedDeclaration(declaration: SirNamedDeclaration, data: Nothing?): SirDeclaration =
        transformNamedDeclaration(declaration)

    open fun transformEnum(enum: SirEnum): SirDeclaration =
        transformNamedDeclaration(enum)

    final override fun transformEnum(enum: SirEnum, data: Nothing?): SirDeclaration =
        transformEnum(enum)

    open fun transformStruct(struct: SirStruct): SirDeclaration =
        transformNamedDeclaration(struct)

    final override fun transformStruct(struct: SirStruct, data: Nothing?): SirDeclaration =
        transformStruct(struct)

    open fun transformClass(klass: SirClass): SirDeclaration =
        transformNamedDeclaration(klass)

    final override fun transformClass(klass: SirClass, data: Nothing?): SirDeclaration =
        transformClass(klass)

    open fun transformCallable(callable: SirCallable): SirDeclaration =
        transformDeclaration(callable)

    final override fun transformCallable(callable: SirCallable, data: Nothing?): SirDeclaration =
        transformCallable(callable)

    open fun transformConstructor(constructor: SirConstructor): SirDeclaration =
        transformCallable(constructor)

    final override fun transformConstructor(constructor: SirConstructor, data: Nothing?): SirDeclaration =
        transformConstructor(constructor)

    open fun transformFunction(function: SirFunction): SirDeclaration =
        transformCallable(function)

    final override fun transformFunction(function: SirFunction, data: Nothing?): SirDeclaration =
        transformFunction(function)

    open fun transformAccessor(accessor: SirAccessor): SirDeclaration =
        transformCallable(accessor)

    final override fun transformAccessor(accessor: SirAccessor, data: Nothing?): SirDeclaration =
        transformAccessor(accessor)

    open fun transformGetter(getter: SirGetter): SirDeclaration =
        transformAccessor(getter)

    final override fun transformGetter(getter: SirGetter, data: Nothing?): SirDeclaration =
        transformGetter(getter)

    open fun transformSetter(setter: SirSetter): SirDeclaration =
        transformAccessor(setter)

    final override fun transformSetter(setter: SirSetter, data: Nothing?): SirDeclaration =
        transformSetter(setter)

    open fun transformVariable(variable: SirVariable): SirDeclaration =
        transformDeclaration(variable)

    final override fun transformVariable(variable: SirVariable, data: Nothing?): SirDeclaration =
        transformVariable(variable)

    open fun transformImport(import: SirImport): SirDeclaration =
        transformDeclaration(import)

    final override fun transformImport(import: SirImport, data: Nothing?): SirDeclaration =
        transformImport(import)
}
