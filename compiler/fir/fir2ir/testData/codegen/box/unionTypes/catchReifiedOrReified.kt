inline fun <reified T : Throwable, reified V : Throwable> runCatching(f: () -> Unit): Unit? {
    return try {
        f()
    } catch (e: T | V) {
        null
    }
}

fun runCatchingSomeErrors(f: () -> Unit) : Unit? {
    return runCatching<NullPointerException | IllegalArgumentException, IllegalStateException>(f)
}

fun box(): String {
    if (runCatchingSomeErrors { throw NullPointerException() } != null) return "Fail 1"
    if (runCatchingSomeErrors { throw IllegalArgumentException() } != null) return "Fail 2"
    if (runCatchingSomeErrors { throw IllegalStateException() } != null) return "Fail 3"
    try {
        runCatchingSomeErrors { throw RuntimeException() }
    } catch (e: RuntimeException) {
        return "OK"
    }

    return "Fail 4"
}