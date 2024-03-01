open class A<T> {
    val s: T = "zzz" as T
}

interface C {
    val s: String
}

class B : C, A<String>()

class Data(val x: Int)

fun box(): String {
    val b = B()
    try {
        val s = (b as A<Data>).s
        return "FAIL: ${s.x}"
    } catch (e: ClassCastException) {
        return "OK"
    }
}
