interface Builder<Y> {
    fun get(): Y
}
fun onValidate(init: () -> Unit) {
}
fun <T> verify(init: () -> Builder<T>): T {
    TODO()
}
fun builder(): Builder<String> {
    TODO()
}

fun main() {
    onValidate {
        verify {
            builder() // expected Builder<Unit>, got Builder<String>// expected Builder<Unit>, got Builder<String>
        }
    }
}

