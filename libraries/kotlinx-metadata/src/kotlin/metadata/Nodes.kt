/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package kotlin.metadata

import kotlin.metadata.internal.FlagImpl
import kotlin.metadata.internal.extensions.*
import kotlin.metadata.internal.propertyBooleanFlag
import org.jetbrains.kotlin.metadata.deserialization.Flags
import kotlin.contracts.ExperimentalContracts
import kotlin.reflect.KClass

/**
 * Represents a Kotlin declaration container, such as a class or a package fragment.
 */
public interface KmDeclarationContainer {
    /**
     * Functions in the container.
     */
    public val functions: MutableList<KmFunction>

    /**
     * Properties in the container.
     */
    public val properties: MutableList<KmProperty>

    /**
     * Type aliases in the container.
     */
    public val typeAliases: MutableList<KmTypeAlias>
}

/**
 * Represents a Kotlin class.
 *
 * 'Class' here is used in a broad sense and includes interfaces, enum classes, companion objects, et cetera.
 * Precise kind of the class can be obtained via [KmClass.kind].
 * Various class attributes can be read and manipulated via extension properties, such as [KmClass.visibility] or [KmClass.isData].
 */
public class KmClass : KmDeclarationContainer {
    internal var flags: Int = 0

    /**
     * Name of the class.
     */
    public lateinit var name: ClassName

    /**
     * Type parameters of the class.
     */
    public val typeParameters: MutableList<KmTypeParameter> = ArrayList(0)

    /**
     * Supertypes of the class.
     */
    public val supertypes: MutableList<KmType> = ArrayList(1)

    /**
     * Functions in the class.
     */
    override val functions: MutableList<KmFunction> = ArrayList()

    /**
     * Properties in the class.
     */
    override val properties: MutableList<KmProperty> = ArrayList()

    /**
     * Type aliases in the class.
     */
    override val typeAliases: MutableList<KmTypeAlias> = ArrayList(0)

    /**
     * Constructors of the class.
     */
    public val constructors: MutableList<KmConstructor> = ArrayList(1)

    /**
     * Name of the companion object of this class, if it has one.
     */
    public var companionObject: String? = null

    /**
     * Names of nested classes of this class.
     */
    public val nestedClasses: MutableList<String> = ArrayList(0)

    /**
     * Names of enum entries, if this class is an enum class.
     */
    public val enumEntries: MutableList<String> = ArrayList(0)

    /**
     * Names of direct subclasses of this class, if this class is `sealed`.
     */
    public val sealedSubclasses: MutableList<ClassName> = ArrayList(0)

    /**
     * Name of the underlying property, if this class is `inline`.
     */
    public var inlineClassUnderlyingPropertyName: String? = null

    /**
     * Type of the underlying property, if this class is `inline`.
     */
    public var inlineClassUnderlyingType: KmType? = null

    /**
     * Types of context receivers of the class.
     */
    @ExperimentalContextReceivers
    public val contextReceiverTypes: MutableList<KmType> = ArrayList(0)

    /**
     * Version requirements on this class.
     */
    public val versionRequirements: MutableList<KmVersionRequirement> = ArrayList(0)

    @InternalExtensionsApi
    internal val extensions: List<KmClassExtension> =
        MetadataExtensions.INSTANCES.map(MetadataExtensions::createClassExtension)

    @InternalExtensionsApi
    public fun getExtension(type: KmExtensionType): KmClassExtension =
        extensions.singleOfType(type)
}

/**
 * Represents a Kotlin package fragment that contains top-level functions, properties and type aliases.
 * Package fragments are produced from single file facades and multi-file class parts.
 * Note that a package fragment does not contain any classes, as classes are not a part of file facades and have their own metadata.
 */
public class KmPackage : KmDeclarationContainer {
    /**
     * Functions in the package fragment.
     */
    override val functions: MutableList<KmFunction> = ArrayList()

    /**
     * Properties in the package fragment.
     */
    override val properties: MutableList<KmProperty> = ArrayList()

    /**
     * Type aliases in the package fragment.
     */
    override val typeAliases: MutableList<KmTypeAlias> = ArrayList(0)

