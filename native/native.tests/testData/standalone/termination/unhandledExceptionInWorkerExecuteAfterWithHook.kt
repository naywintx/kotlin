// No termination is going on here. But that's the closest location to other unhandled exception hook tests.
// KIND: REGULAR
// OUTPUT_REGEX: hook called\R(?!.*an error.*)Will happen
import kotlin.test.*

import kotlin.native.concurrent.*

@Test
fun testExecuteAfterStartQuiet() {
    setUnhandledExceptionHook {
        println("hook called")
    }
    val worker = Worker.start()
    worker.executeAfter(0L, {
        throw Error("an error")
    })
    worker.requestTermination().result
    println("Will happen")
}