// FIR_IDENTICAL
class Controller<T> {
    fun yield(t: T): Boolean = true
}

fun <S> generate(g: suspend Controller<S>.() -> Unit): S = TODO()

fun foo() {
    val t = generate {
        yield("")
        <!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER, NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER, NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>bar<!>(this, "") { it.<!UNRESOLVED_REFERENCE!>length<!> }
    }

    <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.String")!>t<!>
}

fun <R, F : Controller<in R>> bar(f: F, x: R, b: (R) -> Unit) {}