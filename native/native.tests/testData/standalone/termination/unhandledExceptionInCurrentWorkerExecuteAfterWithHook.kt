// No termination is going on here. But that's the closest location to other unhandled exception hook tests.
// OUTPUT_REGEX: hook called\R(?!.*an error.*)Will happen
import kotlin.test.*

import kotlin.native.concurrent.*

@Test
fun testExecuteAfterStartQuiet() {
    setUnhandledExceptionHook {
        println("hook called")
    }
    Worker.current.executeAfter(0L, {
        throw Error("an error")
    })
    Worker.current.processQueue()
    println("Will happen")
}