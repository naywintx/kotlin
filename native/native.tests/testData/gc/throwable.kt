// DISABLE_NATIVE: gcType=NOOP
import kotlin.native.ref.WeakReference
import kotlin.native.runtime.GC
import kotlin.test.*

private fun makeWeakRef(create: () -> Any) = WeakReference(create())

private fun ensureGetsCollected(create: () -> Any) {
    val ref = makeWeakRef(create)
    GC.collect()
    assertNull(ref.get())
}

@Test
fun test() {
    ensureGetsCollected { Throwable() }
    ensureGetsCollected {
        val throwable = Throwable()
        throwable.getStackTrace()
        throwable
    }
}