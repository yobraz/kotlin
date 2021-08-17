object None

fun foo(): None | String {
    return try {
        "K"
    } catch (e: Throwable) {
        None
    }
}

fun box(): String {
    val x: Int | None = try {
        5 / 0
    } catch (e: ArithmeticException) {
        None
    }

    return (if (x === None) "O" else "") + foo()
}