class A {
    fun foo() {}
}

var a: A? = A()

val z: (Int) -> Unit = run {
    { t ->
        a?.foo()
    }
}
