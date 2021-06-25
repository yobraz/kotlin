// EXPECTED_REACHABLE_NODES: 1280
package foo

fun println(a: Any?) {}

fun box(): String {
    val l = MyArrayList()
    l.add(1)
    return "OK"
}

class MyArrayList(private var array: Array<Any?>) {
    constructor() : this(emptyArray()) {}
    constructor(initialCapacity: Int = 0) : this(emptyArray()) {}

    fun add(a: Any?) {
        array.asDynamic().push(a)
    }
}

