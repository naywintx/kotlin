// FIR_IDENTICAL
// MODULE: common
// TARGET_PLATFORM: Common

class <!PACKAGE_OR_CLASSIFIER_REDECLARATION!>Foo<!>

// MODULE: intermediate()()(common)
// TARGET_PLATFORM: Common

class Foo

// MODULE: main()()(common, intermediate)