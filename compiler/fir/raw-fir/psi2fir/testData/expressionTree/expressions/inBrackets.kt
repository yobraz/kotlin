fun test(e: Int.() -> String) = expressionTree {
    val s = 3.e()
    val ss = 3.(e)()
}

fun expressionTree(block: () -> Unit) {
    TODO("intrinsic")
}