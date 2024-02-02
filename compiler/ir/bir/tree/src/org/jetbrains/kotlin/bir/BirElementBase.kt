/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.bir

import org.jetbrains.kotlin.bir.symbols.BirSymbol
import org.jetbrains.kotlin.bir.symbols.ownerIfBound
import org.jetbrains.kotlin.bir.util.ForwardReferenceRecorder
import org.jetbrains.kotlin.bir.util.SmallFixedPointFraction
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or

@Suppress("EqualsOrHashCode")
abstract class BirElementBase(elementClass: BirElementClass<*>) : BirElementParent(), BirElement {
    /**
     * Database reference may be stale.
     * To actualize it for all elements in a database, call [BirDatabase.realizeTreeMovements]
     */
    internal var _containingDatabase: BirDatabase? = null
    internal var _parent: BirElementParent? = null

    internal val elementClassId = elementClass.id.toUByte()
    private var flags: Byte = 0

    internal var lastReturnedInQueryIndex: UByte = 0u

    // Contains both back references and dependent elements
    protected var backReferences: Any? = null // null | BirElementBase | Array<BirElementBase?>
        private set
    private var backReferencesArrayFullness = SmallFixedPointFraction.ZERO

    // Array of form [key, value, key, value, ...]
    internal var dynamicProperties: Array<Any?>? = null


    internal val elementClass
        get() = BirMetadata.allElementsById.getOrNull(elementClassId.toInt())


    override val parent: BirElementBase?
        get() = _parent as? BirElementBase

    final override fun getContainingDatabase(): BirDatabase? {
        // perf: it should be possible to realize movements only for this element
        _containingDatabase?.realizeTreeMovements()
        return _containingDatabase
    }


    internal fun hasFlag(flag: Byte): Boolean =
        (flags and flag).toInt() != 0

    internal fun setFlag(flag: Byte, value: Boolean) {
        flags = if (value) flags or flag else flags and flag.inv()
    }

    internal var containingListId: Int
        get() = flags.toInt() shr (8 - CONTAINING_LIST_ID_BITS)
        set(value) {
            flags = (flags and (-1 ushr (32 - 8 + CONTAINING_LIST_ID_BITS)).toByte()) or (value shl (8 - CONTAINING_LIST_ID_BITS)).toByte()
        }


    fun isAncestorOf(other: BirElementBase): Boolean {
        if (getContainingDatabase() !== other.getContainingDatabase()) {
            return false
        }

        var n = other
        while (true) {
            n = n.parent ?: break
            if (n === this) return true
        }

        return false
    }

    internal fun moveElementToNewParent(newParent: BirElementParent, newDatabase: BirDatabase?) {
        val oldParent = _parent
        if (oldParent != null) {
            require(this is BirImplElementBase) { "Lazy BIR elements cannot be moved" }

            replacedWithInternal(null)
            _parent = newParent
            newDatabase?.elementMoved(this, oldParent)
        } else {
            // Lazy elements have permanent parent, reflecting
            // the one in Fir2Ir class.
            if (this is BirImplElementBase) {
                _parent = newParent
            }
            newDatabase?.elementAttached(this)
        }
    }


    internal open fun getChildrenListById(id: Int): BirChildElementList<*> {
        throwChildrenListWithIdNotFound(id)
    }

    protected fun throwChildrenListWithIdNotFound(id: Int): Nothing {
        throw IllegalStateException("The element $this does not have a children list with id $id")
    }

    internal fun getContainingList(): BirChildElementList<*>? {
        val containingListId = containingListId
        return if (containingListId == 0) null
        else parent?.getChildrenListById(containingListId)
    }


    internal open fun <T> getDynamicProperty(token: BirDynamicPropertyAccessToken<*, T>): T? {
        token.requireValid()

        val arrayMap = dynamicProperties ?: return null
        val keyIndex = findDynamicPropertyIndex(arrayMap, token.key)
        if (keyIndex < 0) {
            return null
        } else {
            @Suppress("UNCHECKED_CAST")
            return arrayMap[keyIndex + 1] as T
        }
    }

