fun getIntOrString(): String | Int = if (true) "O" else 5

fun box(): String {
    val k: Boolean | String = when {
        false -> true
        else -> "K"
    }

    return (getIntOrString() as String) + k as String
}