    @InternalExtensionsApi
    internal val extensions: List<KmPackageExtension> =
        MetadataExtensions.INSTANCES.map(MetadataExtensions::createPackageExtension)

    @InternalExtensionsApi
    public fun getExtension(type: KmExtensionType): KmPackageExtension =
        extensions.singleOfType(type)
}

/**
 * Represents a synthetic class generated for a Kotlin lambda.
 */
public class KmLambda {
    /**
     * Signature of the synthetic anonymous function, representing the lambda.
     */
    public lateinit var function: KmFunction
}

/**
 * Represents a constructor of a Kotlin class.
 *
 * Various constructor attributes can be read and manipulated via extension properties,
 * such as [KmConstructor.visibility] or [KmConstructor.isSecondary].
 */
public class KmConstructor internal constructor(internal var flags: Int) {
    public constructor() : this(0)

    /**
     * Value parameters of the constructor.
     */
    public val valueParameters: MutableList<KmValueParameter> = ArrayList()

    /**
     * Version requirements on the constructor.
     */
    public val versionRequirements: MutableList<KmVersionRequirement> = ArrayList(0)

    @InternalExtensionsApi
    internal val extensions: List<KmConstructorExtension> =
        MetadataExtensions.INSTANCES.map(MetadataExtensions::createConstructorExtension)

    @InternalExtensionsApi
    public fun visitExtensions(type: KmExtensionType): KmConstructorExtension =
        extensions.singleOfType(type)
}

/**
 * Represents a Kotlin function declaration.
 *
 * Various function attributes can be read and manipulated via extension properties,
 * such as [KmFunction.visibility] or [KmFunction.isSuspend].
 *
 * @property name the name of the function
 */
public class KmFunction internal constructor(internal var flags: Int, public var name: String) {

    public constructor(name: String) : this(0, name)

    /**
     * Type parameters of the function.
     */
    public val typeParameters: MutableList<KmTypeParameter> = ArrayList(0)

    /**
     * Type of the receiver of the function, if this is an extension function.
     */
    public var receiverParameterType: KmType? = null

    /**
     * Types of context receivers of the function.
     */
    @ExperimentalContextReceivers
    public val contextReceiverTypes: MutableList<KmType> = ArrayList(0)

    /**
     * Value parameters of the function.
     */
    public val valueParameters: MutableList<KmValueParameter> = ArrayList()

    /**
     * Return type of the function.
     */
    public lateinit var returnType: KmType

    /**
     * Version requirements on the function.
     */
    public val versionRequirements: MutableList<KmVersionRequirement> = ArrayList(0)

    /**
     * Contract of the function.
     */
    @ExperimentalContracts
    public var contract: KmContract? = null

    @InternalExtensionsApi
    internal val extensions: List<KmFunctionExtension> =
        MetadataExtensions.INSTANCES.map(MetadataExtensions::createFunctionExtension)

    @InternalExtensionsApi
    public fun visitExtensions(type: KmExtensionType): KmFunctionExtension =
        extensions.singleOfType(type)
}

/**
 * Represents a Kotlin property accessor.
 *
 * Does not contain meaningful information except attributes, such as visibility and modality.
 * Attributes can be read and written using extension properties, e.g. [KmPropertyAccessorAttributes.visibility] or [KmPropertyAccessorAttributes.isNotDefault].
 */
public class KmPropertyAccessorAttributes internal constructor(internal var flags: Int) {
    public constructor() : this(0)
}

/**
 * Represents a Kotlin property declaration.
 *
 * Various property attributes can be read and manipulated via extension properties,
 * such as [KmProperty.visibility] or [KmProperty.isVar].
 *
 * Getter and setter attributes are available separately via extensions on [KmProperty.getter] and [KmProperty.setter] correspondingly.
 *
 * @property name the name of the property
 */
