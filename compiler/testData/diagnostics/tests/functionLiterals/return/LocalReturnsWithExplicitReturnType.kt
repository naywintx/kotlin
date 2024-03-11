typealias MyUnit = Unit

fun test(a: Int) {
    run<Int>f@{
      if (a > 0) return@f <!TYPE_MISMATCH!>""<!>
      return@f 1
    }

    run<MyUnit>f@ {
        return@f <!TYPE_MISMATCH, TYPE_MISMATCH!>""<!>
    }

    run<Int>{ <!TYPE_MISMATCH!>""<!> }
    run<Int>{ 1 }
}
