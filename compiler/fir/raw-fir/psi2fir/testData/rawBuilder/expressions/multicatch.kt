import java.util.IOException

fun test1() {
    try {
        println()
    } catch (e: IllegalArgumentException, NullPointerException) {
        println()
    }
}

fun test2() {
    try {
        println()
    } catch (e: IllegalArgumentException, NullPointerException) {
        println()
    } finally {

    }
}

fun test3() {
    try {
        println()
    } catch (e: IllegalArgumentException, NullPointerException, IllegalStateException,) {
        println()
    }
}

fun test4() {
    try {
        println()
        try {
            throw IllegalAccessError()
        } catch (f: IllegalStateException, IOException) {

        } finally {

        }
    } catch (e: IllegalArgumentException, NullPointerException) {
        println()
    }
}