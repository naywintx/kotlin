/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.metadata

import kotlin.contracts.ExperimentalContracts

/**
 * Variance applied to a type parameter on the declaration site (*declaration-site variance*),
 * or to a type in a projection (*use-site variance*).
 */
public enum class KmVariance {
    /**
     * The affected type parameter or type is *invariant*, which means it has no variance applied to it.
     */
    INVARIANT,

    /**
     * The affected type parameter or type is *contravariant*. Denoted by the `in` modifier in the source code.
     */
    IN,

    /**
     * The affected type parameter or type is *covariant*. Denoted by the `out` modifier in the source code.
     */
    OUT,
}

/**
 * Type of an effect (a part of the contract of a Kotlin function).
 *
 * Contracts are an internal feature of the standard Kotlin library, and their behavior and/or binary format
 * may change in a subsequent release.
 */
@ExperimentalContracts
public enum class KmEffectType {
    /**
     * Represents `returns(value)` contract effect:
     * a situation when a function returns normally with the specified return value.
     * Return value is stored in the [KmEffect.constructorArguments].
     */
    RETURNS_CONSTANT,

    /**
     * Represents `callsInPlace` contract effect:
     * A situation when the referenced lambda is invoked in place (optionally) specified number of times.
     *
     * Referenced lambda is stored in the [KmEffect.constructorArguments].
     * Number of invocations, if specified, is stored in [KmEffect.invocationKind].
     */
    CALLS,

    /**
     * Represents `returnsNotNull` contract effect:
     * a situation when a function returns normally with any value that is not null.
     */
    RETURNS_NOT_NULL,
}

/**
 * Number of invocations of a lambda parameter specified by an effect (a part of the contract of a Kotlin function).
 *
 * Contracts are an internal feature of the standard Kotlin library, and their behavior and/or binary format
 * may change in a subsequent release.
 */
@ExperimentalContracts
public enum class KmEffectInvocationKind {
    /**
     * A function parameter will be invoked one time or not invoked at all.
     */
    AT_MOST_ONCE,

    /**
     * A function parameter will be invoked exactly one time.
     */
    EXACTLY_ONCE,

    /**
     * A function parameter will be invoked one or more times.
     */
    AT_LEAST_ONCE,
}

/**
 * Severity of the diagnostic reported by the compiler when a version requirement is not satisfied.
 */
public enum class KmVersionRequirementLevel {
    /**
     * Represents a diagnostic with 'WARNING' severity.
     */
    WARNING,

    /**
     * Represents a diagnostic with 'ERROR' severity.
     */
    ERROR,

    /**
     * Excludes the declaration from the resolution process completely when the version requirement is not satisfied.
     */
    HIDDEN,
}

/**
 * The kind of the version that is required by a version requirement.
 */
public enum class KmVersionRequirementVersionKind {
    /**
     * Indicates that certain language version is required.
     */
    LANGUAGE_VERSION,

    /**
     * Indicates that certain compiler version is required.
     */
    COMPILER_VERSION,

    /**
     * Indicates that certain API version is required.
     */
    API_VERSION,

    /**
     * Represents a version requirement not successfully parsed from the metadata.
     *
     * The old metadata format (from Kotlin 1.3 and earlier) did not have enough information for correct parsing of version requirements in some cases,
     * so a stub of this kind is inserted instead.
     *
     * [KmVersionRequirement] with this kind always has [KmVersionRequirementLevel.HIDDEN] level, `256.256.256` [KmVersionRequirement.version],
     * and `null` [KmVersionRequirement.errorCode] and [KmVersionRequirement.message].
     *
     * Version requirements of this kind are being ignored by writers (i.e., are not written back).
     *
     * See the following issues for details: [KT-60870](https://youtrack.jetbrains.com/issue/KT-60870), [KT-25120](https://youtrack.jetbrains.com/issue/KT-25120)
     */
    UNKNOWN
    ;
}
