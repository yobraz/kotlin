interface IThing

fun test() = expressionTree {
    fun test1(x: Any) = x is IThing
    fun test2(x: Any) = x !is IThing
    fun test3(x: Any) = x as IThing
    fun test4(x: Any) = x as? IThing
}

fun expressionTree(block: () -> Unit) {
    TODO("intrinsic")
}