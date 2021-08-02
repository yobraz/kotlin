import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KProperty
import kotlin.properties.ReadWriteProperty

interface StoredProperty<T> {
    var name: String?

    fun getValue(thisRef: BaseState): T
    fun setValue(thisRef: BaseState, value: T)
}

// type must be exposed otherwise `provideDelegate` doesn't work
abstract class StoredPropertyBase<T> : StoredProperty<T>, ReadWriteProperty<BaseState, T> {
    override var name: String? = null

    operator fun provideDelegate(thisRef: Any, property: KProperty<*>): ReadWriteProperty<BaseState, T> {
        name = property.name
        return this
    }

    override operator fun getValue(thisRef: BaseState, property: KProperty<*>): T = getValue(thisRef)
    override operator fun setValue(thisRef: BaseState, property: KProperty<*>, value: T) = setValue(thisRef, value)
}

abstract class BaseState {
    protected abstract fun <PROPERTY_TYPE> property(initialValue: PROPERTY_TYPE, isDefault: (value: PROPERTY_TYPE) -> Boolean): StoredPropertyBase<PROPERTY_TYPE>
}

abstract class Some : BaseState() {
    val hideBySeverity: MutableSet<Int> by <!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER, NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER, NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>property<!>(Collections.newSetFromMap(ConcurrentHashMap()), { it.<!OVERLOAD_RESOLUTION_AMBIGUITY!>isEmpty<!>() })
}
