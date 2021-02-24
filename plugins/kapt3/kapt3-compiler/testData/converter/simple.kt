package test

open class Foo() {
    fun foo(): String = "123"
}

class Bar() : Foo() {
    fun bar(a: String) = a
}
