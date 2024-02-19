package kotlin.collections

interface Iterable
interface MutableIterable

interface Collection
interface MutableCollection

interface List
interface MutableList

interface Set
interface MutableSet

interface Map { interface Entry }
interface MutableMap { interface MutableEntry }

interface Iterator
interface MutableIterator

interface ListIterator
interface MutableListIterator

class ByteIterator { fun next() {} }
class CharIterator { fun next() {} }
class ShortIterator { fun next() {} }
class IntIterator { fun next() {} }
class LongIterator { fun next() {} }
class FloatIterator { fun next() {} }
class DoubleIterator { fun next() {} }
class BooleanIterator { fun next() {} }