public class KmProperty internal constructor(
    internal var flags: Int,
    public var name: String,
    getterFlags: Int,
    setterFlags: Int,
) {
    public constructor(name: String) : this(0, name, 0, 0)

    // needed for reading/writing flags back to protobuf as a whole pack
    private var _hasSetter: Boolean by propertyBooleanFlag(FlagImpl(Flags.HAS_SETTER))
    private var _hasGetter: Boolean by propertyBooleanFlag(FlagImpl(Flags.HAS_GETTER))

    /**
     * Attributes of the getter of this property.
     * Attributes can be retrieved with extension properties, such as [KmPropertyAccessorAttributes.visibility] or [KmPropertyAccessorAttributes.isNotDefault].
     *
     * Getter for property is always present, hence return type of this function is non-nullable.
     */
    public val getter: KmPropertyAccessorAttributes = KmPropertyAccessorAttributes(getterFlags).also { _hasGetter = true }

    /**
     * Attributes of the setter of this property.
     * Attributes can be retrieved with extension properties, such as [KmPropertyAccessorAttributes.visibility] or [KmPropertyAccessorAttributes.isNotDefault].
     *
     * Returns null if setter is absent, i.e. [KmProperty.isVar] is false.
     *
     * Note that setting [KmProperty.isVar] to true does not automatically create [KmProperty.setter] and vice versa. This has to be done explicitly.
     */
    public var setter: KmPropertyAccessorAttributes? = if (this._hasSetter) KmPropertyAccessorAttributes(setterFlags) else null
        set(new) {
            this._hasSetter = new != null
            field = new
        }

    /**
     * A legacy accessor to setter attributes.
     *
     * Property accessor flags, consisting of [Flag.HAS_ANNOTATIONS], visibility flag, modality flag
     * and [Flag.PropertyAccessor] flags.
     *
     * Note that, for compatibility reasons, flags are present even the property is `val` and `setter` is null.
     * In that case, flags for hasAnnotation, visibility and modality are copied from properties' flag, which may lead
     * to incorrect results. For example, when property is annotated, [setterFlags] will also return true for [Flag.Common.HAS_ANNOTATIONS],
     * even though there is no setter nor annotations on it.
     *
     * Setting this property when setter is absent changes the value, but does not create new [setter].
     * This behavior is for compatibility only and will be removed in future versions.
     */
    // TODO delet zis
    @Deprecated("$flagAccessPrefix KmProperty.setter, such as KmProperty.setter.isNotDefault", level = DeprecationLevel.ERROR)
    public var setterFlags: Int = setterFlags // It's either the correct flags from deserializer, or always 0 in the case of hand-created property
        get() = setter?.flags ?: field
        set(value) {
            setter?.flags = value
            field = value
        }

    /**
     * Type parameters of the property.
     */
    public val typeParameters: MutableList<KmTypeParameter> = ArrayList(0)

    /**
     * Type of the receiver of the property, if this is an extension property.
     */
    public var receiverParameterType: KmType? = null

    /**
     * Types of context receivers of the property.
     */
    @ExperimentalContextReceivers
    public val contextReceiverTypes: MutableList<KmType> = ArrayList(0)

    /**
     * Value parameter of the setter of this property, if this is a `var` property and parameter is present.
     * Parameter is present if and only if the setter is not default:
     *
     * ```kotlin
     * var foo: String = ""
     *   set(param) {
     *     field = param.removePrefix("bar")
     *   }
     * ```
     */
    public var setterParameter: KmValueParameter? = null

    /**
     * Type of the property.
     */
    public lateinit var returnType: KmType

    /**
     * Version requirements on the property.
     */
    public val versionRequirements: MutableList<KmVersionRequirement> = ArrayList(0)

    @InternalExtensionsApi
    internal val extensions: List<KmPropertyExtension> =
        MetadataExtensions.INSTANCES.map(MetadataExtensions::createPropertyExtension)

    @InternalExtensionsApi
    public fun getExtension(type: KmExtensionType): KmPropertyExtension =
        extensions.singleOfType(type)
}

/**
 * Represents a Kotlin type alias declaration.
 *
 * Various type alias attributes can be read and manipulated via extension properties,
 * such as [KmTypeAlias.visibility] or [KmTypeAlias.hasAnnotations].
 *
 * @property name the name of the type alias
 */
