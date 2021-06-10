// FIR_IDENTICAL
// !DIAGNOSTICS: -UNUSED_PARAMETER

suspend fun wrapUp2() {
    withContext<Unit> {
        <!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>other<!>()
    }
}
suspend fun <T> withContext(block: suspend () -> T) {}
suspend fun <R> other(): R = TODO()
