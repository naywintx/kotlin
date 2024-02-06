// TARGET_BACKEND: WASM
// ENABLE_CCE_ON_GENERIC_FUNCTION_RETURN

class Some

fun <T> some(): T = Any() as T

fun box(): String {
    try {
        some<Some>()
    } catch(e: ClassCastException) {
        return "OK"
    }
    return "FAIL"
}