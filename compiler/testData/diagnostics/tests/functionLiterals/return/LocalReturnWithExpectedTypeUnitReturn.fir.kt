// ISSUE: KT-66277, KT-66279, KT-66512

fun foo() {}

typealias MyUnit = Unit

val expectedAnyImplicitReturnUnitAndString: () -> Any = l@ {
    if ("0".hashCode() == 42) <!RETURN_TYPE_MISMATCH!>return@l<!>
    ""
}

val expectedUnitImplicitReturnUnitAndString: () -> Unit = l@ {
    if ("0".hashCode() == 42) return@l
    ""
}

val expectedAnyExplicitReturnUnitAndString: () -> Any = l@ {
    if ("0".hashCode() == 42) return@l Unit
    ""
}

val expectedUnitExplicitReturnUnitAndString: () -> Unit = l@ {
    if ("0".hashCode() == 42) return@l Unit
    ""
}

val expectedUnitExplicitReturnUnitAndString2: () -> Unit = l@ {
    if ("0".hashCode() == 42) return@l foo()
    ""
}

val expectedUnitExplicitReturnMyUnitAndString: () -> Unit = l@ {
    if ("0".hashCode() == 42) return@l MyUnit
    ""
}

val expectedMyUnitExplicitReturnString: () -> MyUnit = <!INITIALIZER_TYPE_MISMATCH!>l@ {
    return@l ""
}<!>

val expectedAnyImplicitReturnUnitOnly: () -> Any = l@ {
    if ("0".hashCode() == 42) <!RETURN_TYPE_MISMATCH!>return@l<!>
    <!RETURN_TYPE_MISMATCH!>return@l<!>
}

val expectedAnyImplicitAndExplicitReturnUnit: () -> Any = l@ {
    if ("0".hashCode() == 42) <!RETURN_TYPE_MISMATCH!>return@l<!>
    return@l Unit
}

val expectedUnitImplicitReturnUnitOnly: () -> Unit = l@ {
    if ("0".hashCode() == 42) return@l
    return@l
}

val expectedUnitImplicitAndExplicitReturnUnit: () -> Unit = l@ {
    if ("0".hashCode() == 42) return@l
    return@l Unit
}
