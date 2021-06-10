fun <T> myRun(x: () -> T): T = x()

fun <T> materialize(): T = TODO()

fun c(): Unit = <!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER, NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER, NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>myRun<!> {
    myRun {
        materialize()
    }
}
