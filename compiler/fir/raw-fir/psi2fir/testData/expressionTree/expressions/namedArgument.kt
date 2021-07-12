fun test() = expressionTree {
    fun foo(first: String = "", second: Boolean = true, third: Double = 3.1415) {}

    foo()
    foo("Alpha", false, 2.71)
    foo(first = "Hello", second = true)
    foo(third = -1.0, first = "123")
    foo(= "")
}

fun expressionTree(block: () -> Unit) {
    TODO("intrinsic")
}