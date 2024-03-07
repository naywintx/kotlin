/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
@file:OptIn(InternalExtensionsApi::class) // inheritance of deprecated visitors will be removed with visitors

package kotlin.metadata.jvm.internal

import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import kotlin.metadata.*
import kotlin.metadata.internal.extensions.*
import kotlin.metadata.jvm.*

internal val KmClass.jvm: JvmClassExtension
    get() = getExtension(JvmClassExtension.TYPE) as JvmClassExtension

internal val KmPackage.jvm: JvmPackageExtension
    get() = getExtension(JvmPackageExtension.TYPE) as JvmPackageExtension

internal val KmFunction.jvm: JvmFunctionExtension
    get() = visitExtensions(JvmFunctionExtension.TYPE) as JvmFunctionExtension

internal val KmProperty.jvm: JvmPropertyExtension
    get() = getExtension(JvmPropertyExtension.TYPE) as JvmPropertyExtension

internal val KmConstructor.jvm: JvmConstructorExtension
    get() = visitExtensions(JvmConstructorExtension.TYPE) as JvmConstructorExtension

internal val KmTypeParameter.jvm: JvmTypeParameterExtension
    get() = visitExtensions(JvmTypeParameterExtension.TYPE) as JvmTypeParameterExtension

internal val KmType.jvm: JvmTypeExtension
    get() = visitExtensions(JvmTypeExtension.TYPE) as JvmTypeExtension


internal class JvmClassExtension : KmClassExtension {
    val localDelegatedProperties: MutableList<KmProperty> = ArrayList(0)
    var moduleName: String? = null
    var anonymousObjectOriginName: String? = null
    var jvmFlags: Int = 0

    override val type: KmExtensionType
        get() = TYPE

    companion object {
        val TYPE: KmExtensionType = KmExtensionType(JvmClassExtension::class)
    }
}

internal class JvmPackageExtension : KmPackageExtension {
    val localDelegatedProperties: MutableList<KmProperty> = ArrayList(0)
    var moduleName: String? = null

    override val type: KmExtensionType
        get() = TYPE

    companion object {
        /**
         * The type of this extension visitor.
         *
         * @see KmExtensionType
         */
        @JvmField
        val TYPE: KmExtensionType = KmExtensionType(JvmPackageExtension::class)
    }
}

internal class JvmFunctionExtension : KmFunctionExtension {
    var signature: JvmMethodSignature? = null
    var lambdaClassOriginName: String? = null

    override val type: KmExtensionType
        get() = TYPE

    companion object {
        /**
         * The type of this extension visitor.
         *
         * @see KmExtensionType
         */
        @JvmField
        val TYPE: KmExtensionType = KmExtensionType(JvmFunctionExtension::class)
    }
}

internal class JvmPropertyExtension : KmPropertyExtension {
    var jvmFlags: Int = 0
    var fieldSignature: JvmFieldSignature? = null
    var getterSignature: JvmMethodSignature? = null
    var setterSignature: JvmMethodSignature? = null
    var syntheticMethodForAnnotations: JvmMethodSignature? = null
    var syntheticMethodForDelegate: JvmMethodSignature? = null

    override val type: KmExtensionType
        get() = TYPE

    companion object {
        /**
         * The type of this extension visitor.
         *
         * @see KmExtensionType
         */
        @JvmField
        val TYPE: KmExtensionType = KmExtensionType(JvmPropertyExtension::class)
    }
}

internal class JvmConstructorExtension : KmConstructorExtension {
    var signature: JvmMethodSignature? = null

    override val type: KmExtensionType
        get() = TYPE

    companion object {
        /**
         * The type of this extension visitor.
         *
         * @see KmExtensionType
         */
        @JvmField
        val TYPE: KmExtensionType = KmExtensionType(JvmConstructorExtension::class)
    }
}

internal class JvmTypeParameterExtension : KmTypeParameterExtension {
    val annotations: MutableList<KmAnnotation> = mutableListOf()

    override val type: KmExtensionType
        get() = TYPE

    companion object {
        /**
         * The type of this extension visitor.
         *
         * @see KmExtensionType
         */
        @JvmField
        val TYPE: KmExtensionType = KmExtensionType(JvmTypeParameterExtension::class)
    }
}

internal class JvmTypeExtension : KmTypeExtension {
    var isRaw: Boolean = false
    val annotations: MutableList<KmAnnotation> = mutableListOf()

    override val type: KmExtensionType
        get() = TYPE

    companion object {
        /**
         * The type of this extension visitor.
         *
         * @see KmExtensionType
         */
        @JvmField
        val TYPE: KmExtensionType = KmExtensionType(JvmTypeExtension::class)

        /**
         * The type flexibility id, signifying that the visited type is a JVM platform type.
         *
         * @see KmTypeVisitor.visitFlexibleTypeUpperBound
         */
        const val PLATFORM_TYPE_ID: String = JvmProtoBufUtil.PLATFORM_TYPE_ID // TODO: move out of deprecated visitor
    }
}
