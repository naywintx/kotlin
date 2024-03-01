// WITH_STDLIB

// FILE: A.java
import org.jetbrains.annotations.NotNull;
import kotlin.jvm.functions.Function0;
import kotlin.Unit;

public class A {
    public static void run(@NotNull Function0<Unit> f) {}
}

// FILE: main.kt
typealias MyUnit = Unit

fun foo() {}

fun test(a: Int) {
    run<Int>f@{
      if (a > 0) return@f <!ARGUMENT_TYPE_MISMATCH, RETURN_TYPE_MISMATCH!>""<!>
      return@f 1
    }

    run<Any>f@ {
        if (a > 0) return@f
        ""
    }

    run<Unit>f@ {
        if (a > 0) return@f
        ""
    }

    run<Unit?>f@ {
        if (a > 0) return@f
        ""
    }

    run<Unit?>f@ {
        if (a > 0) return@f
        return@f <!NULL_FOR_NONNULL_TYPE!>null<!>
    }

    A.run f@ {
        if (a > 0) return@f
        ""
    }

    A.run f@ {
        if (a > 0) return@f
        return@f null
    }

    A.run f@ {
        if (a > 0) return@f Unit
        return@f null
    }

    A.run f@ {
        return@f <!ARGUMENT_TYPE_MISMATCH, RETURN_TYPE_MISMATCH!>""<!>
    }

    A.run f@ {
        return@f null
    }

    run<MyUnit>f@ {
        return@f <!ARGUMENT_TYPE_MISMATCH, RETURN_TYPE_MISMATCH!>""<!>
    }

    run<Unit?>f@ {
        return@f <!ARGUMENT_TYPE_MISMATCH, RETURN_TYPE_MISMATCH!>""<!>
    }

    run<Unit?>f@ {
        return@f null
    }

    run<Any>f@ {
        if (a > 0) return@f Unit
        ""
    }

    run<Unit>f@ {
        if (a > 0) return@f Unit
        ""
    }

    run<Unit>f@ {
        if (a > 0) return@f foo()
        ""
    }

    run<Unit?>f@ {
        if (a > 0) return@f Unit
        <!ARGUMENT_TYPE_MISMATCH!>""<!>
    }

    A.run f@ {
        if (a > 0) return@f Unit
        ""
    }

    run<Unit>f@ {
        if (a > 0) return@f MyUnit
        ""
    }

    run<Any>f@ {
        if (a > 0) return@f
        return@f
    }

    run<Any>f@ {
        if (a > 0) return@f
        return@f Unit
    }

    run<Unit>f@ {
        if (a > 0) return@f
        return@f
    }

    run<Unit>f@ {
        if (a > 0) return@f
        return@f Unit
    }

    run<Int>{ <!ARGUMENT_TYPE_MISMATCH!>""<!> }
    run<Int>{ 1 }
}
