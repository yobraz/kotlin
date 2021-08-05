fun foo(x: java.lang.NullPointerException | IllegalArgumentException): String {
    return "OK"
}

fun box(): String {
    try {
        throw NullPointerException()
    } catch (e: IllegalArgumentException | NullPointerException) {
        return foo(e)
    }
    return "Fail"
}