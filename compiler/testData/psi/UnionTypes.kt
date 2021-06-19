val x: Int | String | Double | IntArray = 5

fun foo(x: Boolean | (Int | Double, String) -> Int | Float): Throwable | Int {}

typealias Test = Int? | () -> Unit

val y: (a: Int | Boolean, b: Int | java.lang.String) -> Int | Float = TODO()

fun main() {
    test<Int | String, Boolean>()
}
