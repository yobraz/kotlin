@CompileTimeCalculation
public fun interface Comparator<T> {
    public fun compare(a: T, b: T): Int
}

@PartialEvaluation
public inline fun <T> compareBy(vararg selectors: (T) -> Comparable<*>?): Comparator<T> {
//    require(selectors.size > 0)
    return Comparator { a, b -> compareValuesByImpl(a, b, selectors) }
}

@PartialEvaluation
public inline fun <T> compareValuesByImpl(a: T, b: T, selectors: Array<out (T) -> Comparable<*>?>): Int {
    for (fn in selectors) {
        val v1 = fn(a)
        val v2 = fn(b)
        val diff = compareValues(v1, v2)
        if (diff != 0) return diff
    }
    return 0
}

@PartialEvaluation
public inline fun <T : Comparable<*>> compareValues(a: T?, b: T?): Int {
    if (a === b) return 0
    if (a == null) return -1
    if (b == null) return 1

    @Suppress("UNCHECKED_CAST")
    return (a as Comparable<Any>).compareTo(b)
}

class Foo(val a: Int, val b: Int, val c: Int) : Comparable<Foo> {
    companion object {
        private val comparator = compareBy<Foo>(Foo::a, Foo::b, Foo::c)
    }
    override fun compareTo(other: Foo) = comparator.compare(this, other)
}
