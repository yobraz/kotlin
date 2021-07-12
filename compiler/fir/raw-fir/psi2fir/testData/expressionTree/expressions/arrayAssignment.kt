fun test() = expressionTree {
    val x = intArrayOf(1, 2, 3)
    x[1] = 0
}

fun foo() = 1

fun expressionTree(block: () -> Unit) {
    TODO("intrinsic")
}

fun test2() = expressionTree {
    intArrayOf(1, 2, 3)[foo()] = 1
}