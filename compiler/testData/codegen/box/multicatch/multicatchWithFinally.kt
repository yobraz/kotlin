// TARGET_BACKEND: JVM_IR
// IGNORE_BACKEND: JVM_IR

// Note: JVM_FIR only

fun box(): String {
    var result = ""
    try {
        throw IllegalArgumentException()
    } catch (e: IllegalArgumentException | NullPointerException) {
        result = "OK"
    } finally {
        return result
    }
}