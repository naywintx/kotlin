// MODULE: declarationSite
// MODULE_KIND: LibraryBinary
// FILE: declarationSite.kt
private class Target

// MODULE: useSite(declarationSite)
// FILE: useSite.kt
fun foo() {
    p<caret>rintln()
}
