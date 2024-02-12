@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, FreezingIsDeprecated::class, kotlin.native.runtime.NativeRuntimeApi::class)

import kotlin.native.concurrent.*
import kotlin.native.ref.*
import kotlin.test.*

fun main() {
    test1()
    test2()
}

fun test1() {
    ensureGetsCollected { LazyCapturesThis() }
    ensureGetsCollected {
        val l = LazyCapturesThis()
        l.bar
        l
    }
}

class LazyCapturesThis {
    fun foo() = 42
    val bar by lazy { foo() }
}

fun test2() {
    ensureGetsCollected { Throwable() }
    ensureGetsCollected {
        val throwable = Throwable()
        throwable.getStackTrace()
        throwable
    }
}

fun ensureGetsCollected(create: () -> Any) {
    val ref = makeWeakRef(create)
    kotlin.native.runtime.GC.collect()
    assertNull(ref.get())
}

fun makeWeakRef(create: () -> Any) = WeakReference(create())