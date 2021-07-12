// WITH_RUNTIME
fun some() = expressionTree {
    try {
        throw KotlinNullPointerException()
    } catch (e: RuntimeException) {
        println("Runtime exception")
    } catch (e: Exception) {
        println("Some exception")
    } finally {
        println("finally")
    }
}

fun expressionTree(block: () -> Unit) {
    TODO("intrinsic")
}