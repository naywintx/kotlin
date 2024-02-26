// WITH_STDLIB
// FIR_IDENTICAL

// FILE: main.kt
import kotlinx.serialization.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.descriptors.*

<!KEEP_SERIALIZER_ANNOTATION_USELESS!>@KeepGeneratedSerializer<!>
class NonSerializable(val i: Int)

@Serializable
<!KEEP_SERIALIZER_ANNOTATION_USELESS!>@KeepGeneratedSerializer<!>
class WithoutCustom(val i: Int)


@Serializable(AbstractSerializer::class)
<!KEEP_SERIALIZER_ANNOTATION_ON_POLYMORPHIC!>@KeepGeneratedSerializer<!>
abstract class Abstract(val i: Int)
@Serializer(forClass = Abstract::class)
object AbstractSerializer

@Serializable(SealedSerializer::class)
<!KEEP_SERIALIZER_ANNOTATION_ON_POLYMORPHIC!>@KeepGeneratedSerializer<!>
sealed class Sealed(val i: Int)
@Serializer(forClass = Sealed::class)
object SealedSerializer

@Serializable(InterfaceSerializer::class)
<!KEEP_SERIALIZER_ANNOTATION_ON_POLYMORPHIC!>@KeepGeneratedSerializer<!>
interface Interface
object InterfaceSerializer: ToDoSerializer<Interface>("Interface")

@Serializable(SealedInterfaceSerializer::class)
<!KEEP_SERIALIZER_ANNOTATION_ON_POLYMORPHIC!>@KeepGeneratedSerializer<!>
sealed interface SealedInterface
object SealedInterfaceSerializer: ToDoSerializer<SealedInterface>("SealedInterface")



abstract class ToDoSerializer<T>(name: String): KSerializer<T> {
    override val descriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)
    open override fun deserialize(decoder: Decoder): T = TODO()
    open override fun serialize(encoder: Encoder, value: T) = TODO()
}
