class Data(val x: Int)

fun box(): String {
    val arr = arrayOf("zzz")
    try {
        var x: Int = 0
        for (it in (arr as Array<Data>)) {
            x = it.x
        }
        return "FAIL: $x"
    } catch (e: ClassCastException) {
        return "OK"
    }
}
