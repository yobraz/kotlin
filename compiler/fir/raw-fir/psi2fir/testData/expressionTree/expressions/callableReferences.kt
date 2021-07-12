class A {
    fun foo() {}
    val bar = 0
}

fun expressionTree(block: () -> Unit) {
    TODO("intrinsic")
}

fun A.qux() {}

fun baz() {}

val test1 = expressionTree { A()::foo }

val test2 = expressionTree { A()::bar }

val test3 = expressionTree { A()::qux }

val test4 = expressionTree { A::foo }

val test5 = expressionTree { A::bar }

val test6 = expressionTree { A::qux }

val test7 = expressionTree { ::baz }

val test8 = expressionTree { A?::foo }
