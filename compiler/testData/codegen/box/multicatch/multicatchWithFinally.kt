fun box(): String {
    var result = ""
    try {
        throw IllegalArgumentException()
    } catch (e: IllegalArgumentException, NullPointerException) {
        result = "OK"
    } finally {
        return result
    }
}