    internal fun <T> setDynamicProperty(token: BirDynamicPropertyAccessToken<*, T>, value: T?): T? {
        token.requireValid()

        val arrayMap = dynamicProperties
        var previousValue: T? = null
        if (arrayMap == null) {
            if (value == null) {
                // optimization: next read will return null if the array is null, so no need to initialize it
            } else {
                initializeDynamicProperties(token, value)
            }
        } else {
            val foundIndex = findDynamicPropertyIndex(arrayMap, token.key)
            if (foundIndex >= 0) {
                if (value == null) {
                    removeDynamicPropertyAtPruningSubsequent(arrayMap, foundIndex)
                } else {
                    val valueIndex = foundIndex + 1
                    @Suppress("UNCHECKED_CAST")
                    previousValue = arrayMap[valueIndex] as T?
                    arrayMap[valueIndex] = value
                }
            } else {
                val entryIndex = -(foundIndex + 1)
                addDynamicProperty(arrayMap, entryIndex, token.key, value)
            }
        }

        return previousValue
    }

    internal fun <T> getOrPutDynamicProperty(token: BirDynamicPropertyAccessToken<*, T>, compute: () -> T): T {
        token.requireValid()

        val arrayMap = dynamicProperties
        val foundIndex = if (arrayMap != null)
            findDynamicPropertyIndex(arrayMap, token.key)
        else -1
        if (foundIndex >= 0) {
            @Suppress("UNCHECKED_CAST")
            return arrayMap!![foundIndex + 1] as T
        } else {
            val newValue = compute()

            if (arrayMap != null) {
                val entryIndex = -(foundIndex + 1)
                addDynamicProperty(arrayMap, entryIndex, token.key, newValue)
            } else {
                initializeDynamicProperties(token, newValue)
            }

            return newValue
        }
    }

    // todo: fine-grained control of which data to copy
    internal fun copyDynamicProperties(from: BirElementBase) {
        dynamicProperties = from.dynamicProperties?.copyOf()
    }

    private fun <T> initializeDynamicProperties(token: BirDynamicPropertyAccessToken<*, T>, value: T?) {
        val size = 2
        val arrayMap = arrayOfNulls<Any?>(size * 2)
        arrayMap[0] = token.key
        arrayMap[1] = value
        this.dynamicProperties = arrayMap
    }

    private fun <T> addDynamicProperty(arrayMap: Array<Any?>, index: Int, key: BirDynamicPropertyKey<*, T>, value: T?): Boolean {
        if (value == null) {
            return false
        }

        var arrayMap = arrayMap
        if (arrayMap.size <= index) {
            arrayMap = arrayMap.copyOf(arrayMap.size * 2)
            this.dynamicProperties = arrayMap
        }

        arrayMap[index] = key
        arrayMap[index + 1] = value
        return true
    }

    private fun findDynamicPropertyIndex(arrayMap: Array<Any?>, propertyKey: BirDynamicPropertyKey<*, *>): Int {
        var i = 0
        var foundAnyOutdated = false
        while (i < arrayMap.size) {
            val key = arrayMap[i]
            if (key == null) {
                return -i - 1
            }

            if (key === propertyKey) return i
            if (!foundAnyOutdated && (key is TemporaryBirDynamicProperty<*, *> && !key.isValid)) {
                foundAnyOutdated = true
                removeDynamicPropertyAtPruningSubsequent(arrayMap, i)
            }

            i += 2
        }
        return -i - 1
    }

    private fun removeDynamicPropertyAtPruningSubsequent(arrayMap: Array<Any?>, keyIndex: Int) {
        var i = keyIndex + 2
        var j = keyIndex
        while (true) {
            val key = arrayMap.getOrNull(i)
            arrayMap[j] = key
            arrayMap[j + 1] = arrayMap.getOrNull(i + 1)

            if (key == null) {
                return
            }

            i += 2
            if (!(key is TemporaryBirDynamicProperty<*, *> && !key.isValid)) {
                j += 2
            }
        }
    }

    private fun BirDynamicPropertyAccessToken<*, *>.requireValid() {
        if (this is TemporaryBirDynamicProperty<*, *>) {
            require(isValid) { "The property token can only be used within the phase $validInPhase" }
        }
    }


    internal open fun getForwardReferences(recorder: ForwardReferenceRecorder) {}

    protected fun forwardReferencePropertyChanged() {
        _containingDatabase?.indexForwardReferencesOfElement(this)
    }