public class KmTypeAlias internal constructor(
    internal var flags: Int,
    public var name: String,
) {

    public constructor(name: String) : this(0, name)

    /**
     * Type parameters of the type alias.
     */
    public val typeParameters: MutableList<KmTypeParameter> = ArrayList(0)

    /**
     * Underlying type of the type alias, i.e. the type in the right-hand side of the type alias declaration.
     */
    public lateinit var underlyingType: KmType

    /**
     * Expanded type of the type alias, i.e. the full expansion of the underlying type, where all type aliases are substituted
     * with their expanded types. If no type aliases are used in the underlying type, the expanded type is equal to the underlying type.
     */
    public lateinit var expandedType: KmType

    /**
     * Annotations on the type alias.
     */
    public val annotations: MutableList<KmAnnotation> = ArrayList(0)

    /**
     * Version requirements on the type alias.
     */
    public val versionRequirements: MutableList<KmVersionRequirement> = ArrayList(0)

    @InternalExtensionsApi
    internal val extensions: List<KmTypeAliasExtension> =
        MetadataExtensions.INSTANCES.mapNotNull(MetadataExtensions::createTypeAliasExtension)

    @InternalExtensionsApi
    public fun visitExtensions(type: KmExtensionType): KmTypeAliasExtension =
        extensions.singleOfType(type)
}

/**
 * Represents a value parameter of a Kotlin constructor, function or property setter.
 *
 * Various value parameter attributes can be read and manipulated via extension properties,
 * such as [KmValueParameter.declaresDefaultValue].
 *
 * @property name the name of the value parameter
 */
public class KmValueParameter internal constructor(
    internal var flags: Int,
    public var name: String,
) {

    public constructor(name: String) : this(0, name)

    /**
     * Type of the value parameter.
     * If this is a `vararg` parameter of type `X`, returns the type `Array<out X>`.
     */
    public lateinit var type: KmType

    /**
     * Type of the `vararg` value parameter, or `null` if this is not a `vararg` parameter.
     */
    public var varargElementType: KmType? = null

    @InternalExtensionsApi
    internal val extensions: List<KmValueParameterExtension> =
        MetadataExtensions.INSTANCES.mapNotNull(MetadataExtensions::createValueParameterExtension)

    @InternalExtensionsApi
    public fun visitExtensions(type: KmExtensionType): KmValueParameterExtension =
        extensions.singleOfType(type)
}

/**
 * Represents a type parameter of a Kotlin class, function, property or type alias.
 *
 * Various type parameter attributes can be read and manipulated via extension properties,
 * such as [KmTypeParameter.isReified].
 *
 * @property name the name of the type parameter
 * @property id the id of the type parameter, useful to be able to uniquely identify the type parameter in different contexts where
 *           the name is not enough (e.g. `class A<T> { fun <T> foo(t: T) }`)
 * @property variance the declaration-site variance of the type parameter
 */
public class KmTypeParameter internal constructor(
    internal var flags: Int,
    public var name: String,
    public var id: Int,
    public var variance: KmVariance,
) {

    public constructor(name: String, id: Int, variance: KmVariance) : this(0, name, id, variance)

    /**
     * Upper bounds of the type parameter.
     */
    public val upperBounds: MutableList<KmType> = ArrayList(1)

    @InternalExtensionsApi
    internal val extensions: List<KmTypeParameterExtension> =
        MetadataExtensions.INSTANCES.map(MetadataExtensions::createTypeParameterExtension)

    @InternalExtensionsApi
    public fun visitExtensions(type: KmExtensionType): KmTypeParameterExtension =
        extensions.singleOfType(type)
}

/**
 * Represents a type.
 *
 * Various type attributes can be read and manipulated via extension properties,
 * such as [KmType.isNullable].
 */
