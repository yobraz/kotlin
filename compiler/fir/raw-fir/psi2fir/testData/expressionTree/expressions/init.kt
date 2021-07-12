val a = expressionTree {
    class WithInit(x: Int) {
        val x: Int

        init {
            this.x = x
        }
    }
}

fun expressionTree(block: () -> Unit) {
    TODO("intrinsic")
}