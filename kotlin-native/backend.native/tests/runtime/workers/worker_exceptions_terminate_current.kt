@file:OptIn(FreezingIsDeprecated::class, ObsoleteWorkersApi::class)

import kotlin.native.concurrent.*

fun main() {
    Worker.current.executeAfter(0L, {
        throw Error("some error")
    })
    Worker.current.processQueue()
    println("Will not happen")
}
