// EXIT_CODE: !0
// OUTPUT_REGEX: .*an error(?!.*Will not happen.*)
import kotlin.test.*

import kotlin.native.concurrent.*

fun main() {
    Worker.current.executeAfter(0L, {
        throw Error("an error")
    })
    Worker.current.processQueue()
    println("Will not happen")
}