public class KmType internal constructor(
    internal var flags: Int,
) {

    public constructor() : this(0)

    /**
     * Classifier of the type.
     */
    public lateinit var classifier: KmClassifier

    /**
     * Arguments of the type, if the type's classifier is a class or a type alias.
     */
    public val arguments: MutableList<KmTypeProjection> = ArrayList(0)

    /**
     * Abbreviation of this type. Note that all types are expanded for metadata produced by the Kotlin compiler. For example:
     *
     *     typealias A<T> = MutableList<T>
     *
     *     fun foo(a: A<Any>) {}
     *
     * The type of the `foo`'s parameter in the metadata is actually `MutableList<Any>`, and its abbreviation is `A<Any>`.
     */
    public var abbreviatedType: KmType? = null

    /**
     * Outer type of this type, if this type's classifier is an inner class. For example:
     *
     *     class A<T> { inner class B<U> }
     *
     *     fun foo(a: A<*>.B<Byte?>) {}
     *
     * The type of the `foo`'s parameter in the metadata is `B<Byte>` (a type whose classifier is class `B`, and it has one type argument,
     * type `Byte?`), and its outer type is `A<*>` (a type whose classifier is class `A`, and it has one type argument, star projection).
     */
    public var outerType: KmType? = null

    /**
     * Upper bound of this type, if this type is flexible. In that case, all other data refers to the lower bound of the type.
     *
     * Flexible types in Kotlin include platform types in Kotlin/JVM and `dynamic` type in Kotlin/JS.
     */
    public var flexibleTypeUpperBound: KmFlexibleTypeUpperBound? = null

    @InternalExtensionsApi
    internal val extensions: List<KmTypeExtension> =
        MetadataExtensions.INSTANCES.map(MetadataExtensions::createTypeExtension)

    @InternalExtensionsApi
    public fun visitExtensions(type: KmExtensionType): KmTypeExtension =
        extensions.singleOfType(type)
}

/**
 * Represents a version requirement on a Kotlin declaration.
 *
 * Version requirement is an internal feature of the Kotlin compiler and the standard Kotlin library,
 * enabled, for example, with the internal [kotlin.internal.RequireKotlin] annotation.
 */
public class KmVersionRequirement {
    /**
     * Kind of the version that this declaration requires.
     */
    public lateinit var kind: KmVersionRequirementVersionKind

    /**
     * Level of the diagnostic that must be reported on the usages of the declaration in case the version requirement is not satisfied.
     */
    public lateinit var level: KmVersionRequirementLevel

    /**
     * Optional error code to be displayed in the diagnostic.
     */
    public var errorCode: Int? = null

    /**
     * Optional message to be displayed in the diagnostic.
     */
    public var message: String? = null

    /**
     * Version required by this requirement.
     */
    public lateinit var version: KmVersion

    /**
     * Returns the String representation of this KmVersionRequirement object, consisting of
     * [kind], [level], [version], [errorCode], and [message].
     */
    override fun toString(): String {
        return "KmVersionRequirement(kind=$kind, level=$level, version=$version, errorCode=$errorCode, message=$message)"
    }
}

/**
 * Represents a contract of a Kotlin function.
 *
 * Contracts are an internal feature of the standard Kotlin library, and their behavior and/or binary format
 * may change in a subsequent release.
 */
@ExperimentalContracts
public class KmContract {
    /**
     * Effects of this contract.
     */
    public val effects: MutableList<KmEffect> = ArrayList(1)
}

/**
 * Represents an effect (a part of the contract of a Kotlin function).
 *
 * Contracts are an internal feature of the standard Kotlin library, and their behavior and/or binary format
 * may change in a subsequent release.
 *
 * @property type type of the effect
 * @property invocationKind optional number of invocations of the lambda parameter of this function,
 *   specified further in the effect expression
 */
@ExperimentalContracts
public class KmEffect(
    public var type: KmEffectType,
    public var invocationKind: KmEffectInvocationKind?,
) {
    /**
     * Arguments of the effect constructor, i.e. the constant value for the [KmEffectType.RETURNS_CONSTANT] effect,
     * or the parameter reference for the [KmEffectType.CALLS] effect.
     */
    public val constructorArguments: MutableList<KmEffectExpression> = ArrayList(1)

    /**
     * Conclusion of the effect. If this value is set, the effect represents an implication with this value as the right-hand side.
     */
    public var conclusion: KmEffectExpression? = null
}

/**
 * Represents an effect expression, the contents of an effect (a part of the contract of a Kotlin function).
 *
 * Contracts are an internal feature of the standard Kotlin library, and their behavior and/or binary format
 * may change in a subsequent release.
 *
 * Various effect expression attributes can be read and manipulated via extension properties,
 * such as [KmEffectExpression.isNegated].
 */
