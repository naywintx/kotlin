// TARGET_BACKEND: WASM

fun checkLambdaEquality(a: () -> Int, b: () -> Int): Boolean = js("a === b")

fun box(): String {
    val x = { 42 }
    val y = { 24 }

    if (!checkLambdaEquality(x, x)) return "FAIL1"
    if (checkLambdaEquality(x, y)) return "FAIL2"

    return "OK"
}