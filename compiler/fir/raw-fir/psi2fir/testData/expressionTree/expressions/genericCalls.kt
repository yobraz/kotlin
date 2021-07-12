fun <T> nullableValue(): T? = null

fun expressionTree(block: () -> Unit) {
    TODO("intrinsic")
}

fun test() = expressionTree {
    val n = nullableValue<Int>()
    val x = nullableValue<Double>()
    val s = nullableValue<String>()
}