@ExperimentalContracts
public class KmEffectExpression {
    /**
     * Effect expression flags, consisting of [Flag.EffectExpression] flags.
     */
    internal var flags: Int = 0

    /**
     * Optional 1-based index of the value parameter of the function, for effects which assert something about
     * the function parameters. Index 0 means the extension receiver parameter.
     */
    public var parameterIndex: Int? = null

    /**
     * Constant value used in the effect expression.
     */
    public var constantValue: KmConstantValue? = null

    /**
     * Type used as the target of an `is`-expression in the effect expression.
     */
    public var isInstanceType: KmType? = null

    /**
     * Arguments of an `&&`-expression. If this list is non-empty, the resulting effect expression is a conjunction of this expression
     * and elements of the list.
     */
    public val andArguments: MutableList<KmEffectExpression> = ArrayList(0)

    /**
     * Arguments of an `||`-expression. If this list is non-empty, the resulting effect expression is a disjunction of this expression
     * and elements of the list.
     */
    public val orArguments: MutableList<KmEffectExpression> = ArrayList(0)
}

/**
 * Represents a classifier of a Kotlin type. A classifier is a class, type parameter, or type alias.
 * For example, in `MutableMap<in String?, *>`, `MutableMap` is the classifier.
 */
public sealed class KmClassifier {
    /**
     * Represents a class used as a classifier in a type.
     *
     * @property name the name of the class
     */
    public data class Class(val name: ClassName) : KmClassifier()

    /**
     * Represents a type parameter used as a classifier in a type.
     *
     * @property id id of the type parameter
     */
    public data class TypeParameter(val id: Int) : KmClassifier()

    /**
     * Represents a type alias used as a classifier in a type. Note that all types are expanded for metadata produced
     * by the Kotlin compiler, so the type with a type alias classifier may only appear in [KmType.abbreviatedType].
     *
     * @property name the name of the type alias
     */
    public data class TypeAlias(val name: ClassName) : KmClassifier()
}

/**
 * Represents type projection used in a type argument of the type based on a class or on a type alias.
 * For example, in `MutableMap<in String?, *>`, `in String?` is the type projection which is the first type argument of the type.
 *
 * @property variance the variance of the type projection, or `null` if this is a star projection
 * @property type the projected type, or `null` if this is a star projection
 */
public data class KmTypeProjection(var variance: KmVariance?, var type: KmType?) {
    /**
     * Contains default instance for star projection: [KmTypeProjection.STAR].
     */
    public companion object {
        /**
         * Star projection (`*`).
         * For example, in `MutableMap<in String?, *>`, `*` is the star projection which is the second type argument of the type.
         */
        @JvmField
        public val STAR: KmTypeProjection = KmTypeProjection(null, null)
    }
}

/**
 * Represents an upper bound of a flexible Kotlin type.
 *
 * @property type upper bound of the flexible type
 * @property typeFlexibilityId id of the kind of flexibility this type has. For example, "kotlin.jvm.PlatformType" for JVM platform types,
 *                          or "kotlin.DynamicType" for JS dynamic type
 */
public data class KmFlexibleTypeUpperBound(var type: KmType, var typeFlexibilityId: String?) {
    /**
     * A companion object providing possibility to declare various platform-dependent constant ids as extension properties of it.
     */
    public companion object
}

/**
 * Represents a version used in a version requirement.
 *
 * @property major the major component of the version (e.g. "1" in "1.2.3")
 * @property minor the minor component of the version (e.g. "2" in "1.2.3")
 * @property patch the patch component of the version (e.g. "3" in "1.2.3")
 */
public data class KmVersion(val major: Int, val minor: Int, val patch: Int) {

    /**
     * Returns a string representation of this version in "$major.$minor.$patch" form.
     */
    override fun toString(): String = "$major.$minor.$patch"
}

/**
 * Represents a constant value used in an effect expression.
 *
 * Contracts are an internal feature of the standard Kotlin library, and their behavior and/or binary format
 * may change in a subsequent release.
 *
 * @property value the constant value. May be `true`, `false` or `null`
 */
@ExperimentalContracts
public data class KmConstantValue(val value: Any?)

private const val flagAccessPrefix = "Flag API is deprecated. Please use corresponding member extensions on"
