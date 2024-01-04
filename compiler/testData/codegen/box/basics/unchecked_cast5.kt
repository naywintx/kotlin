// IGNORE_NATIVE: optimizationMode=OPT

class Data(val x: Int)

fun box(): String {
    val arr = arrayOf("zzz")
    try {
        val x = (arr as Array<Data>)[0].x
        return "FAIL: $x"
    } catch (e: ClassCastException) {
        return "OK"
    }
}
