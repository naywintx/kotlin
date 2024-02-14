// MODULE: declarationSite
// MODULE_KIND: Source
// FILE: declarationSite.kt
class Hello {
    fun h<caret>ello() { }
}

// MODULE: useSite(declarationSite)
// FILE: useSite.kt
fun foo() {
    p<caret>rintln()
}
