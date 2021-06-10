fun <T> example(
    fun1: () -> T,
    fun2: (T) -> Unit
): T = fun1().also(fun2)

fun runUnit(x: () -> Unit) = x()

fun main() {
    runUnit {
        // OI: `T` is inferred to `String`, coercion to Unit is performed on `example(...)`
        // NI: `T` is inferred to `Unit`, coercion to Unit is performed on "hello"
        // FIR: `T` is `String` again
        example(
            { "hello" },
            { str -> str.length }
        )
    }
}
