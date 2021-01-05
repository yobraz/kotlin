fun first(z: Float | Nothing): Double | Int {
    val x: Int | String = 5

    logExceptions<IllegalStateException | IllegalArgumentException> {

    }

    try {

    } catch (e: IllegalAccessError | NullPointerException) {

    }
}

