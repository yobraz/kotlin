import kotlin.reflect.KProperty1

data class Data(val a: Int, val b: Double, val c: String)
class NotData(val a: Int, val b: Double, val c: String) {
    private val asString: String = toStringAsDataClass()

    override fun toString(): String = asString
}

@PartialEvaluation
inline fun Any.toStringAsDataClass(): String {
    val kClass = this::class
    val name = kClass.simpleName
    val properties = kClass.members.filterIsInstance<KProperty1<Any, *>>()
    val propertiesFormatted = properties.joinToString { "${it.name}=${it.invoke(this)}" }
    return "$name($propertiesFormatted)"
    //return "${kClass.simpleName}(${kClass.members.filterIsInstance<KProperty1<Any, *>>().joinToString { "${it.name}=${it.invoke(this)}" }})"
}

@PartialEvaluation
inline fun <reified R> Iterable<*>.filterIsInstance(): List<R> {
    return filterIsInstanceTo(ArrayList<R>())
}

@PartialEvaluation
inline fun <reified R, C : MutableCollection<in R>> Iterable<*>.filterIsInstanceTo(destination: C): C {
    for (element in this) if (element is R) destination.add(element)
    return destination
}

@PartialEvaluation
inline fun <T> Iterable<T>.joinToString(separator: CharSequence = ", ", prefix: CharSequence = "", postfix: CharSequence = "", limit: Int = -1, truncated: CharSequence = "...", crossinline transform: (T) -> CharSequence): String {
    return joinTo(StringBuilder(), separator, prefix, postfix, limit, truncated) { transform(it) }.toString()
}

@PartialEvaluation
inline fun <T, A : Appendable> Iterable<T>.joinTo(buffer: A, separator: CharSequence = ", ", prefix: CharSequence = "", postfix: CharSequence = "", limit: Int = -1, truncated: CharSequence = "...", crossinline transform: (T) -> CharSequence): A {
    buffer.append(prefix)
    var count = 0
    for (element in this) {
        if (++count > 1) buffer.append(separator)
        if (limit < 0 || count <= limit) {
            buffer.appendElement(element) { transform(it) }
        } else break
    }
    if (limit >= 0 && count > limit) buffer.append(truncated)
    buffer.append(postfix)
    return buffer
}

@PartialEvaluation
inline fun <T> Appendable.appendElement(element: T, transform: (T) -> CharSequence) {
    when {
        element is CharSequence? -> append(element)
        element is Char -> append(element)
        else -> append(transform(element))
    }
}
