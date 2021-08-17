inline fun <reified T : RuntimeException> runCatching(f: () -> Unit): Unit? {
    return try {
        f()
    } catch (e: T | IllegalArgumentException) {
        null
    }
}

fun box(): String {
    if (runCatching<NullPointerException> { throw NullPointerException() } != null) return "Fail 1"
    if (runCatching<NullPointerException> { throw IllegalArgumentException() } != null) return "Fail 2"
    try {
        runCatching<NullPointerException> { throw RuntimeException() }
    } catch (e: RuntimeException) {
        return "OK"
    }

    return "Fail 3"
}