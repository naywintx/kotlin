// MODULE: declarationSite
// MODULE_KIND: LibraryBinary
// FILE: declarationSite.kt
private class Target

// MODULE: useSite(declarationSite)
// MODULE_KIND: LibraryBinary
// FILE: useSite.kt
fun target() {
}
