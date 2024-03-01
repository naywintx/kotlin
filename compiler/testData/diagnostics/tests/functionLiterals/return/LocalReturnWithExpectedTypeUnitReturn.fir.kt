// ISSUE: KT-66277, KT-66279, KT-66512, KT-66534
// WITH_STDLIB

// FILE: A.java
import org.jetbrains.annotations.NotNull;
import kotlin.jvm.functions.Function0;
import kotlin.Unit;

public class A {
    public static @NotNull Function0<Unit> foo;
}

// FILE: main.kt
fun foo() {}

typealias MyUnit = Unit

val expectedAnyImplicitReturnUnitAndString: () -> Any = l@ {
    if ("0".hashCode() == 42) return@l
    ""
}

val expectedUnitImplicitReturnUnitAndString: () -> Unit = l@ {
    if ("0".hashCode() == 42) return@l
    ""
}

val expectedNullableUnitImplicitReturnUnitAndString: () -> Unit? = l@ {
    if ("0".hashCode() == 42) return@l
    ""
}

val expectedNullableUnitImplicitReturnUnitAndNull: () -> Unit? = l@ {
    if ("0".hashCode() == 42) return@l
    return@l <!NULL_FOR_NONNULL_TYPE!>null<!>
}

fun expectedFlexibleUnitImplicitReturnUnitAndString() {
    A.foo = l@ {
        if ("0".hashCode() == 42) return@l
        ""
    }
}

fun expectedFlexibleUnitImplicitReturnUnitAndNull() {
    A.foo = l@ {
        if ("0".hashCode() == 42) return@l
        return@l <!NULL_FOR_NONNULL_TYPE!>null<!>
    }
}

val expectedAnyExplicitReturnUnitAndString: () -> Any = l@ {
    if ("0".hashCode() == 42) return@l Unit
    ""
}

val expectedUnitExplicitReturnUnitAndString: () -> Unit = <!INITIALIZER_TYPE_MISMATCH!>l@ {
    if ("0".hashCode() == 42) return@l Unit
    ""
}<!>

val expectedUnitExplicitReturnUnitAndString2: () -> Unit = <!INITIALIZER_TYPE_MISMATCH!>l@ {
    if ("0".hashCode() == 42) return@l foo()
    ""
}<!>

val expectedNullableUnitExplicitReturnUnitAndString: () -> Unit? = <!INITIALIZER_TYPE_MISMATCH!>l@ {
    if ("0".hashCode() == 42) return@l Unit
    ""
}<!>

fun expectedFlexibleUnitExplicitReturnUnitAndString() {
    A.foo = l@ <!ASSIGNMENT_TYPE_MISMATCH!>{
        if ("0".hashCode() == 42) return@l Unit
        ""
    }<!>
}

val expectedUnitExplicitReturnMyUnitAndString: () -> Unit = <!INITIALIZER_TYPE_MISMATCH!>l@ {
    if ("0".hashCode() == 42) return@l MyUnit
    ""
}<!>

val expectedMyUnitExplicitReturnString: () -> MyUnit = <!INITIALIZER_TYPE_MISMATCH!>l@ {
    return@l ""
}<!>

val expectedNullableUnitExplicitReturnString: () -> Unit? = <!INITIALIZER_TYPE_MISMATCH!>l@ {
    return@l ""
}<!>

val expectedNullableUnitExplicitReturnNull: () -> Unit? = l@ {
    return@l null
}

fun expectedFlexibleUnitExplicitReturnString() {
    A.foo = l@ <!ASSIGNMENT_TYPE_MISMATCH!>{
        return@l ""
    }<!>
}

fun expectedFlexibleUnitExplicitReturnNull() {
    A.foo = l@ {
        return@l null
    }
}

val expectedAnyImplicitReturnUnitOnly: () -> Any = l@ {
    if ("0".hashCode() == 42) return@l
    return@l
}

val expectedAnyImplicitAndExplicitReturnUnit: () -> Any = l@ {
    if ("0".hashCode() == 42) return@l
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
