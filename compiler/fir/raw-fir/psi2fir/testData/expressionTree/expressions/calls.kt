// WITH_RUNTIME

fun expressionTree(block: () -> Unit) {
    TODO("intrinsic")
}

infix fun Int.distance(y: Int) = expressionTree { this + y }

fun test() = expressionTree { 3 distance 4 }

fun testRegular() = expressionTree { 3.distance(4) }

class My(var x: Int) {
    operator fun invoke() = x

    fun foo() {}

    fun copy() = My(x)
}

fun testInvoke() = expressionTree { My(13)() }

fun testQualified(first: My, second: My?) = expressionTree {
    println(first.x)
    println(second?.x)
    first.foo()
    second?.foo()
    first.copy().foo()
    first.x = 42
}