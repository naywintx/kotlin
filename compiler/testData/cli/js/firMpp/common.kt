package kotlin

interface I {
    fun f()
}

expect class A : I {
    fun test()
    override fun f()
}

expect fun test2(): Unit

public annotation class OptionalExpectation

@OptionalExpectation
public expect annotation class JsName(val name: String)

public expect interface AutoCloseable {
    public fun close(): Unit
}

fun main() {
    val int = 0
    // K1/compileKotlinJs: ok
    // K2/compileKotlinJs: compiler crash (java.util.NoSuchElementException @ org.jetbrains.kotlin.fir.scopes.impl.FirIntegerConstantOperatorScope.processFunctionsByName(FirIntegerConstantOperatorScope.kt:177))
    42 + int
}