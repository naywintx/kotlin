// FIR_IDENTICAL
// FIR_DUMP
// IGNORE_DIAGNOSTIC_API
// IGNORE_REVERSED_RESOLVE
// Reason: FIR evaluator is not called in api tests, so we have different dump
package second

@Target(AnnotationTarget.TYPE)
annotation class Anno(val int: Int)

interface Base
fun bar(): Base = object : Base {}

const val constant = 0

class MyClass: @Anno(constant) Base by bar() {
    @Target(AnnotationTarget.TYPE)
    annotation class Anno(val string: String)
}
