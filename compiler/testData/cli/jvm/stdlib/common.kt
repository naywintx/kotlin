// ISSUE: KT-65841

package kotlin

expect class Int

fun test(x: Int): Int {
    return 42 + x
}