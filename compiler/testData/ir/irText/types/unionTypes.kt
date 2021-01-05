fun first(a: Double | Int): Int | String {
    val x: Int | String = 5

    var z: () -> Int | () -> String

    try {

    } catch (e: java.lang.IllegalStateException | java.lang.NullPointerException) {

    }
    return x
}