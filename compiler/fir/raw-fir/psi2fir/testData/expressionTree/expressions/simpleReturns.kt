fun test() = expressionTree {
    fun foo() {
        return
    }

    fun bar(): String {
        return "Hello"
    }
}

fun expressionTree(block: () -> Unit) {
    TODO("intrinsic")
}