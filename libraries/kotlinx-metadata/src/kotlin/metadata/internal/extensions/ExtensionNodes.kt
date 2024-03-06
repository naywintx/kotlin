/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package kotlin.metadata.internal.extensions

import kotlin.metadata.*
import kotlin.reflect.KClass

// TODO: rewrite docs for extensions
@RequiresOptIn("Internal!", level = RequiresOptIn.Level.ERROR)
public annotation class InternalExtensionsApi

/**
 * A type of the extension visitor expected by the code that uses the visitor API.
 *
 * Each declaration which can have platform-specific extensions in the metadata has a method `getExtension`, e.g.:
 *
 *     fun KmFunction.getExtension(type: KmExtensionType): KmFunctionExtension
 *
 * The client code is supposed to return the extension visitor corresponding to the given type, or to return `null` if the type is
 * of no interest to that code. Each platform-specific extension visitor has a [KmExtensionType] instance declared in the `TYPE` property
 * its companion object. For example, to load JVM extensions on a function, one could do:
 * ```
 *     override fun visitExtensions(type: KmExtensionType): KmFunctionExtensionVisitor? {
 *         if (type != JvmFunctionExtensionVisitor.TYPE) return null
 *
 *         return object : JvmFunctionExtensionVisitor() {
 *             ...
 *         }
 *     }
 * ```
 * In case an extension visitor of an unrelated type is returned, the code using the visitor API must ignore that visitor.
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

@InternalExtensionsApi
public interface KmExtension {

    /**
     * Type of this extension visitor.
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
