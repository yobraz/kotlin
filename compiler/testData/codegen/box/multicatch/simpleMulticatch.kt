fun box(): String {
    try {
        try {
            throw IllegalStateException()
        } catch (f: IndexOutOfBoundsException, NullPointerException, IllegalStateException) {
            throw NullPointerException()
        }
    } catch (e: IllegalArgumentException, NullPointerException) {
        return "OK"
    }
}