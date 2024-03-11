// KIND: REGULAR
// EXIT_CODE: !0
// OUTPUT_REGEX: .*an error(?!.*Will not happen.*)
import kotlin.test.*

import kotlin.native.concurrent.*

@Test
fun testExecuteAfterStartQuiet() {
    val worker = Worker.start()
    worker.executeAfter(0L, {
        throw Error("an error")
    })
    worker.requestTermination().result
    println("Will not happen")
}