fun getSize(x: Int | String | Boolean): Int {
    return when (x) {
        is Int -> x
        is String -> (x).length
        is Boolean -> 1
        // TODO: remove me
        else -> throw AssertionError()
    }
}

fun getRandomObject(): Int | String | Boolean {
    return if (java.util.Random().nextBoolean())
        if (java.util.Random().nextBoolean())
            true
        else
            7
    else
        "fff"
}

fun main() {
    val obj = getRandomObject()
    getSize(obj)
    getSize("foo")
    getSize(1)
}