// TARGET_BACKEND: WASM
// IGNORE_BACKEND: WASM
// Ignore reason KT-xxxxx

fun f1(x: Number): String = x.toString()
fun f2(x: Number): String = x.toString()

fun id1(f: (Float) -> String): String = js("f(42.2)")
fun id2(f: (Byte) -> String): String =  js("f(42)")

fun box(): String {

    val f1ref = ::f1
    val id11 = id1(f1ref)
    val id12 = id2(f1ref)

    val f2ref = ::f2
    val id22 = id2(f2ref)
    val id21 = id1(f2ref)

    if (id11 != id21) return "FAIL1"
    if (id12 != id22) return "FAIL2"

    return "OK"
}