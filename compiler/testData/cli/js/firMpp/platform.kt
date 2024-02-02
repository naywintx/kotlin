package kotlin

public actual interface AutoCloseable {
    public actual fun close(): Unit
}

@JsName("A")
actual class A : I {
    actual fun test() {}
    actual override fun f() {}
}

actual fun test2() {}

class Int private constructor() {
    operator fun plus(other: Int): Int = 42

    fun times(other: Int) {}
    fun xor(other: Int) {}
}

fun Any?.toString(): String = "null"


class Any {
    fun toString() {}
}

object Unit

interface Annotation

class Enum<E>

interface Comparable

class Boolean {
    fun not() {}
}

class Number

class Byte
class Short
class Long

class Float
class Double

class Char
interface CharSequence
class String {
    fun plus(other: Any?) {}
}

class Array<T>

class ByteArray
class CharArray
class ShortArray
class IntArray
class LongArray
class FloatArray
class DoubleArray
class BooleanArray

interface Function

class Throwable

class Nothing

fun arrayOfNulls(size: Int) {}
fun arrayOf(vararg elements: Any) {}
fun String?.plus(other: Any?) {}
