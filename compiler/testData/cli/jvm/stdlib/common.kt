// ISSUE: KT-65841

package kotlin

expect interface Function<out R>

expect class Any {
    //public open operator fun equals(other: Any?): Boolean
    //public open fun hashCode(): Int
    public open fun toString(): String
}

expect class Int {
    operator fun plus(other: Int): Int
}

/*expect abstract class Enum<E : Enum<E>>(@kotlin.internal.IntrinsicConstEvaluation public val name: String, public val ordinal: Int) : Comparable<E> {

    final override fun compareTo(other: E): Int = ordinal.compareTo(other.ordinal)

    final override fun equals(other: Any?): Boolean = this === other

    final override fun hashCode(): Int = identityHashCode(this)

    override fun toString(): String = name

    public companion object
}*/

expect class Int1

fun test(x: Int): Int {
    return 42 + x
}