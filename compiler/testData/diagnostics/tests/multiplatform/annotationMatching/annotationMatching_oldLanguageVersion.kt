// FIR_IDENTICAL
// LANGUAGE: -ProhibitDifferentAnnotationsOnExpectAndActual
// MODULE: m1-common
// FILE: common.kt
annotation class Ann

@Ann
expect class AnnotationMatching

@Ann
expect class AnnotationOnExpectOnly

expect class AnnotationOnActualOnly

expect class AnnotationInside {
    @Ann
    fun matches()

    @Ann
    fun onlyOnExpect()

    fun onlyOnActual()
}

// MODULE: m1-jvm()()(m1-common)
// FILE: jvm.kt
@Ann
actual class AnnotationMatching

actual class AnnotationOnExpectOnly

@Ann
actual class AnnotationOnActualOnly

actual class AnnotationInside {
    @Ann
    actual fun matches() {}

    actual fun onlyOnExpect() {}

    @Ann
    actual fun onlyOnActual() {}
}