    internal fun registerBackReference(backReference: BirElementBase) {
        val hasBeenRegisteredFlag = FLAG_HAS_BEEN_REGISTERED_AS_BACK_REFERENCE

        var elementsOrSingle = backReferences
        when (elementsOrSingle) {
            null -> {
                backReferences = backReference
                backReferencesArrayFullness = SmallFixedPointFraction.ZERO
            }
            is BirElementBase -> {
                if (elementsOrSingle !== backReference) {
                    // 2 elements in array is a very common case.
                    val elements = arrayOfNulls<BirElementBase>(2)
                    elements[0] = elementsOrSingle
                    elements[1] = backReference
                    backReferences = elements

                    val newSize = 2
                    backReferencesArrayFullness = SmallFixedPointFraction(newSize, elements.size)
                }
            }
            else -> {
                @Suppress("UNCHECKED_CAST")
                elementsOrSingle as Array<BirElementBase?>

                var alreadyRegistered = false
                var currentCount = 0
                if (backReference.hasFlag(FLAG_HAS_BEEN_REGISTERED_AS_BACK_REFERENCE)) {
                    while (currentCount < elementsOrSingle.size) {
                        val element = elementsOrSingle[currentCount]
                        if (element == null) {
                            break
                        } else if (element === backReference) {
                            alreadyRegistered = true
                            break
                        }
                        currentCount++
                    }
                } else {
                    // Optimization: this element certainly isn't in the array. Just find a free spot.
                    currentCount = findBackReferencesArrayCount(elementsOrSingle)
                }

                if (!alreadyRegistered) {
                    if (currentCount == elementsOrSingle.size) {
                        // This formula gives a nice progression: 2, 3, 4, 6, 9, 13...
                        val newArraySize = elementsOrSingle.size * 3 / 2

                        elementsOrSingle = elementsOrSingle.copyOf(newArraySize)
                        backReferences = elementsOrSingle
                    }
                    elementsOrSingle[currentCount] = backReference

                    currentCount++
                    backReferencesArrayFullness = SmallFixedPointFraction(currentCount, elementsOrSingle.size)
                }
            }
        }

        backReference.setFlag(hasBeenRegisteredFlag, true)
    }

    protected fun removeBackReference(index: Int) {
        val relatedElements = backReferences
        if (relatedElements is Array<*>) {
            @Suppress("UNCHECKED_CAST")
            val array = relatedElements as Array<BirElementBase?>
            val count = findBackReferencesArrayCount(array)
            require(index < count)

            val lastIndex = count - 1
            if (index != lastIndex) {
                array[index] = array[lastIndex]
            }
            array[lastIndex] = null

            backReferencesArrayFullness = SmallFixedPointFraction(lastIndex, array.size)
        } else {
            require(index == 0)
            require(relatedElements != null)
            this.backReferences = null
        }
    }

    private fun findBackReferencesArrayCount(array: Array<BirElementBase?>): Int {
        val minSize = backReferencesArrayFullness * array.size
        if (minSize == array.size) {
            return minSize
        }

        for (i in minSize..<array.size) {
            if (array[i] == null) {
                return i
            }
        }
        error("Should not reach here")
    }

    internal fun <R : BirElement> getBackReferences(key: BirElementBackReferencesKey<*, R>): List<BirElementBase> {
        _containingDatabase?.realizeTreeMovements()
        require(_containingDatabase != null) { "Element must be attached to some BirDatabase" }

        val array = when (val elementsOrSingle = backReferences) {
            null -> return emptyList<BirElementBase>()
            is BirElementBase -> arrayOf(elementsOrSingle)
            else -> {
                @Suppress("UNCHECKED_CAST")
                elementsOrSingle as Array<BirElementBase?>
            }
        }

        val results = ArrayList<BirElementBase>(array.size)
        for (i in array.indices) {
            val backRef = array[i] ?: break

            var isValidBackRef = false
            if (backRef.hasFlag(FLAG_HAS_BEEN_REGISTERED_AS_BACK_REFERENCE)) {
                val forwardRef = key.getForwardReference(backRef)

                if (forwardRef === this) {
                    backRef._containingDatabase?.realizeTreeMovements()
                    if (backRef._containingDatabase != null) {
                        isValidBackRef = true
                    }
                }
            }

            if (isValidBackRef) {
                results += backRef
            }
        }

        return results
    }


    final override fun equals(other: Any?): Boolean {
        return other === this ||
                (other is BirSymbol && other.ownerIfBound === this)
    }


    companion object {
        internal const val FLAG_IS_IN_CLASS_INDEX: Byte = (1 shl 0).toByte()
        internal const val FLAG_HAS_VALID_FORWARD_REFERENCES_INDEX: Byte = (1 shl 1).toByte()
        internal const val FLAG_IS_IN_MOVED_ELEMENTS_BUFFER: Byte = (1 shl 2).toByte()
        internal const val FLAG_HAS_BEEN_REGISTERED_AS_BACK_REFERENCE: Byte = (1 shl 3).toByte()

        private const val CONTAINING_LIST_ID_BITS = 3
    }
}