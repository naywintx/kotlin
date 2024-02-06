// MODULE: m1-common
// FILE: common.kt

expect open class Foo {
    open var foo: String
        protected set
}

// MODULE: m1-jvm()()(m1-common)
// FILE: jvm.kt

actual open class Foo {
    actual open var foo: String = ""
        <!ACTUAL_WITHOUT_EXPECT!>public<!> set
}
