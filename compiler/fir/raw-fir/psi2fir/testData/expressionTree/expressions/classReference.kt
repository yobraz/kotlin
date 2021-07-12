//WITH_RUNTIME
package test

fun expressionTree(block: () -> Unit) {
    TODO("intrinsic")
}

class A

fun test() = expressionTree {
    A::class
    test.A::class
    A()::class

    A::class.java
    test.A::class.java
    A()::class.java
}