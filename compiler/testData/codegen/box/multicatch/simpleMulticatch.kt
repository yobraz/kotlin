// TARGET_BACKEND: JVM_IR
// IGNORE_BACKEND: JVM_IR

// Note: JVM_FIR only

fun box(): String {
    try {
        try {
            throw IllegalStateException()
        } catch (f: IndexOutOfBoundsException | NullPointerException | IllegalStateException) {
            throw NullPointerException()
        }
    } catch (e: IllegalArgumentException | NullPointerException) {
        return "OK"
    }
}