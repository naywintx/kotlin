@file:OptIn(FreezingIsDeprecated::class, kotlin.experimental.ExperimentalNativeApi::class, ObsoleteWorkersApi::class)

import kotlin.native.concurrent.*

fun main() {
    setUnhandledExceptionHook({ _: Throwable ->
        println("hook called")
    })

    Worker.current.executeAfter(0L, {
        throw Error("some error")
    })
    Worker.current.processQueue()
    println("Will happen")
}
