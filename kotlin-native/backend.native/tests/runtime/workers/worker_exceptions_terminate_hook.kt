@file:OptIn(FreezingIsDeprecated::class, kotlin.experimental.ExperimentalNativeApi::class, ObsoleteWorkersApi::class)

import kotlin.native.concurrent.*

fun main() {
    setUnhandledExceptionHook({ _: Throwable ->
        println("hook called")
    })


    val worker = Worker.start()
    worker.executeAfter(0L, {
        throw Error("some error")
    })
    worker.requestTermination().result
    println("Will happen")
}
