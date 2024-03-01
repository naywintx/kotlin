interface I<T> {
    fun foo(p: T)
}

class C : I<Nothing> {
    override fun foo(p: Nothing) {
        println(p)
    }
}

fun box(): String {
    val c = C()
    try {
        val x = (c as I<String>).foo("zzz")
        return "FAIL: $x"
    } catch (e: ClassCastException) {
        return "OK"
    }
}
