fun <T> run(block: () -> T): T = block()

fun expressionTree(block: () -> Unit) {
    TODO("intrinsic")
}

fun test_1() = expressionTree {
    run { return@run }
    run { return@expressionTree }
}

fun test_2() = expressionTree {
    run(fun (): Int { return 1 })
}