// TARGET_BACKEND: JVM_IR
// !LANGUAGE: +MultiPlatformProjects
// WITH_STDLIB
// DIAGNOSTICS: -NOTHING_TO_OVERRIDE -NO_PARCELABLE_SUPERTYPE
// IGNORE_FIR_DIAGNOSTICS

// MODULE: m1-common
// FILE: common.kt

package test

expect interface MyParcelable

annotation class TriggerParcelize

@TriggerParcelize
data class User(val name: String) : MyParcelable

// MODULE: m2-jvm()()(m1-common)
// FILE: android.kt

@file:JvmName("TestKt")
package test

import kotlinx.parcelize.*

actual typealias MyParcelable = android.os.Parcelable

fun box() = parcelTest { parcel ->
    val user = User("John")
    user.writeToParcel(parcel, 0)

    val bytes = parcel.marshall()
    parcel.unmarshall(bytes, 0, bytes.size)
    parcel.setDataPosition(0)

    val user2 = parcelableCreator<User>().createFromParcel(parcel)
    assert(user == user2)
}