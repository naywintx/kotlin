// MODULE: declarationSite
// MODULE_KIND: LibraryBinary
// FILE: declarationSite.kt
class Hello {
    fun target() { }
}

// MODULE: useSite(declarationSite)
// FILE: useSite.kt
fun foo() {
    p<caret>rintln()
}
