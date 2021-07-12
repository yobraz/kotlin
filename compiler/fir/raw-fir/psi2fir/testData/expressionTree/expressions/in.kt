fun foo(x: Int, y: Int, c: Collection<Int>) = expressionTree {
    x in c && y !in c
}

fun expressionTree(block: () -> Unit) {
    TODO("intrinsic")
}