val p = 0
fun foo() = 1

class Wrapper(val v: IntArray)

fun expressionTree(block: () -> Unit) {
    TODO("intrinsic")
}

fun test(a: IntArray, w: Wrapper) = expressionTree { a[0] + a[p] + a[foo()] + w.v[0] }