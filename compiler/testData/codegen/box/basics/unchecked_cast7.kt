// IGNORE_NATIVE: optimizationMode=OPT

fun box(): String {
    try {
        val x = Any().uncheckedCast<Int?>()
        return "FAIL: $x"
    } catch (e: ClassCastException) {
        return "OK"
    }
}

fun <T> Any?.uncheckedCast(): T = this as T