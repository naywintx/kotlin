/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package kotlin.metadata.internal.extensions

import kotlin.metadata.*
import kotlin.reflect.KClass

/**
 * Marks functions and classes that are part of the internal mechanism to work with platform-specific metadata.
 *
 * Such API is used exclusively by platform-specific counterparts (like kotlin-metadata-jvm and kotlinx-metadata-klib) of the kotlin-metadata library.
 * It is not intended to be publicly used, nor are there any use cases for that.
 *
 * Platform-specific data is presented to users as corresponding extensions on Km nodes, like `KmProperty.getterSignature`, and there is no
 * publicly available mechanism to retrieve them another way.
 */
@RequiresOptIn(
    "This is an internal kotlin-metadata API for working with platform-specific metadata. There are no situations for it to be explicitly used.",
    level = RequiresOptIn.Level.ERROR
)
public annotation class InternalExtensionsApi

/**
 * A type of the extension expected by the code that uses the extensions API.
 *
 * Each declaration that can have platform-specific extensions in the metadata has a method `getExtension`, e.g.:
 * `fun KmFunction.getExtension(type: KmExtensionType): KmFunctionExtension`.
 *
 * These functions are used by -jvm and -klib counterparts to retrieve platform-specific metadata
 * and should not be used in any other way.
 */
@InternalExtensionsApi
public class KmExtensionType(private val klass: KClass<out KmExtension>) {
    override fun equals(other: Any?): Boolean =
        other is KmExtensionType && klass == other.klass

    override fun hashCode(): Int =
        klass.hashCode()

    override fun toString(): String =
        klass.java.name
}

/**
 * Base interface for all extensions to hold the extension type.
 */
@InternalExtensionsApi
public interface KmExtension {

    /**
     * Type of this extension.
     */
    public val type: KmExtensionType
}

@InternalExtensionsApi
public interface KmClassExtension : KmExtension

@InternalExtensionsApi
public interface KmPackageExtension : KmExtension

@InternalExtensionsApi
public interface KmModuleFragmentExtension : KmExtension

@InternalExtensionsApi
public interface KmFunctionExtension : KmExtension

@InternalExtensionsApi
public interface KmPropertyExtension : KmExtension

@InternalExtensionsApi
public interface KmConstructorExtension : KmExtension

@InternalExtensionsApi
public interface KmTypeParameterExtension : KmExtension

@InternalExtensionsApi
public interface KmTypeExtension : KmExtension

@InternalExtensionsApi
public interface KmTypeAliasExtension : KmExtension

@InternalExtensionsApi
public interface KmValueParameterExtension : KmExtension
