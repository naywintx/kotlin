// IGNORE_NATIVE: optimizationMode=OPT

fun box(): String {
    try {
        val x = ("1" as Comparable<Any>).compareTo(2)
        return "FAIL: $x"
    } catch (e: ClassCastException) {
        return "OK"
    }
}
