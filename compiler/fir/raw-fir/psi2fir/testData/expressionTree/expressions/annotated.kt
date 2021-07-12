@Target(AnnotationTarget.EXPRESSION, AnnotationTarget.LOCAL_VARIABLE)
@Retention(AnnotationRetention.SOURCE)
annotation class Ann

fun expressionTree(block: () -> Unit) {
    TODO("intrinsic")
}

fun foo(arg: Int): Int {
    expressionTree {
        if (@Ann arg == 0) {
            @Ann return@expressionTree 1
        }
        @Ann if (arg == 1) {
            return@expressionTree (@Ann 1)
        }
    }
    return 42
}

data class Two(val x: Int, val y: Int)

fun bar(two: Two) {
    expressionTree {
        val (@Ann x, @Ann y) = two
    }
}