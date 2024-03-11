import kotlin.native.internal.ExportedBridge
import kotlin.native.internal.*

data object MyObject

@ExportedBridge("get_object")
fun getObject(): Long = CreateStableRef(MyObject)!!

@ExportedBridge("compare_objects")
fun compareObjects(obj1: Long, obj2: Long): Boolean = DereferenceStableRef(obj1) == DereferenceStableRef(obj2)

@ExportedBridge("dispose_object")
fun compareObjects(obj: Long): Unit = DisposeStableRef(obj)
