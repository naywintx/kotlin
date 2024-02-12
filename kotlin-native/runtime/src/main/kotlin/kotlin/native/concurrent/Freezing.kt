/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package kotlin.native.concurrent

/**
 * Exception thrown whenever freezing is not possible.
 *
 * No standard library code throws this exception.
 *
 * @param toFreeze an object intended to be frozen.
 * @param blocker an object preventing freezing, usually one marked with [ensureNeverFrozen] earlier.
 */
@FreezingIsDeprecated
public class FreezingException(toFreeze: Any, blocker: Any) :
        RuntimeException("freezing of $toFreeze has failed, first blocker is $blocker")

/**
 * Exception thrown whenever we attempt to mutate frozen objects.
 *
 * No standard library code throws this exception.
 *
 * @param where a frozen object that was attempted to mutate
 */
@FreezingIsDeprecated
public class InvalidMutabilityException(message: String) : RuntimeException(message)

/**
 * This is deprecated and does nothing.
 *
 * @return the object itself
 */
@FreezingIsDeprecated
public fun <T> T.freeze(): T {
    return this
}

/**
 * This is deprecated and always returns false.
 *
 * @return false
 */
@FreezingIsDeprecated
public val Any?.isFrozen: Boolean
    get() = false

/**
 * This is deprecated and does nothing.
 */
@FreezingIsDeprecated
public fun Any.ensureNeverFrozen(): Unit = Unit