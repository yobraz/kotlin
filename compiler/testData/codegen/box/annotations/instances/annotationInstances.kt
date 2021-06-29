// IGNORE_BACKEND_FIR: JVM, JVM_IR
// TARGET_BACKEND: JVM_IR

// WITH_RUNTIME
// !LANGUAGE: +InstantiationOfAnnotationClasses

import kotlin.reflect.KClass

annotation class Bar(val s: String)

annotation class Foo(
    val int: Int,
    val s: String,
    val arr: Array<String>,
    val arr2: IntArray,
    val kClass: KClass<*>,
    val bar: Bar
)

fun box(): String {
    val foo = Foo(42, "foo", arrayOf("foo", "bar"), intArrayOf(1,2), Bar::class, Bar("bar"))
    if (foo.int != 42) return "Fail on int ${foo.int}"
    if (foo.s != "foo") return "Fail on string ${foo.s}"
    if (foo.arr[0] != "foo" || foo.arr[1] != "bar") return "Fail on array ${foo.arr}"
    if (foo.arr2[0] != 1 || foo.arr2[1] != 2) return "Fail on array ${foo.arr2}"
    if (foo.kClass != Bar::class) return "Fail on KClass ${foo.kClass}"
    if (foo.bar.s != "bar") return "Fail on nested annotation ${foo.bar}"
    return "OK"
}