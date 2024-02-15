// TARGET_BACKEND: JVM_IR
// WITH_STDLIB
// WITH_REFLECT
// JVM_ABI_K1_K2_DIFF: K2 names companion objects in metadata correctly

import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val KType.str get() = classifier.toString()

class A {
    fun foo(): String {
        class Nested {
            inner class Inner {
                val prop = this
            }
        }
        return Nested().Inner()::class.memberProperties.iterator().next().returnType.str
    }
}

fun foo1(): String {
    class X {
        inner class Y {
            companion object Z

            val prop = Z
        }
    }
    return X.Y::class.memberProperties.iterator().next().returnType.str
}

fun foo2(): String {
    class X {
        inner class Y {
            companion object

            val prop = Companion
        }
    }
    return X.Y::class.memberProperties.iterator().next().returnType.str
}

fun foo3(): String {
    class X {
        inner class Y {
            val prop = object {}
        }
    }
    return X.Y::class.memberProperties.iterator().next().returnType.str
}

fun foo4(): String {
    var res = ""

    class A {
        inner class B {
            inner class C {
                fun bar() {
                    class D {
                        val prop = this
                    }
                    res = D::class.memberProperties.iterator().next().returnType.str
                }

                init {
                    bar()
                }
            }
        }
    }
    A().B().C()
    return res
}

fun foo5(): String {
    var res = ""
    object {
        fun bar() {
            return object {
                fun foo() {
                    class A {
                        inner class B {
                            val prop = this
                            init {
                                res = prop::class.memberProperties.iterator().next().returnType.str
                            }
                        }
                    }
                    A().B()
                }
            }.foo()
        }
    }.bar()
    return res
}

fun foo6(): String {
    var res = ""
    object {
        fun bar() {
            class A {
                inner class B {
                    inner class C {
                        val prop = this

                        init {
                            res = prop::class.memberProperties.iterator().next().returnType.str
                        }
                    }
                }
            }
            A().B().C()
        }
    }.bar()
    return res
}

fun foo7(): String {
    var res = ""
    val x = object {
        val y = object {
            val z = object {
                val y = this
                init {
                    res = this::class.memberProperties.iterator().next().returnType.str
                }
            }
        }
    }
    return res
}

fun box(): String {
    assertTrue(A().foo().endsWith("A\$foo\$Nested\$Inner"), "A().foo() not ends with A\$foo\$Nested\$Inner")
    assertTrue(foo1().endsWith("LocalNestedClassesKt\$foo1\$X\$Y\$Z"), "foo1() not ends with LocalNestedClassesKt\$foo1\$X\$Y\$Z")
    assertTrue(foo2().endsWith("LocalNestedClassesKt\$foo2\$X\$Y\$Companion"), "foo2() not ends with LocalNestedClassesKt\$foo2\$X\$Y\$Companion")
    assertTrue(foo3().endsWith("LocalNestedClassesKt\$foo3\$X\$Y\$prop\$1"), "foo3() not ends with LocalNestedClassesKt\$foo3\$X\$Y\$prop\$1")
    assertTrue(foo4().endsWith("LocalNestedClassesKt\$foo4\$A\$B\$C\$bar\$D"), "foo4() not ends with LocalNestedClassesKt\$foo4\$A\$B\$C\$bar\$D")
    assertTrue(foo5().endsWith("LocalNestedClassesKt\$foo5\$1\$bar\$1\$foo\$A\$B"), "foo5() not ends with LocalNestedClassesKt\$foo5\$1\$bar\$1\$foo\$A\$B")
    assertTrue(foo6().endsWith("LocalNestedClassesKt\$foo6\$1\$bar\$A\$B\$C"), "foo6() not ends with LocalNestedClassesKt\$foo6\$1\$bar\$A\$B\$C")
    assertTrue(foo7().endsWith("LocalNestedClassesKt\$foo7\$x\$1\$y\$1\$z\$1"), "foo7() not ends with LocalNestedClassesKt\$foo7\$x\$1\$y\$1\$z\$1")
    return "OK"
}
