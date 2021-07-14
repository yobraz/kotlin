fun foo(x: java.lang.NullPointerException | IllegalArgumentException): String {
    return "OK"
}

fun box(): String {
    try {
        throw NullPointerException()
    } catch (e: IllegalArgumentException | NullPointerException) {
        // TODO: Stackmap table contain java/lang/Object type instead of RuntimeException
//        return foo(e)
    }
    return "OK"
}