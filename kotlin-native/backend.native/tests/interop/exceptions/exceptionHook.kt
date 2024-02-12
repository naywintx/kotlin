@file:OptIn(FreezingIsDeprecated::class, kotlin.experimental.ExperimentalNativeApi::class)

// KT-47828
fun setHookAndThrow() {
    val hook = { _: Throwable ->
        println("OK. Kotlin unhandled exception hook")
    }
    setUnhandledExceptionHook(hook)
    throw Exception("Error")
}