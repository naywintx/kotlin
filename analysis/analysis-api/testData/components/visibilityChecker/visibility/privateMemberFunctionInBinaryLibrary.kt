// MODULE: declarationSite
// MODULE_KIND: LibraryBinary
// FILE: declarationSite.kt
class Hello {
    private fun target() { }
}

// MODULE: useSite(declarationSite)
// FILE: useSite.kt
fun foo() {
    p<caret>rintln()
}
