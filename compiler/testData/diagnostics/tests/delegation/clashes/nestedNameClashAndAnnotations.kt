// FIR_IDENTICAL
// FIR_DUMP
// IGNORE_DIAGNOSTIC_API
// IGNORE_REVERSED_RESOLVE
// Reason: FIR evaluator is not called in api tests, so we have different dump
package second

@Target(AnnotationTarget.TYPE)
annotation class Anno(val i: Int)

interface Base<A> {
    fun foo() {}
}

const val outer = 0
const val inner = ""

class MyClass(val prop: @Anno(0 + inner) second.Base<@Anno(1 + inner) second.Base<@Anno(2 + inner) Int>>): @Anno(3 + outer) Base<@Anno(4 + outer) Base<@Anno(5 + outer) Int>> by prop {
    interface Base<B>

    companion object {
        const val outer = ""
        const val inner = 0
    }
}
