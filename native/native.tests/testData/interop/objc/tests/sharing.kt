@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, FreezingIsDeprecated::class, ObsoleteWorkersApi::class)

import kotlinx.cinterop.*
import kotlin.native.concurrent.*
import kotlin.test.*
import objcTests.*

private class NSObjectImpl : NSObject() {
    var x = 111
}

// Also see counterpart interop/objc/illegal_sharing.kt
@Test fun testSharing() = withWorker {
    val obj = NSObjectImpl()
    val array = nsArrayOf(obj)

    obj.x = 222

    // TODO: test [obj release] etc.
}
