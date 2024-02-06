// MODULE: m1-common
// FILE: common.kt

<!EXPECT_ACTUAL_INCOMPATIBILITY{JVM}!>expect open class Foo {
    <!EXPECT_ACTUAL_INCOMPATIBILITY{JVM}!>open var foo: String
        protected set<!>
}<!>

// MODULE: m1-jvm()()(m1-common)
// FILE: jvm.kt

actual open class Foo {
    actual open var <!ACTUAL_WITHOUT_EXPECT!>foo<!>: String = ""
        public set
}
