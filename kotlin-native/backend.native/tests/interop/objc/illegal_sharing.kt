@file:OptIn(FreezingIsDeprecated::class, ObsoleteWorkersApi::class)

import kotlin.native.concurrent.*
import kotlin.test.*
import platform.Foundation.*
import platform.darwin.NSObject

fun Worker.runInWorker(block: () -> Unit) {
    this.execute(TransferMode.SAFE, { block }) {
        it()
    }.result
}

private class NSObjectImpl : NSObject() {
    var x = 111
}

// Also see counterpart in interop/objc/tests/sharing.kt
fun main() = withWorker {
    val obj = NSObjectImpl()
    val array: NSArray = NSMutableArray().apply {
        addObject(obj)
    }

    println("Before")
    runInWorker {
        array.objectAtIndex(0)
    }
    println("After")
}
